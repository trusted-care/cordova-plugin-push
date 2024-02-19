package com.adobe.phonegap.push

import android.content.Context

/*
* Resources mapper class to get resources id with variable project package name
* */
object ResourcesMapper {

    fun getId(context: Context, name: String): Int {
        return getResId(context, name, ResourcesKeys.RES_TYPE_ID)
    }

    fun getLayout(context: Context, name: String): Int {
        return getResId(context, name, ResourcesKeys.RES_TYPE_LAYOUT)
    }

    fun getString(context: Context, name: String): Int {
        return getResId(context, name, ResourcesKeys.RES_TYPE_STRING)
    }

    fun getDrawable(context: Context, name: String): Int {
        return getResId(context, name, ResourcesKeys.RES_TYPE_DRAWABLE)
    }

    fun getResId(context: Context, name: String, type: String): Int {
        val packageName = context.packageName
        return context.resources.getIdentifier(name, type, packageName)
    }
}
