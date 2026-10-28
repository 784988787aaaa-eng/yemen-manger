# MT Manager

Dual-pane Android file manager, archive inspector, code editor, installed apps & APK extractor, DEX/Smali/AXML/ARSC inspector, and comprehensive APK engineering toolkit.

## Build Requirements
- JDK 17
- Android SDK Platform 35
- Gradle 8.9+

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## GitHub Actions CI/CD & Secrets Setup

The repository includes an automated GitHub Actions workflow at `.github/workflows/android-build.yml` configured to build, sign, and upload APK artifacts automatically on every push, pull request, tag, or manual trigger (`workflow_dispatch`).

### Configured Secrets (Repository Secrets)
The workflow uses the four secrets shown in GitHub Repository Secrets:

| Secret Name | Description |
|---|---|
| `KEYSTORE_BASE64` | The Base64-encoded string of your Android release keystore (`.jks` or `.keystore`). The workflow decodes this to `app/release.keystore`. |
| `KEY_ALIAS` | The alias name of the private key inside the keystore. |
| `KEY_PASSWORD` | The password for the specific key alias. |
| `STORE_PASSWORD` | The password for unlocking the keystore file. |

### How to Generate `KEYSTORE_BASE64` (if updating)
```bash
# Linux / macOS
base64 -w 0 my-release-key.jks > keystore_base64.txt

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-release-key.jks")) | Out-File -Encoding ASCII keystore_base64.txt
```

### Artifact Outputs
Whenever the GitHub Actions workflow runs:
1. **`MTManager-Release-APK`**: The signed production APK (`MTManager-release.apk`), signed with your repository secrets.
2. **`MTManager-Debug-APK`**: The debug APK for testing (`MTManager-debug.apk`).
3. **GitHub Releases**: Pushing a tag (e.g. `git tag v5.0 && git push origin v5.0`) will automatically publish a GitHub Release with the APKs attached.

