package com.mediaflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mediaflow.app.navigation.AppNavigation

/** Main launch activity for MediaFlow. */
class MainActivity : ComponentActivity() {
    private var pendingNotificationResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingNotificationResult?.invoke(granted)
        pendingNotificationResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setWindowAnimations(0)
        setContent {
            AppNavigation(
                requestNotificationPermission = ::requestNotificationPermission,
            )
        }
    }

    /**
     * Downloads use a foreground service notification. On Android 13+ ask for
     * notification permission before enqueueing the first transfer.
     */
    private fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            pendingNotificationResult = onResult
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
