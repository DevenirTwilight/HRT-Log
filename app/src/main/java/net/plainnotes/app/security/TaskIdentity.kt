package net.plainnotes.app.security

import android.app.ActivityManager
import android.os.Build

/** Explicit task identity: activity aliases/application labels alone do not control every Recents implementation. */
@Suppress("DEPRECATION")
fun taskIdentity(label:String,icon:Int):ActivityManager.TaskDescription =
    if(Build.VERSION.SDK_INT>=28) ActivityManager.TaskDescription(label,icon,0) else ActivityManager.TaskDescription(label)
