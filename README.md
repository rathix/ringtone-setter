# Ringtone Setter

An enterprise Android app that downloads a ringtone from Azure Blob Storage and assigns it as a custom ringtone for specific contacts. Designed for managed device deployments via Microsoft Intune.

## How It Works

1. An IT admin pushes configuration (ringtone URL + contact phone numbers) via Intune Managed Configurations
2. The app reads the configuration, downloads the ringtone file, and registers it in the device's MediaStore
3. Each specified contact is looked up by phone number and assigned the custom ringtone

## Requirements

- Android 8.0+ (API 26)
- Device managed by Microsoft Intune (or another MDM that supports Android managed configurations)
- Azure Blob Storage account with a valid SAS URL for the ringtone file

## Intune Configuration

Push these keys via an [App Configuration Policy](https://learn.microsoft.com/en-us/mem/intune/apps/app-configuration-policies-overview) targeting managed devices:

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `ringtone_sas_url` | String | Yes | Full Azure Blob Storage SAS URL for the ringtone file |
| `contact_phone_numbers` | String | Yes | Comma-separated phone numbers in E.164 format (e.g. `+14155552671,+12125551234`) |
| `ringtone_display_name` | String | No | Display name shown in system ringtone settings (default: `Enterprise Ringtone`) |

### E.164 Phone Number Format

All phone numbers must use [E.164](https://en.wikipedia.org/wiki/E.164) international format:
- Start with `+` followed by country code
- No spaces, dashes, or parentheses
- Examples: `+14155552671` (US), `+442071234567` (UK), `+61412345678` (AU)

## Permissions

The app requests these permissions at runtime:

| Permission | Purpose |
|------------|---------|
| `READ_CONTACTS` | Look up contacts by phone number |
| `WRITE_CONTACTS` | Assign custom ringtone to contacts |
| `WRITE_EXTERNAL_STORAGE` | Write ringtone file on Android 9 and below (not needed on Android 10+) |

The `INTERNET` permission is declared in the manifest (no runtime prompt) for downloading the ringtone file.

## Building

### Prerequisites

- **Java 17** — e.g. `brew install openjdk@17`
- **Android SDK** — with API 35 build tools installed

### Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

For a release build:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
```

Release builds have ProGuard minification enabled.

### Release Signing

To produce a signed release APK, provide these Gradle properties (typically via CI secrets):

```properties
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

If these properties are not set, `assembleRelease` produces an unsigned release artifact.

### Local SDK Path

The Android SDK path is configured in `local.properties`:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

Adjust this to match your local Android SDK installation.

## Architecture

```
app/src/main/kotlin/com/kennyandries/ringtonesetter/
├── RingtoneSetterApplication.kt        # Manual DI container
├── MainActivity.kt                     # Permission handling + Compose host
├── config/
│   ├── ManagedConfig.kt                # Config data class + validation
│   └── ManagedConfigReader.kt          # Reads from Android RestrictionsManager
├── download/
│   └── RingtoneDownloader.kt           # OkHttp streaming download
├── ringtone/
│   └── RingtoneRegistrar.kt            # MediaStore registration (IS_PENDING pattern)
├── contacts/
│   └── ContactRingtoneAssigner.kt      # Phone lookup + ringtone assignment
├── viewmodel/
│   ├── RingtoneSetterViewModel.kt      # Orchestration + state management
│   └── RingtoneSetterUiState.kt        # UI state types
└── ui/
    ├── RingtoneSetterScreen.kt         # Main Compose screen
    └── theme/                          # Material 3 theme (colors, typography)
```

### Key Design Decisions

- **No DI framework** — Dependencies are wired manually in `RingtoneSetterApplication`. The app is small enough that Hilt/Dagger/Koin would add unnecessary complexity.
- **Idempotent ringtone registration** — Existing MediaStore entries with the same display name are deleted before inserting, preventing duplicates across repeated runs.
- **Independent contact assignments** — Each contact is processed separately. One failure does not block the others.
- **IS_PENDING pattern** — On Android 10+, the ringtone file is hidden from other apps until the download completes, preventing partial files from being visible.
- **Streaming download** — The ringtone is streamed directly from the network to MediaStore in 8 KB chunks, avoiding loading the entire file into memory.

### Workflow

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  ManagedConfig   │────▶│ RingtoneDownloader│────▶│  RingtoneRegistrar  │
│  Reader          │     │  (OkHttp)        │     │  (MediaStore)       │
└─────────────────┘     └──────────────────┘     └─────────┬───────────┘
                                                           │
                                                           ▼
                                                 ┌─────────────────────┐
                                                 │ ContactRingtone     │
                                                 │ Assigner            │
                                                 └─────────────────────┘
```

1. **Read config** — `ManagedConfigReader` pulls the SAS URL, phone numbers, and display name from `RestrictionsManager`
2. **Validate** — `ManagedConfig.validate()` checks URL presence and E.164 phone number format
3. **Download** — `RingtoneDownloader` streams the file from Azure via OkHttp
4. **Register** — `RingtoneRegistrar` creates a MediaStore entry in the `Ringtones/` directory
5. **Assign** — `ContactRingtoneAssigner` looks up each contact by phone number and sets the custom ringtone URI

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.0 |
| Jetpack Compose | BOM 2024.12.01 |
| Material 3 | (from Compose BOM) |
| OkHttp | 4.12.0 |
| AndroidX Lifecycle | 2.8.7 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Java | 17 |

## License

Proprietary. Internal enterprise use only.
