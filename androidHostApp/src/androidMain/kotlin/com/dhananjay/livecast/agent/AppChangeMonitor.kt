package com.dhananjay.livecast.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.dhananjay.livecast.group.DeviceIdentity
import com.dhananjay.livecast.protocol.FirestorePaths
import com.dhananjay.livecast.protocol.GroupWrites
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface InstalledAppsSource {
    fun userApps(): List<InstalledApp>
    fun app(packageName: String): InstalledApp?
}

interface AppBaselineStore {
    suspend fun load(): Map<String, String>?
    suspend fun save(baseline: Map<String, String>)
    suspend fun clear()
}

interface AppEventWriter {
    fun write(deviceId: String, event: NewAppEvent)
}

interface PackageChangeRegistrar {
    fun register(onChange: (PackageChange) -> Unit)
    fun unregister()
}

interface WallClock {
    fun nowMs(): Long
}

class SystemWallClock : WallClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

class AndroidInstalledAppsSource(
    private val context: Context
) : InstalledAppsSource {
    override fun userApps(): List<InstalledApp> {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        return packages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            if (packageInfo.packageName == ownPackage) return@mapNotNull null
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
            val label = appInfo.loadLabel(pm).toString()
            val firstInstallTime = packageInfo.firstInstallTime
            InstalledApp(
                packageName = packageInfo.packageName,
                label = label,
                firstInstallTimeMs = firstInstallTime
            )
        }
    }

    override fun app(packageName: String): InstalledApp? {
        val pm = context.packageManager
        if (packageName == context.packageName) return null
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) {
            return null
        }
        val appInfo = packageInfo.applicationInfo ?: return null
        if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return null
        val label = appInfo.loadLabel(pm).toString()
        return InstalledApp(
            packageName = packageInfo.packageName,
            label = label,
            firstInstallTimeMs = packageInfo.firstInstallTime
        )
    }
}

class SharedPreferencesAppBaselineStore(
    private val context: Context
) : AppBaselineStore {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun load(): Map<String, String>? = withContext(Dispatchers.IO) {
        if (!prefs.contains(KEY_BASELINE)) return@withContext null
        val jsonStr = prefs.getString(KEY_BASELINE, null) ?: return@withContext null
        try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getString(key)
            }
            map
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun save(baseline: Map<String, String>): Unit = withContext(Dispatchers.IO) {
        val json = JSONObject()
        for ((pkg, label) in baseline) {
            json.put(pkg, label)
        }
        prefs.edit().putString(KEY_BASELINE, json.toString()).commit()
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_BASELINE).commit()
    }

    companion object {
        private const val PREFS_NAME = "livecast_app_baseline"
        private const val KEY_BASELINE = "baseline_json"
    }
}

class FirestoreAppEventWriter(
    private val firestore: FirebaseFirestore
) : AppEventWriter {
    override fun write(deviceId: String, event: NewAppEvent) {
        val seconds = event.atEpochMs / 1000
        val nanoseconds = ((event.atEpochMs % 1000) * 1_000_000).toInt()
        val timestamp = Timestamp(seconds, nanoseconds)
        val fields = GroupWrites.appEventFields(
            type = event.type,
            packageName = event.packageName,
            label = event.label,
            at = timestamp,
            detectedLate = event.detectedLate,
            groupId = event.groupId
        )
        firestore.collection(FirestorePaths.appEvents(deviceId))
            .add(fields)
            .addOnFailureListener { e ->
                Log.w("FirestoreAppEventWriter", "Failed to write app event for $deviceId: ${event.packageName}", e)
            }
    }
}

class AndroidPackageChangeRegistrar(
    private val context: Context,
    private val clock: WallClock
) : PackageChangeRegistrar {
    @Volatile
    private var receiver: BroadcastReceiver? = null

    override fun register(onChange: (PackageChange) -> Unit) {
        unregister()
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                val uri = intent.data ?: return
                val packageName = uri.schemeSpecificPart ?: return
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                val added = action == Intent.ACTION_PACKAGE_ADDED
                val change = PackageChange(
                    packageName = packageName,
                    added = added,
                    replacing = replacing,
                    atEpochMs = clock.nowMs()
                )
                onChange(change)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    override fun unregister() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            receiver = null
        }
    }
}

class AppChangeMonitor(
    private val identity: DeviceIdentity,
    private val enrollment: EnrollmentState,
    private val apps: InstalledAppsSource,
    private val baseline: AppBaselineStore,
    private val events: AppEventWriter,
    private val registrar: PackageChangeRegistrar,
    private val clock: WallClock
) : AgentComponent {

    private val mutex = Mutex()
    private var startJob: Job? = null
    private var stopJob: Job? = null
    @Volatile
    private var currentGroupId: String? = null

    override fun start(scope: CoroutineScope) {
        val prev = stopJob
        startJob = scope.launch {
            prev?.join()
            val enrollmentStatus = enrollment.awaitStatus()
            if (enrollmentStatus !is EnrollmentStatus.Enrolled) return@launch
            val groupId = enrollmentStatus.groupId
            mutex.withLock {
                ensureActive()
                val deviceId = identity.deviceId ?: return@withLock
                val currentBaseline = baseline.load()
                val installed = apps.userApps()
                val diff = AppChangeDiff.onStart(
                    baseline = currentBaseline,
                    installed = installed,
                    groupId = groupId,
                    nowMs = clock.nowMs()
                )
                for (event in diff.events) {
                    events.write(deviceId, event)
                }
                baseline.save(diff.baseline)

                ensureActive()
                currentGroupId = groupId
                registrar.register { change ->
                    scope.launch {
                        mutex.withLock {
                            val activeGroupId = currentGroupId ?: return@withLock
                            val activeDeviceId = identity.deviceId ?: return@withLock
                            val activeBaseline = baseline.load() ?: emptyMap()
                            val app = if (change.added) {
                                apps.app(change.packageName)
                            } else {
                                null
                            }
                            val broadcastDiff = AppChangeDiff.onBroadcast(
                                change = change,
                                baseline = activeBaseline,
                                app = app,
                                groupId = activeGroupId
                            )
                            for (event in broadcastDiff.events) {
                                events.write(activeDeviceId, event)
                            }
                            baseline.save(broadcastDiff.baseline)
                        }
                    }
                }
            }
        }
    }

    override fun stop(reportOff: Boolean) {
        currentGroupId = null
        registrar.unregister()
        startJob?.cancel()
        startJob = null
    }
}
