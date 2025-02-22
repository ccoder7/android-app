package one.mixin.android.ui.call

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import one.mixin.android.R
import one.mixin.android.vo.CallStateLiveData
import one.mixin.android.vo.CallType
import one.mixin.android.webrtc.ACTION_CALL_ANSWER
import one.mixin.android.webrtc.ACTION_CALL_CANCEL
import one.mixin.android.webrtc.ACTION_CALL_DECLINE
import one.mixin.android.webrtc.ACTION_CALL_LOCAL_END
import one.mixin.android.webrtc.ACTION_KRAKEN_ACCEPT_INVITE
import one.mixin.android.webrtc.ACTION_KRAKEN_CANCEL
import one.mixin.android.webrtc.ACTION_KRAKEN_DECLINE
import one.mixin.android.webrtc.ACTION_KRAKEN_END
import one.mixin.android.webrtc.CallService
import one.mixin.android.webrtc.GroupCallService
import one.mixin.android.webrtc.VoiceCallService
import timber.log.Timber

class CallNotificationBuilder {

    companion object {
        private const val CHANNEL_NODE = "channel_node"
        const val WEBRTC_NOTIFICATION = 313388

        fun getCallNotification(context: Context, callState: CallStateLiveData): Notification? {
            val callType = callState.callType
            if (callState.isIdle() || callType == CallType.None) {
                Timber.w("try get a call notification for foreground service in idle state.")
                return null
            }

            val callIntent = Intent(context, CallActivity::class.java)
            callIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val user = callState.user
            val pendingCallIntent = PendingIntent.getActivity(context, 0, callIntent, FLAG_UPDATE_CURRENT)

            val builder = NotificationCompat.Builder(context, CHANNEL_NODE)
                .setSmallIcon(R.drawable.ic_msg_default)
                .setContentIntent(pendingCallIntent)
                .setOngoing(true)
                .setContentTitle(user?.fullName)

            val isGroupCall = callType == CallType.Group
            val clazz = if (isGroupCall) {
                GroupCallService::class.java
            } else {
                VoiceCallService::class.java
            }
            when (callState.state) {
                CallService.CallState.STATE_DIALING -> {
                    builder.setContentText(context.getString(R.string.call_notification_outgoing))
                    val action = if (isGroupCall) {
                        ACTION_KRAKEN_CANCEL
                    } else ACTION_CALL_CANCEL
                    builder.addAction(
                        getAction(
                            context, clazz, action, R.drawable.ic_close_black,
                            R.string
                                .call_notification_action_cancel
                        )
                    )
                }
                CallService.CallState.STATE_RINGING -> {
                    builder.setContentText(context.getString(R.string.call_notification_incoming_voice))
                    val answerAction = if (isGroupCall) {
                        ACTION_KRAKEN_ACCEPT_INVITE
                    } else ACTION_CALL_ANSWER
                    val declineAction = if (isGroupCall) {
                        ACTION_KRAKEN_DECLINE
                    } else ACTION_CALL_DECLINE
                    builder.addAction(
                        getAction(
                            context, clazz, answerAction, R.drawable.ic_close_black,
                            R.string
                                .call_notification_action_answer
                        )
                    )
                    builder.addAction(
                        getAction(
                            context, clazz, declineAction, R.drawable.ic_close_black,
                            R.string
                                .call_notification_action_decline
                        )
                    )
                }
                CallService.CallState.STATE_CONNECTED -> {
                    builder.setContentText(context.getString(R.string.call_notification_connected))
                    val action = if (isGroupCall) {
                        ACTION_KRAKEN_END
                    } else ACTION_CALL_LOCAL_END
                    builder.addAction(
                        getAction(
                            context, clazz, action, R.drawable.ic_close_black,
                            R.string
                                .call_notification_action_hang_up
                        )
                    )
                }
                else -> {
                    builder.setContentText(context.getString(R.string.call_connecting))
                    val action = if (isGroupCall) {
                        ACTION_KRAKEN_CANCEL
                    } else {
                        if (callState.isOffer) ACTION_CALL_CANCEL else ACTION_CALL_DECLINE
                    }
                    builder.addAction(
                        getAction(
                            context, clazz, action, R.drawable.ic_close_black,
                            R.string
                                .call_notification_action_hang_up
                        )
                    )
                }
            }
            return builder.build()
        }

        private fun getAction(
            context: Context,
            clazz: Class<*>,
            action: String,
            iconResId: Int,
            titleResId: Int,
            putExtra: ((intent: Intent) -> Unit)? = null
        ): NotificationCompat.Action {
            val intent = Intent(context, clazz)
            intent.action = action
            putExtra?.invoke(intent)
            val pendingIntent = PendingIntent.getService(context, 0, intent, FLAG_UPDATE_CURRENT)
            return NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent)
        }
    }
}
