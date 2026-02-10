# Deployment Guide

This guide covers deploying the Ringtone Setter app to managed Android devices via Microsoft Intune.

## Prerequisites

- Microsoft Intune tenant with Android Enterprise enrollment
- Azure Blob Storage account containing the ringtone file
- A SAS (Shared Access Signature) URL for the ringtone blob with at minimum **Read** permission
- Target devices running Android 8.0 or later

## Step 1: Generate an Azure Blob SAS URL

1. Upload your ringtone file (`.mp3`, `.ogg`, `.wav`, etc.) to an Azure Blob Storage container
2. Generate a SAS URL for the blob:
   - In the Azure Portal, navigate to the blob and select **Generate SAS**
   - Set permissions to **Read** only
   - Set an expiry date appropriate for your deployment timeline
   - Copy the full SAS URL (includes the `?sv=...&sig=...` query parameters)

> **Tip**: For long-lived deployments, consider using a SAS policy with a far-future expiry or rotating the URL periodically via Intune policy updates.

The SAS URL should look like:
```
https://<account>.blob.core.windows.net/<container>/<filename>.mp3?sv=2022-11-02&st=...&se=...&sr=b&sp=r&sig=...
```

## Step 2: Identify Target Contacts

Collect the phone numbers for contacts that should receive the custom ringtone. Each number must be in **E.164 international format**:

```
+14155552671
+12125551234
+442071234567
```

Format these as a single comma-separated string:
```
+14155552671,+12125551234,+442071234567
```

> **Note**: Android's phone number lookup handles formatting variations, but the app validates input against E.164 format. Do not include spaces, dashes, or parentheses.

## Step 3: Build and Upload the APK

Build a release APK:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
```

Upload `app/build/outputs/apk/release/app-release.apk` to Intune as a Line-of-Business (LOB) app:

1. Go to **Intune** > **Apps** > **All apps** > **Add**
2. Select **Line-of-business app**
3. Upload the APK
4. Fill in app information and assign to target device groups

## Step 4: Create an App Configuration Policy

1. Go to **Intune** > **Apps** > **App configuration policies** > **Add** > **Managed devices**
2. Configure:
   - **Name**: Ringtone Setter Configuration
   - **Platform**: Android Enterprise
   - **Targeted app**: Ringtone Setter
3. Under **Configuration settings**, select **Use configuration designer**
4. Add the following keys:

| Configuration Key | Value Type | Value |
|-------------------|-----------|-------|
| `ringtone_sas_url` | String | Your full Azure Blob SAS URL |
| `contact_phone_numbers` | String | Comma-separated E.164 phone numbers |
| `ringtone_display_name` | String | Display name (e.g. `Help Desk Ringtone`) |

5. Assign the policy to the same device groups as the app

## Step 5: User Experience

Once the app and configuration are deployed:

1. The user opens the app
2. The app displays the configuration status (valid or invalid with specific errors)
3. If permissions haven't been granted, the user taps **Grant Permissions** and approves contact access
4. The user taps **Apply Ringtone**
5. The app downloads the file, registers it, and assigns it to each contact
6. Results are displayed showing success or failure for each contact

### Expected Permission Prompts

Users will see these permission dialogs on first launch:

- **Allow Ringtone Setter to access your contacts?** — Required for both reading and writing contacts

On Android 9 and below, an additional prompt appears:

- **Allow Ringtone Setter to access photos, media, and files on your device?** — Required for writing the ringtone file to storage

## Troubleshooting

### Configuration Shows as Invalid

| Error | Cause | Fix |
|-------|-------|-----|
| "Ringtone SAS URL is not configured" | `ringtone_sas_url` is missing or empty | Verify the key name and value in your Intune policy |
| "No contact phone numbers configured" | `contact_phone_numbers` is missing or empty | Add at least one phone number |
| "Invalid E.164 phone numbers: ..." | One or more values in `contact_phone_numbers` do not match E.164 | Ensure all numbers start with `+` and contain only digits |

### Configuration Not Appearing

- Ensure the **App Configuration Policy** is assigned to the same group as the device
- Verify the device has synced with Intune recently (Settings > Accounts > Work account > Sync)
- The app reads configuration on every resume — switch away and back to the app after syncing

### Contact Not Found

The app uses Android's `PhoneLookup` API which handles common format variations. If a contact isn't found:

- Verify the phone number is saved in the device's contacts
- Check the number matches (country code and digits) — the lookup is flexible but not fuzzy
- The contact must be in the device's local contacts or synced Google/Exchange contacts

### Download Failures

- Verify the SAS URL hasn't expired
- Ensure the device has internet connectivity
- Check that the SAS URL has **Read** permission on the blob
- Manual runs from the app do not retry automatically — tap **Apply Ringtone** again after resolving the issue
- Managed configuration change processing retries automatically (up to 3 attempts with backoff)

### Ringtone Not Playing for Calls

- Verify the contact assignment shows "Success" in the app
- Check the contact's details in the Contacts app — the ringtone should appear under the contact's settings
- Some OEM Android skins override custom ringtones in Do Not Disturb or specific calling modes
