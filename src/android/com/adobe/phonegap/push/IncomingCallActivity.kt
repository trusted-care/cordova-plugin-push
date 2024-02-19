package com.adobe.phonegap.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat

private const val POST_NOTIFICATIONS_REQUEST_CODE = 8234

private const val RES_TYPE_ID = "id"
private const val RES_TYPE_DRAWABLE = "drawable"
private const val RES_TYPE_LAYOUT = "layout"

private const val RES_LAYOUT_ACTIVITY_INCOMING_CALL = "activity_incoming_call"
private const val RES_TV_CALLER = "tvCaller"
private const val RES_BTN_ACCEPT = "btnAccept"
private const val RES_BTN_DECLINE = "btnDecline"
private const val RES_ANIMATED_CIRCLE = "ivAnimatedCircle"
private const val RES_CIRCLE_ANIMATION_AVD = "circle_animation_avd"

class IncomingCallActivity : Activity() {

    var caller: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        showWhenLockedAndTurnScreenOn()
        super.onCreate(savedInstanceState)

        Log.d("", "IncomingCallActivity.onCreate()")
        val activityIncomingCallRes = getResId(RES_LAYOUT_ACTIVITY_INCOMING_CALL, RES_TYPE_LAYOUT)
        setContentView(activityIncomingCallRes)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        instance = this

        val tvCallerRes = getResId(RES_TV_CALLER, RES_TYPE_ID)
        val btnAcceptRes = getResId(RES_BTN_ACCEPT, RES_TYPE_ID)
        val btnDeclineRes = getResId(RES_BTN_DECLINE, RES_TYPE_ID)
        val ivAnimatedCircleRes = getResId(RES_ANIMATED_CIRCLE, RES_TYPE_ID)
        val circleAnimationAvdRes = getResId(RES_CIRCLE_ANIMATION_AVD, RES_TYPE_DRAWABLE)

        caller = intent?.extras?.getString("caller") ?: ""
        (findViewById<TextView>(tvCallerRes)).text = caller
        val btnAccept: Button = findViewById(btnAcceptRes)
        val btnDecline: Button = findViewById(btnDeclineRes)

        btnAccept.setOnClickListener { v -> requestPhoneUnlock() }
        btnDecline.setOnClickListener { v -> declineIncomingVoIP() }

        val animatedCircle: ImageView = findViewById(ivAnimatedCircleRes)
        val drawableCompat = AnimatedVectorDrawableCompat.create(this, circleAnimationAvdRes)
        animatedCircle.setImageDrawable(drawableCompat)
        drawableCompat?.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
            private val fHandler = Handler(Looper.getMainLooper())
            override fun onAnimationEnd(drawable: Drawable?) {
                super.onAnimationEnd(drawable)
                if (instance != null) {
                    fHandler.post(drawableCompat::start)
                }
            }
        })

        drawableCompat?.start()
    }

    private fun getResId(name: String, type: String): Int {
        return resources.getIdentifier(name, type, packageName)
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onBackPressed() {
        // Do nothing on back button
    }

    private fun requestPhoneUnlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val context = this.applicationContext
        if (km.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                        acceptIncomingVoIP()
                    }

                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                    }

                    override fun onDismissError() {
                        super.onDismissError()
                    }
                })
            } else {
                acceptIncomingVoIP()
                if (km.isKeyguardSecure) {
                    // Register receiver for dismissing "Unlock Screen" notification
                    phoneUnlockBR = PhoneUnlockBroadcastReceiver()
                    val filter = IntentFilter()
                    filter.addAction(Intent.ACTION_USER_PRESENT)
                    phoneUnlockBR?.apply {
                        context?.registerReceiver(this as BroadcastReceiver, filter)
                    }
                    showUnlockScreenNotification()
                } else {
                    val myLock: KeyguardManager.KeyguardLock = km.newKeyguardLock("AnswerCall")
                    myLock?.disableKeyguard()
                }
            }
        } else {
            acceptIncomingVoIP()
        }
    }

    fun acceptIncomingVoIP() {
        Log.d("IC", "acceptIncomingVoIP")
        IncomingCallHelper.handleActionCall(applicationContext, intent, PushConstants.VOIP_ACCEPT_KEY)
    }

    private fun declineIncomingVoIP() {
        Log.d("IC", "declineIncomingVoIP")
        IncomingCallHelper.handleActionCall(applicationContext, intent, PushConstants.VOIP_DECLINE_KEY)
    }

    @SuppressLint("MissingPermission")
    private fun showUnlockScreenNotification() {
        val notificationBuilder = NotificationCompat.Builder(this, PushConstants.DEFAULT_CHANNEL_ID)
            .setSmallIcon(resources.getIdentifier("pushicon", "drawable", packageName))
            .setContentTitle("Ongoing call with $caller")
            .setContentText("Please unlock your device to continue")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(false)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle())
            .setSound(null)
        val ongoingCallNotification = notificationBuilder.build()
        val notificationManager = NotificationManagerCompat.from(this.applicationContext)
        // Display notification
        if (!isPostNotificationsGranted()) {
            requestPostNotifications()
        } else {
            notificationManager.notify(NOTIFICATION_MESSAGE_ID, ongoingCallNotification)
        }
    }

    private fun isPostNotificationsGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                POST_NOTIFICATIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE &&
            grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED
        ) {
            showUnlockScreenNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    class PhoneUnlockBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action.equals(Intent.ACTION_USER_PRESENT)) {
                dismissUnlockScreenNotification(context.applicationContext)
            }
        }
    }

    companion object {

        private const val NOTIFICATION_MESSAGE_ID = 1337

        var instance: IncomingCallActivity? = null

        var phoneUnlockBR: PhoneUnlockBroadcastReceiver? = null
        fun dismissUnlockScreenNotification(applicationContext: Context) {
            NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_MESSAGE_ID)
            if (phoneUnlockBR != null) {
                applicationContext.unregisterReceiver(phoneUnlockBR)
                phoneUnlockBR = null
            }
        }
    }
}
