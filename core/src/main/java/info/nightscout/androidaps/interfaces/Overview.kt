package info.nightscout.androidaps.interfaces

import androidx.annotation.StringRes
import info.nightscout.androidaps.plugins.bus.RxBus

interface Overview : ConfigExportImport {

    val overviewBus: RxBus

    /**
     * Add notification that shows dialog after clicking button
     * @param id if of notification
     * @text text of notification
     * @level urgency level of notification
     * @actionButtonId label of button
     * @title Dialog title
     * @message Dialog body
     */
    fun addNotificationWithDialogResponse(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, title: String, message: String)
    /**
     * Add notification that executes [Runnable] after clicking button
     * @param id if of notification
     * @text text of notification
     * @level urgency level of notification
     * @actionButtonId label of button
     * @action Runnable to be run
     */
    fun addNotification(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, action: Runnable)
    /**
     * Remove notification
     * @param id if of notification
     */
    fun dismissNotification(id: Int)
}