package com.ivan200.easyscanner.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import com.ivan200.easyscanner.R
import android.Manifest
import com.ivan200.easyscanner.log

/**
 * Delegate class for checking and requesting permissions
 *
 * @param activity             activity for checking permissions
 * @param savedInstanceState   instance state to restore state of this delegate
 * @param onPermissionResult   function what called after permissions granted
 * @param onPermissionRejected function what called after permissions rejected
 *
 * @author ivan200
 * @since 06.08.2022
 */
abstract class PermissionsDelegate(
    val activity: ComponentActivity,
    savedInstanceState: Bundle?,
    var onPermissionResult: (ResultType) -> Unit,
    var permissions: Array<String>,
    val dialogTheme: Int = 0
) {
    private var needToShowDialog = true
    private var permissionResults: List<Pair<String, Boolean>> = emptyList()
    private val missingPermissions: Array<String> get() = permissionResults.filter { it.second == false }.map { it.first }.toTypedArray()
    private val hasAllPermissions get() = permissionResults.isEmpty() || permissionResults.all { it.second == true }

    private var resultLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updatePermissionResults()
        if (hasAllPermissions) {
            onPermissionResult.invoke(ResultType.Allow.AfterSettings)
        } else {
            needToShowDialog = false
            requestPermissionLauncher.launch(missingPermissions)
        }
    }

    private var requestPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        updatePermissionResults()
        if (hasAllPermissions) {
            onPermissionResult.invoke(if (needToShowDialog) ResultType.Allow.SystemRequest else ResultType.Allow.SystemRequest2)
        } else {
            if (needToShowDialog) {
                val missedPermission = permissionResults.first { it.second == false }.first
                showDialogOnPermissionRejected(missedPermission)
            } else {
                onPermissionResult.invoke(ResultType.Denied.DeniedAfterSettings)
            }
        }
    }

    init {
        @Suppress("UNCHECKED_CAST")
        savedInstanceState?.apply {
            getBoolean(KEY_NEED_TO_SHOW_DIALOG, true).let { needToShowDialog = it }
            getStringArray(KEY_PERMISSIONS).let { permissions = it ?: emptyArray() }
        }
        updatePermissionResults()
    }

    private fun updatePermissionResults() {
        permissionResults = permissions.map {
            it to (checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun getRationale(): Boolean = missingPermissions.let {
        it.isNotEmpty() && it.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }


    fun queryPermissionsOnStart() {
        updatePermissionResults()
        if (hasAllPermissions) {
            onPermissionResult.invoke(ResultType.Allow.AlreadyHas)
        } else {
            needToShowDialog = true
            requestPermissionLauncher.launch(missingPermissions)
        }
    }


    abstract fun getDialogTitieForMissedPermission(blockedPermission: String) : String
    abstract fun getDialogMessageForMissedPermission(blockedPermission: String) : String

    /**
     * Show dialog on permission rejected
     *
     * @param blockedPermission string of permission which was rejected
     * @param canReAsk          if you can call system dialog for request permission once again
     */
    private fun showDialogOnPermissionRejected(blockedPermission: String) {
        val titleId = getDialogTitieForMissedPermission(blockedPermission)
        val messageId = getDialogMessageForMissedPermission(blockedPermission)

        val dialog = AlertDialog.Builder(activity, dialogTheme)
            .setTitle(titleId)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(messageId)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                gotoPhonePermissionSettings(resultLauncher, activity)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                onPermissionResult.invoke(ResultType.Denied.CustomDialogNo)
                dialog.dismiss()
            }
            .setOnCancelListener { dialog ->
                onPermissionResult.invoke(ResultType.Denied.CustomDialogCancelled)
                dialog.dismiss()
            }
            .create()
            .apply {
                setOnCancelListener { d ->
                    onPermissionResult.invoke(ResultType.Denied.CustomDialogCancelled)
                    d.dismiss()
                }
                setOnKeyListener { arg0, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        onPermissionResult.invoke(ResultType.Denied.CustomDialogCancelled)
                        arg0.dismiss()
                    }
                    true
                }
            }
        dialog.show()
    }

    /**
     * Saving the state in the bundle of the current activity
     * since after going to the application settings and changing the permissions, the activity may die,
     * you need to save data in [outState] and restore it in order to process them correctly
     */
    fun saveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_NEED_TO_SHOW_DIALOG, needToShowDialog)
        outState.putStringArray(KEY_PERMISSIONS, permissions)
    }

    private fun gotoPhonePermissionSettings(launcher: ActivityResultLauncher<Intent>, activity: Activity) {
        val intent = Intent()
            .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, activity.packageName)
                }
            }
            .setData(Uri.fromParts("package", activity.packageName, null))
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            onPermissionResult.invoke(ResultType.Denied.CanNotGoToSettings)
        }
    }

    private companion object {
        const val TAG = "PermissionsDelegate"
        const val KEY_NEED_TO_SHOW_DIALOG = "KEY_NEED_TO_SHOW_DIALOG"
        const val KEY_PERMISSIONS = "KEY_PERMISSIONS"
    }
}