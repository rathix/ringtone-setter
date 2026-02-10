package com.kennyandries.ringtonesetter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kennyandries.ringtonesetter.ui.RingtoneSetterScreen
import com.kennyandries.ringtonesetter.ui.theme.RingtoneSetterTheme
import com.kennyandries.ringtonesetter.viewmodel.RingtoneSetterViewModel

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

    private lateinit var viewModelInstance: RingtoneSetterViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        viewModelInstance.onPermissionsResult(allGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as RingtoneSetterApplication

        setContent {
            RingtoneSetterTheme {
                val vm: RingtoneSetterViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return RingtoneSetterViewModel(
                                configReader = app.configReader,
                                downloader = app.downloader,
                                registrar = app.registrar,
                                assigner = app.assigner,
                            ) as T
                        }
                    }
                )
                viewModelInstance = vm

                RingtoneSetterScreen(
                    viewModel = vm,
                    onRequestPermissions = {
                        permissionLauncher.launch(requiredPermissions)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModelInstance.isInitialized) {
            viewModelInstance.refreshConfig()
            viewModelInstance.onPermissionsResult(hasAllPermissions())
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
