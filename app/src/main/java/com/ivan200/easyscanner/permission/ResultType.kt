package com.ivan200.easyscanner.permission

/**
 * @author ivan200
 * @since 08.10.2022
 */
sealed class ResultType {
    /**
     * The user has given permissions
     */
    sealed class Allow : ResultType() {
        /**
         * Permissions may already be given.
         */
        object AlreadyHas : Allow()

        /**
         * We have requested permission using the system dialog and the user has granted permission
         */
        object SystemRequest : Allow()

        /**
         * We showed a custom dialog, the user went to the settings, changed the permissions there, and returned.
         */
        object AfterSettings : Allow()

        /**
         * After returning from the settings, the permissions have not yet been given.
         * We requested them again using the system dialog and the user allowed.
         */
        object SystemRequest2 : Allow()
    }

    /**
     * The user has not given permissions
     */
    sealed class Denied : ResultType() {
        /**
         * User refused to go to settings
         */
        object CustomDialogNo : Denied()

        /**
         * User canceled dialog to go to settings
         */
        object CustomDialogCancelled : Denied()

        /**
         * The user went to the settings, returned, and still refused to give permissions
         */
        object DeniedAfterSettings : Denied()

        /**
         * Can not go to application settings
         */
        object CanNotGoToSettings : Denied()
    }
}
