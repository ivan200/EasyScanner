package com.ivan200.easyscanner.permission

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog

/**
 * Delegate for processing permissions
 *
 * @property activity activity for checking permissions
 * @property onPermissionGranted function what called after permissions granted
 * @property onPermissionRejected function what called after permissions rejected
 * @param savedInstanceState instance state to restore state of this delegate
 *
 * Created by Ivan200 on 25.10.2019.
 */
@Suppress("MemberVisibilityCanBePrivate")
open class PermissionsDelegate(
    var activity: ComponentActivity,
    savedInstanceState: Bundle?,
    var allPermissions: ArrayList<PermissionData> = arrayListOf(),
    var onPermissionGranted: (() -> Unit)? = null,
    var onPermissionRejected: ((String) -> Unit)? = null
) {
    private var deniedPermissionsArray: Array<String> = emptyArray()
    private var deniedPermissionsStates = ArrayList<PermissionData>()
    private var resultLauncher: ActivityResultLauncher<Intent>
    private var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    init {
        @Suppress("UNCHECKED_CAST")
        savedInstanceState?.apply {
            (getSerializable(KEY_PERMISSION_STATES) as? ArrayList<PermissionData>)?.let { deniedPermissionsStates = it }
            (getSerializable(KEY_ALL_PERMISSION_STATES) as? ArrayList<PermissionData>)?.let { allPermissions = it }
        }

        resultLauncher =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                requestPermissions()
            }

        requestPermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { this.onRequestPermissionsResult() }
    }

    /**
     * Saving the state in the bundle of the current activity
     * since after going to the application settings and changing the permissions, the activity may die,
     * you need to save and restore the data of the permissions in order to process them correctly
     */
    fun saveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_PERMISSION_STATES, deniedPermissionsStates)
        outState.putSerializable(KEY_ALL_PERMISSION_STATES, allPermissions)
    }

    /**
     * Main method for requesting permissions
     */
    open fun requestPermissions() {
        deniedPermissionsStates = ArrayList(allPermissions.filter { !it.hasPermission(activity) })
        deniedPermissionsArray = deniedPermissionsStates.map { it.permission }.toTypedArray()
        if (deniedPermissionsStates.isNotEmpty()) {
            deniedPermissionsStates.forEach { it.setBefore(activity) }
            requestPermissionsLauncher.launch(deniedPermissionsArray)
        } else {
            //Если все пермишены разрешены, то ничего не делаем
            onPermissionGranted?.invoke()
        }
    }

    /**
     * Method to return the result of a permission request
     */
    fun onRequestPermissionsResult() {
        deniedPermissionsStates.forEach { it.setAfter(activity) }
        val permissionsMap = deniedPermissionsStates.map { Pair(it, it.getState(activity)) }
        val deniedPermission = permissionsMap.firstOrNull { it.second.isDenied() }
        if (deniedPermission != null) {
            showDialogOnPermissionRejected(deniedPermission.first.permission, deniedPermission.second.canReAsk())
        } else {
            onPermissionGranted?.invoke()
        }
    }

    /**
     * Show dialog on permission rejected
     *
     * @param blockedPermission String of permission which was rejected
     * @param canReAsk if you can call system dialog for request permission once again
     */
    open fun showDialogOnPermissionRejected(blockedPermission: String, canReAsk: Boolean = false) {
        val messageId = allPermissions.first { it.permission == blockedPermission }.let {
            if (canReAsk) it.messageIdOnPermissionRejected
            else it.messageIdOnPermissionBlocked
        }
        AlertDialog.Builder(activity)
            .setTitle(android.R.string.dialog_alert_title)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(messageId)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                if (canReAsk) {
                    requestPermissions()
                } else {
                    openAppSettings(activity, blockedPermission)
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                onPermissionRejected?.invoke(blockedPermission)
                dialog.dismiss()
            }.apply {
                if (onPermissionRejected != null) {
                    setOnCancelListener { dialog ->
                        onPermissionRejected?.invoke(blockedPermission)
                        dialog.dismiss()
                    }
                }
            }
            .create()
            .apply {
                if (onPermissionRejected != null) {
                    setOnCancelListener { d ->
                        onPermissionRejected?.invoke(blockedPermission)
                        d.dismiss()
                    }
                    setOnKeyListener { arg0, keyCode, _ ->
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            onPermissionRejected?.invoke(blockedPermission)
                            arg0.dismiss()
                        }
                        true
                    }
                }
            }
            .show()
    }

    /**
     * Open application settings
     *
     * @param activity activity of application in which parameters we will go
     */
    open fun openAppSettings(activity: Activity, blockedPermission: String) {
        val intent = Intent()
            .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", activity.packageName, null))
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    putExtra(Intent.EXTRA_PACKAGE_NAME, activity.packageName)
                }
            }
        try {
            resultLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onPermissionRejected?.invoke(blockedPermission)
        }
    }

    companion object {
        private const val KEY_PERMISSION_STATES = "KEY_PERMISSION_STATES"
        private const val KEY_ALL_PERMISSION_STATES = "KEY_ALL_PERMISSION_STATES"
    }
}
