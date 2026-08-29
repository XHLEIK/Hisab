package com.example.hisab.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class PermissionStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    RESTRICTED,
    NOT_APPLICABLE
}

data class PermissionState(
    val notification: PermissionStatus,
    val smsReceive: PermissionStatus,
    val smsRead: PermissionStatus,
    val storageRead: PermissionStatus,
    val storageWrite: PermissionStatus,
    val manageStorage: PermissionStatus
) {
    val isSmsGranted: Boolean get() = smsReceive == PermissionStatus.GRANTED && smsRead == PermissionStatus.GRANTED
    val isNotificationGranted: Boolean get() = notification == PermissionStatus.GRANTED
    val isStorageGranted: Boolean get() = storageRead == PermissionStatus.GRANTED || manageStorage == PermissionStatus.GRANTED
}

object PermissionCoordinator {

    fun checkNotificationPermission(context: Context): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.NOT_APPLICABLE
        return when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
            else -> PermissionStatus.DENIED
        }
    }

    fun checkSmsPermissions(context: Context): Pair<PermissionStatus, PermissionStatus> {
        val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)

        val receiveStatus = when (receive) {
            PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
            else -> PermissionStatus.DENIED
        }
        val readStatus = when (read) {
            PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
            else -> PermissionStatus.DENIED
        }
        return receiveStatus to readStatus
    }

    fun checkStoragePermissions(context: Context): Pair<PermissionStatus, PermissionStatus> {
        // READ_EXTERNAL_STORAGE is declared maxSdkVersion=32 and WRITE maxSdkVersion=28 in the
        // manifest. Checking/requesting them above those levels returns DENIED forever — which
        // used to make the storage card reappear endlessly on modern devices. Above their
        // levels they are NOT_APPLICABLE: backup access there is a SAF capability instead.
        val readStatus = when {
            Build.VERSION.SDK_INT > 32 -> PermissionStatus.NOT_APPLICABLE
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
            else -> PermissionStatus.DENIED
        }

        val writeStatus = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> PermissionStatus.NOT_APPLICABLE
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
            else -> PermissionStatus.DENIED
        }
        return readStatus to writeStatus
    }

    fun checkManageStoragePermission(context: Context): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) PermissionStatus.GRANTED else PermissionStatus.DENIED
        } else {
            PermissionStatus.NOT_APPLICABLE
        }
    }

    fun checkAll(context: Context): PermissionState {
        val notification = checkNotificationPermission(context)
        val (smsReceive, smsRead) = checkSmsPermissions(context)
        val (storageRead, storageWrite) = checkStoragePermissions(context)
        val manageStorage = checkManageStoragePermission(context)
        return PermissionState(notification, smsReceive, smsRead, storageRead, storageWrite, manageStorage)
    }

    fun isSmsRestricted(context: Context, shouldShowRationale: Boolean, permissionDenied: Boolean): Boolean {
        // Hard-restricted: System will not show dialog, shouldShowRationale is false and permission is denied
        // This is device-specific; we treat permanently denied as restricted for SMS to show in-app guidance
        return permissionDenied && !shouldShowRationale
    }

    fun getStoragePermissionsToRequest(context: Context): Array<String> {
        val list = mutableListOf<String>()
        val (readStatus, writeStatus) = checkStoragePermissions(context)
        if (readStatus == PermissionStatus.DENIED) {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (writeStatus == PermissionStatus.DENIED) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        // On API 33+, storage permissions are NOT_APPLICABLE but Documents access may still need user picker
        // Return empty but caller should show SAF picker if backup needs it
        return list.toTypedArray()
    }

    fun isBackupAccessNeedsPicker(context: Context): Boolean {
        // Check if internal backup exists — if so, no picker needed (Case A)
        val internalFile = java.io.File(context.filesDir, "backups/hisab_auto_backup.json")
        if (internalFile.exists() && internalFile.length() > 0) return false
        // Check if external backup might exist but requires user action on this API level
        // On API 33+, direct File access to Documents is blocked, so we need SAF picker
        // We do a lightweight MediaStore probe without requiring permission
        return try {
            val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val selection = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val args = arrayOf("hisab_auto_backup.json")
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                cursor.count > 0
            } ?: false
            // If MediaStore finds it, it's accessible without picker (app-created)
            // If not found, we may need picker to let user select it
        } catch (e: Exception) {
            false
        }
    }

    fun shouldShowStorageUi(context: Context): Boolean {
        val (readStatus, _) = checkStoragePermissions(context)
        // On API <=32, show storage permission card if denied
        if (readStatus == PermissionStatus.DENIED) return true
        // On API 33+, check if backup needs picker
        if (Build.VERSION.SDK_INT > 32) {
            return isBackupAccessNeedsPicker(context)
        }
        return false
    }

    fun getSmsPermissionsToRequest(context: Context): Array<String> {
        val list = mutableListOf<String>()
        val (receive, read) = checkSmsPermissions(context)
        if (receive == PermissionStatus.DENIED) list.add(Manifest.permission.RECEIVE_SMS)
        if (read == PermissionStatus.DENIED) list.add(Manifest.permission.READ_SMS)
        return list.toTypedArray()
    }

    fun getNotificationPermissionToRequest(context: Context): Array<String> {
        return if (checkNotificationPermission(context) == PermissionStatus.DENIED) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyArray()
    }
}
