package com.dhananjay.livecast.cast.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dhananjay.livecast.cast.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// At the top level of your kotlin file:
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(
    private val context: Context
) {

    suspend fun saveLoginStatus(isLoggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Constants.PREF_LOGIN_STATUS] = isLoggedIn
        }
    }

    fun getLoginStatus(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[Constants.PREF_LOGIN_STATUS] ?: false
        }
    }
}