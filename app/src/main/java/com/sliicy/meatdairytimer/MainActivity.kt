package com.sliicy.meatdairytimer

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), LifecycleObserver {

    // Used to ensure only one timer is running:
    private var timerStarted = false

    // User controls:
    private lateinit var buttonCountDown: Button
    private lateinit var buttonLookup: Button
    private lateinit var spinner: Spinner
    private lateinit var switch: SwitchCompat
    private lateinit var textViewTime: TextView

    // Channel used to deliver notifications:
    private val channelID: String = "0"
    private val notificationID: Int = 0
    private var dairyTime: String = ""
    private var startTime: String = ""

    private fun loadSettings() {
        val sp = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val savedMinhag = sp.getInt("Minhag", 0)
        val notifyOnComplete = sp.getBoolean("AlertOnComplete", false)
        spinner.setSelection(savedMinhag)
        switch.isChecked = notifyOnComplete
    }

    private fun saveSettings() {
        val sp = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sp.edit()
        editor.apply {
            editor.putInt("Minhag", spinner.selectedItemPosition)
            editor.putBoolean("AlertOnComplete", switch.isChecked)
        }.apply()
    }

    private fun redrawCountdownButton() {
        buttonCountDown.isEnabled = !(spinner.selectedItemPosition == 0 && buttonCountDown.text != getString(R.string.stop))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun displayNotification(title: String, description: String, sticky: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = PendingIntent.getActivity(this,0, intent, 0)
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(com.google.android.material.R.drawable.ic_clock_black_24dp)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(sticky)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(0, builder.build())
        }
    }

    private fun removeNotification() {
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationID)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        if (buttonCountDown.text == getString(R.string.stop)) {
            displayNotification(getString(R.string.you_can_eat_dairy_at), dairyTime, true)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        removeNotification()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // Initialize controls:
        buttonCountDown = findViewById(R.id.buttonCountDown)
        buttonLookup = findViewById(R.id.buttonLookup)
        spinner = findViewById(R.id.spinnerMinhag)
        switch = findViewById(R.id.switchSoundComplete)
        textViewTime = findViewById(R.id.textViewTime)

        val minhagArray = resources.getStringArray(R.array.minhag_array)

        createNotificationChannel()

        val ad: ArrayAdapter<*> = ArrayAdapter<Any?>(
            this,
            android.R.layout.simple_spinner_item,
            minhagArray)
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = ad
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                saveSettings()
                redrawCountdownButton()
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
                redrawCountdownButton()
            }
        }
        loadSettings()
        redrawCountdownButton()

        buttonCountDown.setOnClickListener { startCountDownButtonClick() }
        buttonLookup.setOnClickListener { lookupHalacha() }
        switch.setOnClickListener { switchChanged() }
    }

    private fun startCountDownButtonClick() {
        if (buttonCountDown.text == getString(R.string.stop)) {
            buttonCountDown.text = getString(R.string.start_countdown)
            spinner.isEnabled = true
            textViewTime.text = null
            redrawCountdownButton()
            return
        }
        val timeAmount: Long
        val sdf = SimpleDateFormat("hh:mm aa", Locale.US)

        val calendar = Calendar.getInstance()
        calendar.time = Calendar.getInstance().time

        when (spinner.selectedItemPosition) {
            1 -> {
                timeAmount = 3600000 * 6
                calendar.add(Calendar.HOUR, 6)
            }
            2 -> {
                timeAmount = 3600000 * 5 + 60000 * 31
                calendar.add(Calendar.HOUR, 5)
                calendar.add(Calendar.MINUTE, 31)
            }
            3 -> {
                timeAmount = 3600000 * 5 + 60000
                calendar.add(Calendar.HOUR, 5)
                calendar.add(Calendar.MINUTE, 1)
            }
            4 -> {
                timeAmount = 3600000 * 3
                calendar.add(Calendar.HOUR, 3)
            }
            5 -> {
                timeAmount = 3600000
                calendar.add(Calendar.HOUR, 1)
            }
            else -> {
                textViewTime.text = getString(R.string.please_choose_a_minhag_first)
                redrawCountdownButton()
                return
            }
        }

        buttonCountDown.text = getString(R.string.stop)
        spinner.isEnabled = false
        if (timerStarted)
            return
        else
            timerStarted = true

        removeNotification()

        startTime = sdf.format(Calendar.getInstance().time)
        dairyTime = sdf.format(calendar.time)

        object : CountDownTimer(timeAmount, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                (getString(R.string.remaining_time) + " " + getStringTime(millisUntilFinished.toInt() / 1000)).also { textViewTime.text = it }
                if (buttonCountDown.text != getString(R.string.stop)) {
                    textViewTime.text = null
                    redrawCountdownButton()
                    timerStarted = false
                    spinner.isEnabled = true
                    removeNotification()
                    this.cancel()
                }
            }

            override fun onFinish() {
                removeNotification()

                displayNotification(getString(R.string.you_can_eat_dairy_now), convertToPlural(spinner.selectedItem.toString()) + " " + getString(R.string.elapsed_since) + " " + startTime + ".", false)
                (convertToPlural(spinner.selectedItem.toString()) + " " + getString(R.string.elapsed_since) + " " + startTime + ".").also { textViewTime.text = it }
                buttonCountDown.text = getString(R.string.start_countdown)
                timerStarted = false
                spinner.isEnabled = true

                redrawCountdownButton()
                if (switch.isChecked) {
                    try {
                        val notification: Uri =
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        val r = RingtoneManager.getRingtone(applicationContext, notification)
                        r.play()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500, 200, 500, 200)
                v.vibrate(pattern, -1)
            }
        }.start()
    }

    // Function responsible for returning the time-span and either 'have' or 'has', depending on grammar:
    private fun convertToPlural(input: String): String {
        return if (Locale.getDefault().language == "en") {
            input + " ha" + (if (input.lowercase().endsWith('s')) "ve" else "s")
        } else {
            input
        }
    }

    private fun pad(num: Int): String {
        return if (num < 10) "0$num" else num.toString()
    }

    private fun getStringTime(inputTime: Int): String {
        var time = inputTime
        var res: String? = null
        val hour: Int
        val min: Int
        val sec: Int
        if (time > 0) {
            hour = time / 3600
            time %= 3600
            min = time / 60
            sec = time % 60
            res = pad(hour) + ":" + pad(min) + ":" + pad(sec)
        }
        if (res == null) res = "00:00:00"
        return res
    }

    private fun switchChanged() {
        saveSettings()
    }

    private fun lookupHalacha() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://halachipedia.com/index.php?title=Waiting_between_Meat_and_Milk"))
        startActivity(browserIntent)
    }
}