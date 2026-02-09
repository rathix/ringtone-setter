# Architecture

## Overview

Ringtone Setter follows a layered architecture with clear separation of concerns. Each layer has a single responsibility and communicates through well-defined interfaces.

```
┌──────────────────────────────────────────────────────┐
│                    UI Layer                           │
│  RingtoneSetterScreen.kt (Jetpack Compose)           │
├──────────────────────────────────────────────────────┤
│                 ViewModel Layer                       │
│  RingtoneSetterViewModel.kt (orchestration + state)  │
├──────────────────────────────────────────────────────┤
│                 Domain Layer                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ Config   │ │ Download │ │ Ringtone │ │ Contacts│ │
│  │ Reader   │ │          │ │ Registrar│ │ Assigner│ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
├──────────────────────────────────────────────────────┤
│              Android Framework / System              │
│  RestrictionsManager, OkHttp, MediaStore, Contacts   │
└──────────────────────────────────────────────────────┘
```

## Dependency Injection

The app uses manual DI via the `RingtoneSetterApplication` class. All dependencies are instantiated once at application startup:

```kotlin
// RingtoneSetterApplication.kt
class RingtoneSetterApplication : Application() {
    lateinit var configReader: ManagedConfigReader
    lateinit var downloader: RingtoneDownloader
    lateinit var registrar: RingtoneRegistrar
    lateinit var assigner: ContactRingtoneAssigner

    override fun onCreate() {
        // Wire dependencies here
    }
}
```

`MainActivity` retrieves the application instance and passes dependencies to the ViewModel factory. This pattern avoids the complexity of Hilt or Dagger while keeping dependencies explicit and testable.

## Component Details

### Config (`config/`)

**ManagedConfigReader** reads the Android `RestrictionsManager` to retrieve Intune-pushed configuration values. It returns a sealed `ManagedConfig.Result` that is either `Valid` (with parsed config) or `Invalid` (with a list of human-readable error messages).

**ManagedConfig** is a data class with a companion `validate()` function that enforces:
- SAS URL is present and non-blank
- At least one phone number is provided
- All phone numbers match E.164 format (`+` followed by 1-15 digits)

### Download (`download/`)

**RingtoneDownloader** wraps OkHttp to stream a file from a URL into an `OutputStream`. It returns a `DownloadResult` containing the MIME type (extracted from the HTTP `Content-Type` header, defaulting to `audio/mpeg`) and the number of bytes written.

The download uses an 8 KB buffer and streams directly to the output — the full file is never held in memory.

### Ringtone (`ringtone/`)

**RingtoneRegistrar** manages the MediaStore lifecycle for ringtone files:

1. **prepare()** — Creates a MediaStore entry with `IS_PENDING=1` (Android 10+), returns a URI and `OutputStream`
2. **finalize()** — Clears `IS_PENDING`, making the ringtone visible to the system
3. **cleanup()** — Deletes a partial entry on failure

The prepare step first deletes any existing entry with the same display name, making the operation idempotent. This is important for re-runs — updating the ringtone replaces the old one rather than creating duplicates.

**API level handling:**
- Android 10+ (API 29): Uses `VOLUME_EXTERNAL_PRIMARY`, `IS_PENDING` pattern, `RELATIVE_PATH = "Ringtones/"`
- Android 9 and below: Uses `EXTERNAL_CONTENT_URI` without `IS_PENDING`

### Contacts (`contacts/`)

**ContactRingtoneAssigner** looks up contacts by phone number and assigns a ringtone URI:

1. Uses `PhoneLookup.CONTENT_FILTER_URI` to find the contact ID for a phone number
2. Updates the contact's `CUSTOM_RINGTONE` field with the ringtone content URI

Each phone number is processed independently. Results are returned as a list of `AssignmentResult` objects, each containing the phone number, success/failure status, optional contact name, and optional error message.

### ViewModel (`viewmodel/`)

**RingtoneSetterViewModel** orchestrates the entire workflow:

1. Reads and validates configuration
2. Tracks permission state
3. Executes the apply workflow (download → register → assign)
4. Manages UI state via `StateFlow`

The workflow runs on `Dispatchers.IO` within `viewModelScope`. State updates use `MutableStateFlow.update{}` for thread-safe atomic modifications.

**Operation phases:**
```
Idle → Downloading → Registering → Assigning → Done
```

On error at any phase, the ViewModel cleans up partial state (e.g., deleting a partial MediaStore entry) and surfaces the error message to the UI.

### UI (`ui/`)

**RingtoneSetterScreen** is a single Compose screen built with Material 3 components:

- **ConfigCard** — Shows configuration status with validation errors
- **PermissionsCard** — Permission grant button (hidden when granted)
- **ProgressCard** — Current operation phase with a circular progress indicator
- **ResultsCard** — Per-contact assignment results
- **ErrorCard** — Dismissible error messages
- **ApplyButton** — Enabled only when config is valid and permissions are granted

The screen observes `StateFlow<RingtoneSetterUiState>` via `collectAsState()` and recomposes reactively.

## State Management

UI state is a single data class:

```kotlin
data class RingtoneSetterUiState(
    val configStatus: ConfigStatus,       // Loading | Valid | Invalid
    val permissionsGranted: Boolean,
    val operationPhase: OperationPhase,   // Idle | Downloading | Registering | Assigning | Done
    val error: String?,
    val results: List<AssignmentResult>?
)
```

All state transitions flow through the ViewModel. The UI is purely declarative — it renders state and dispatches events, but never modifies state directly.

## Error Handling Strategy

- **Configuration errors** — Surfaced as a list of human-readable strings in the UI
- **Download errors** — `IOException` caught, MediaStore entry cleaned up, error shown
- **Registration errors** — Partial entry cleaned up via `RingtoneRegistrar.cleanup()`
- **Contact assignment errors** — Captured per-contact in `AssignmentResult`, does not interrupt other assignments
- **All exceptions** — Caught at the ViewModel level and converted to user-visible error messages

## Threading Model

| Operation | Thread |
|-----------|--------|
| Config reading | Main (synchronous, fast) |
| Download + register + assign | `Dispatchers.IO` via `viewModelScope` |
| UI rendering | Main (Compose) |
| State updates | Thread-safe via `StateFlow.update{}` |
