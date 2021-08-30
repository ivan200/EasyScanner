package com.ivan200.easyscanner.permission

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.Serializable

/**
 * Permission parameters
 *
 * Created by Ivan200 on 21.11.2020.
 */
@Suppress("MemberVisibilityCanBePrivate")
data class PermissionData (
    val permission: String,
    val messageIdOnPermissionRejected: Int,
    val messageIdOnPermissionBlocked: Int
) : Serializable {
    private var beforeRat: Boolean? = null
    private var afterRat: Boolean? = null

    /**
     * Set rationale state before requestPermissions
     *
     * @param activity to request shouldShowRequestPermissionRationale
     */
    fun setBefore(activity: Activity) {
        beforeRat = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Set rationale state after requestPermissions
     *
     * @param activity to request shouldShowRequestPermissionRationale
     */
    fun setAfter(activity: Activity) {
        afterRat = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun hasPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getState(activity: Activity): StateEnum {
        return when {
            beforeRat == null || afterRat == null -> StateEnum.UNKNOWN

            beforeRat!! && afterRat!! -> {
                StateEnum.REJECTED_ALL
            }

            beforeRat!! && !afterRat!! -> {
                if (!hasPermission(activity)) {
                    StateEnum.FIRST_NEVER_ASK
                } else {
                    StateEnum.FIRST_GRANTED
                }
            }
            !beforeRat!! && afterRat!! -> {
                if (!hasPermission(activity)) {
                    StateEnum.FIRST_DENIED
                } else {
                    StateEnum.SECOND_GRANTED
                }
            }
            !beforeRat!! && !afterRat!! -> {
                if (!hasPermission(activity)) {
                    StateEnum.REJECTED_NEVER_ASK
                } else {
                    StateEnum.ALWAYS_GRANTED
                }
            }
            else -> throw Exception()
        }
    }
}