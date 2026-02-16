package com.kennyandries.ringtonesetter

import android.app.Application
import com.kennyandries.ringtonesetter.config.ManagedConfigReader
import com.kennyandries.ringtonesetter.contacts.ContactRingtoneAssigner
import com.kennyandries.ringtonesetter.download.RingtoneDownloader
import com.kennyandries.ringtonesetter.ringtone.RingtoneRegistrar
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RingtoneSetterApplication : Application() {

    lateinit var configReader: ManagedConfigReader
        private set
    lateinit var downloader: RingtoneDownloader
        private set
    lateinit var registrar: RingtoneRegistrar
        private set
    lateinit var assigner: ContactRingtoneAssigner
        private set

    override fun onCreate() {
        super.onCreate()

        // Pin Azure Blob Storage TLS certificates to prevent MITM attacks.
        // These are the SHA-256 hashes of the intermediate CAs used by
        // *.blob.core.windows.net (DigiCert Global G2 and Microsoft RSA TLS CA).
        val certificatePinner = CertificatePinner.Builder()
            .add(
                "*.blob.core.windows.net",
                "sha256/RCbqB+W8nwjznTeP4O6VjkYw0S7GE8+/ATLUNqQohos=",
                "sha256/VjLZe/p3W/PJnd6lL8JVNBCGQBZynFLdZSTIqcO0SJ8=",
            )
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        configReader = ManagedConfigReader(this)
        downloader = RingtoneDownloader(okHttpClient)
        registrar = RingtoneRegistrar(this)
        assigner = ContactRingtoneAssigner(this)
    }
}
