package com.ivan200.easyscanner.permission

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.ivan200.easyscanner.R

/**
 * @author ivan200
 * @since 08.10.2022
 */
class PermissionsDelegateCamera(
    activity: ComponentActivity,
    savedInstanceState: Bundle?,
    onPermissionResult: (ResultType) -> Unit
) : PermissionsDelegate(
    activity,
    savedInstanceState,
    onPermissionResult,
    arrayOf(Manifest.permission.CAMERA)
) {

    override fun getDialogTitieForMissedPermission(blockedPermission: String): String {
        return activity.getString(R.string.permission_camera_goto_settings_title)
    }

    override fun getDialogMessageForMissedPermission(blockedPermission: String): String {
        return activity.getString(R.string.permission_camera_rationale_goto_settings)
    }
}
