package com.example.ai

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device Admin Receiver enabling Neli AI's hands-free `"Hey Neli lock my screen"` command.
 * Strictly requests only `force-lock` policy — no wipe or password changes.
 */
class NeliDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun componentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, NeliDeviceAdminReceiver::class.java)
        }

        fun isLockPermissionGranted(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.isAdminActive(componentName(context)) == true
            } catch (_: Exception) {
                false
            }
        }

        fun buildEnableAdminIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Allow Neli AI to lock your screen when you say \"Hey Neli lock my screen\"."
                )
            }
        }

        /**
         * Locks the device immediately if admin permission is granted.
         * Returns true if locked, false if permission still needs to be granted in NeliTv AI Settings.
         */
        fun lockDeviceNow(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                    ?: return false
                if (dpm.isAdminActive(componentName(context))) {
                    dpm.lockNow()
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
