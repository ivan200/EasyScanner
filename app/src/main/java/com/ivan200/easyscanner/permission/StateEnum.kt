package com.ivan200.easyscanner.permission

/**
 * Current result state of specific permission
 *
 * Created by Ivan200 on 21.11.2020.
 */
enum class StateEnum {
    /** The exact state of permissions is unknown */
    UNKNOWN,

    /** Both before and after the request permissions were forbidden */
    REJECTED_ALL,

    /** Just been granted, first display of the dialog */
    FIRST_GRANTED,

    /** Just been denied, first display of the dialog */
    FIRST_DENIED,

    /** Just been denied, and user set checkbox to never ask */
    FIRST_NEVER_ASK,

    /** The user had a checkbox never ask */
    REJECTED_NEVER_ASK,

    /** They were denied, and now the user has granted it */
    SECOND_GRANTED,

    /** Were granted */
    ALWAYS_GRANTED;


    fun isDenied(): Boolean {
        return this == REJECTED_ALL
            || this == FIRST_DENIED
            || this == FIRST_NEVER_ASK
            || this == REJECTED_NEVER_ASK
    }

    fun canReAsk(): Boolean {
        return this == REJECTED_ALL
            || this == FIRST_DENIED
    }
}