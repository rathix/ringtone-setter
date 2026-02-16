# Changelog

## [1.1] - 2026-02-16

### Security
- Add TLS certificate pinning for Azure Blob Storage endpoints
- Validate SAS URL requires HTTPS scheme and Azure Blob Storage domain
- Enforce 10 MB download size limit with `Content-Type: audio/*` validation
- Sanitize error messages to strip SAS URLs and tokens before displaying in UI
- Disable HTTP redirects on OkHttp client

### Added
- Background worker (WorkManager) for managed configuration updates
- Structured logging across downloader, registrar, worker, and ViewModel
- Disk space check (50 MB minimum) before downloading
- Unit tests for URL validation, content-type rejection, size limits, cancellation, error sanitization, and ViewModel state

### Changed
- Worker retry now re-assigns only failed contacts instead of re-downloading the entire ringtone
- MediaStore `finalize()` is called before `updateMimeType()` so the entry is no longer pending when MIME type is set
- Work policy changed from REPLACE to KEEP to avoid cancelling in-flight work
- Download loop polls `isStopped` for cooperative Worker cancellation
- Build produces AAB bundle; package name changed to `com.kennyandries`
- Bump `versionCode` to 2 for Play Store compatibility

### Fixed
- Fix Codex review issues with retry logic and MediaStore ordering
- Fix WorkManager and OneTimeWorkRequest imports
- Fix unit test crashes by returning default values for `android.util.Log`
- Move test sources from `java/` to `kotlin/` to match main source set convention

## [1.0] - 2025-01-01

### Added
- Initial implementation of Ringtone Setter enterprise Android app
- Download ringtone from Azure Blob Storage via SAS URL
- Assign custom ringtone to specific contacts via Intune Managed Configurations
- Kotlin + Jetpack Compose UI with Material 3 theme
- OkHttp streaming download
- MediaStore registration with `IS_PENDING` pattern
- GitHub Actions CI workflow for building APK/AAB
- README and architecture documentation
