package app.dewey.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for permission to post notifications, at the moment one is about to matter.
 *
 * The permission is declared in the manifest, but from Android 13 declaring it
 * is not enough — without the runtime grant the sort's progress notification is
 * dropped silently and a four-hundred-file sort looks like the app hung.
 *
 * The returned function is called when the user hands over a folder, not on
 * launch: a permission dialog with nothing behind it yet is the kind a person
 * dismisses without reading, and Android only offers it once.
 *
 * Refusal costs the notification and nothing else. Work runs in the background
 * either way — every setForeground call is already wrapped against exactly this —
 * so there is no answer to this dialog that needs handling.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Nothing to do: see the KDoc. */ }

    return remember(context, launcher) {
        {
            if (needsNotificationPermission(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * False before Android 13, where the permission is granted at install, and false
 * once it has been granted — asking again in either case does nothing but spend
 * the one prompt the system allows.
 */
private fun needsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false

    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
}
