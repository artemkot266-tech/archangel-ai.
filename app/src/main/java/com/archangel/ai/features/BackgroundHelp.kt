package com.archangel.ai.features

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Экраны системных настроек, без которых HyperOS/MIUI закрывает фоновые приложения. */
object BackgroundHelp {
    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }

    private fun appDetails(context: Context) =
        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))

    fun ignoringBatteryLimits(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** «Не ограничивать» расход батареи. */
    fun batteryUnrestricted(context: Context) {
        val ok = open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)))
        if (!ok) appDetails(context)
    }

    /** Автозапуск на Xiaomi/Poco (HyperOS, MIUI). На других телефонах — карточка приложения. */
    fun autostart(context: Context) {
        val xiaomi = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        if (!open(context, xiaomi)) appDetails(context)
    }

    fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

    /** «Поверх других окон» — чтобы из фона открывать YouTube, музыку, такси, звонилку. */
    fun overlay(context: Context) {
        val ok = open(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
        if (!ok) appDetails(context)
    }

    /**
     * Xiaomi/Poco: «Другие разрешения» приложения — там «Отображать всплывающие окна в фоне»
     * и «Показывать на экране блокировки». Без них HyperOS не даёт открывать приложения из фона.
     */
    fun xiaomiPermissions(context: Context) {
        val pkg = context.packageName
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg)
        val ok = open(context, Intent(editor).setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")) ||
            open(context, Intent(editor).setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")) ||
            open(context, editor)
        if (!ok) appDetails(context)
    }
}
