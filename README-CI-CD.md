
# CI/CD Pipeline for Android Releases

This repository includes an automated CI/CD pipeline that builds, signs, and releases Android APKs using GitHub Actions.

## 🚀 Quick Start

### 1. Set Up Secrets (One-Time Setup)

**Important:** Debug builds use Android's automatic debug keystore and only need 1 secret. Release builds need all 5 secrets.

**Minimum Setup (Debug APK only - for testing):**
- `GOOGLE_SERVICES_JSON` - Firebase configuration (base64)

**Full Setup (Release APK - for production/Play Store):**
- `GOOGLE_SERVICES_JSON` - Firebase configuration (base64)
- `KEYSTORE_BASE64` - Release keystore file (base64)
- `KEYSTORE_PASSWORD` - Keystore password
- `KEY_ALIAS` - Key alias (e.g., `livecast-release`)
- `KEY_PASSWORD` - Key password

See [DEBUG-VS-RELEASE-BUILDS.md](./docs/DEBUG-VS-RELEASE-BUILDS.md) for detailed explanation.

### 2. Create a Release
Choose one of these methods:

**Option A: Git Tag (Recommended)**
```bash
git tag v1.0.0
git push origin v1.0.0
```

**Option B: Push to Release Branch**
```bash
git checkout release
git merge main
git push origin release
```

**Option C: Manual Trigger**
1. Go to: GitHub → Actions → "Android Release Build"
2. Click "Run workflow"
3. Enter version (e.g., `1.2.3`)
4. Click "Run workflow"

### 3. Download Your APKs
- **Artifacts:** Actions tab → Workflow run → Artifacts section
- **Releases:** GitHub Releases page (for tagged builds)

Both debug and release APKs will be available!

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [CI-CD-SETUP.md](./docs/CI-CD-SETUP.md) | **Complete setup guide** with detailed instructions |
| [SECRETS-QUICK-REFERENCE.md](./docs/SECRETS-QUICK-REFERENCE.md) | **Quick reference** for secrets and commands |
| [SECURITY-CHECKLIST.md](./docs/SECURITY-CHECKLIST.md) | **Security best practices** and checklist |

## 🔧 What This Pipeline Does

✅ Builds **Debug APK** (unsigned, for testing)  
✅ Builds **Release APK** (signed, production-ready)  
✅ Automatically versions your builds  
✅ Creates GitHub releases with attached APKs  
✅ Uploads artifacts for all builds  
✅ Generates release notes from commits  

## 📦 Build Outputs

### APK Naming
- Debug: `livecast-{version}-debug.apk`
- Release: `livecast-{version}-release.apk`

### Where to Find
- **All Builds:** GitHub Actions → Artifacts (30-90 days retention)
- **Tagged/Manual:** GitHub Releases (permanent)

## 🎯 Workflow Triggers

| Trigger | When | Creates Release? | Version Source |
|---------|------|------------------|----------------|
| Push to `release` branch | On every push | ❌ No | Auto-generated timestamp |
| Git tag `v*` | When tag is pushed | ✅ Yes | From tag name |
| Manual trigger | Click "Run workflow" | ✅ Yes | User input or auto |

## 🔐 Security Notes

⚠️ **CRITICAL:**
- Never commit keystore files (`.jks`, `.keystore`)
- Never commit `google-services.json`
- Never commit passwords or API keys
- Always backup your keystore securely

✅ **Best Practices:**
- Store keystore in password manager
- Use separate key for debug builds
- Enable 2FA on GitHub
- Review the [Security Checklist](./docs/SECURITY-CHECKLIST.md)

## 🛠️ Local Testing

Test builds locally before pushing:

```bash
# Debug APK (no signing required)
./gradlew :composeApp:assembleDebug

# Release APK (requires keystore)
./gradlew :composeApp:assembleRelease \
  -Pversion.name=1.0.0 \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password=YOUR_PASSWORD \
  -Pandroid.injected.signing.key.alias=YOUR_ALIAS \
  -Pandroid.injected.signing.key.password=YOUR_KEY_PASSWORD
```

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| Build fails: "google-services.json not found" | Check `GOOGLE_SERVICES_JSON` secret |
| Build fails: "Keystore not found" | Check `KEYSTORE_BASE64` secret |
| APK not signed | Verify all 5 secrets are set |
| Release not created | Use git tag `v*` or manual trigger |

See [CI-CD-SETUP.md](./docs/CI-CD-SETUP.md) for detailed troubleshooting.

## 📋 Setup Checklist

Before first use:
- [ ] Generate or locate release keystore
- [ ] Add all 5 GitHub secrets
- [ ] Test with manual workflow trigger
- [ ] Backup keystore securely
- [ ] Review security checklist
- [ ] Document keystore details

## 🔄 Versioning

Versions are automatically managed:
- **Git tags:** `v1.2.3` → Version name `1.2.3`, Code `10203`
- **Manual:** User specifies version
- **Auto:** Generated from timestamp

Version codes follow semantic versioning:
- `1.2.3` → `10203`
- `2.0.15` → `20015`

## 📱 Distribution

After successful build:

**For Testing:**
1. Download debug APK from Artifacts
2. Share via email, Slack, etc.
3. Install on test devices

**For Production:**
1. Download release APK from GitHub Releases
2. Upload to Google Play Console
3. Create release in Play Console

## 🆘 Support

If you encounter issues:
1. Check workflow logs in GitHub Actions
2. Review the [Troubleshooting section](./docs/CI-CD-SETUP.md#troubleshooting)
3. Verify all secrets are correctly configured
4. Check the [Security Checklist](./docs/SECURITY-CHECKLIST.md)

## 📖 Additional Resources

- [Android App Signing](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Firebase Android Setup](https://firebase.google.com/docs/android/setup)

---

**Need help?** Read the [Complete Setup Guide](./docs/CI-CD-SETUP.md) for detailed instructions.
