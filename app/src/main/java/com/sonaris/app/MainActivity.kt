package com.sonaris.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.sonaris.app.ui.AppRoot
import com.sonaris.app.ui.YTMusicTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            YTMusicTheme {
                AppRoot(vm)
            }
        }
    }

    // Con la pantalla apagada o la app en segundo plano no hace falta refrescar la barra de progreso.
    override fun onStart() {
        super.onStart()
        vm.setUiActive(true)
    }

    override fun onStop() {
        super.onStop()
        vm.setUiActive(false)
    }
}
