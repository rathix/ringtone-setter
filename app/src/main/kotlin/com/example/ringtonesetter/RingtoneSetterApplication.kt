package com.example.ringtonesetter

import android.app.Application
import com.example.ringtonesetter.config.ManagedConfigReader
import com.example.ringtonesetter.contacts.ContactRingtoneAssigner
import com.example.ringtonesetter.download.RingtoneDownloader
import com.example.ringtonesetter.ringtone.RingtoneRegistrar
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

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        configReader = ManagedConfigReader(this)
        downloader = RingtoneDownloader(okHttpClient)
        registrar = RingtoneRegistrar(this)
        assigner = ContactRingtoneAssigner(this)
    }
}
