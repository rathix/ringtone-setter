# Ringtone Setter

Enterprise Android app that downloads a ringtone from Azure Blob Storage (SAS URL) and assigns it as a custom ringtone for specific contacts. Configuration is pushed via Microsoft Intune Managed Configurations.

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
```

Requires:
- Java 17: `/opt/homebrew/opt/openjdk@17`
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (set in `local.properties`)

## Tech Stack

- Kotlin + Jetpack Compose, min SDK 26, target SDK 35
- Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01
- OkHttp 4.12 for HTTP download
- No DI framework — manual DI via `RingtoneSetterApplication`

## Architecture

```
app/src/main/kotlin/com/example/ringtonesetter/
├── RingtoneSetterApplication.kt        # DI container
├── MainActivity.kt                     # Permission handling, Compose host
├── config/                             # Intune managed config reading + validation
├── download/                           # OkHttp streaming download
├── ringtone/                           # MediaStore registration (IS_PENDING pattern)
├── contacts/                           # ContactsContract phone lookup + ringtone assignment
├── viewmodel/                          # UI state + orchestration
└── ui/                                 # Compose UI + Material 3 theme
```

## Managed Config Keys (Intune)

| Key | Type | Description |
|-----|------|-------------|
| `ringtone_sas_url` | String | Full Azure Blob SAS URL |
| `contact_phone_numbers` | String | Comma-separated E.164 phone numbers |
| `ringtone_display_name` | String | Display name (default: "Enterprise Ringtone") |

## Key Conventions

- `android:description` in `app_restrictions.xml` must use `@string/` resource references, not literal strings (AAPT linking error otherwise)
- Ringtone registration is idempotent — deletes existing MediaStore entry with the same display name before inserting
- `WRITE_EXTERNAL_STORAGE` is only declared with `maxSdkVersion="28"` for pre-Q fallback
- Each contact assignment is independent; one failure does not block others
