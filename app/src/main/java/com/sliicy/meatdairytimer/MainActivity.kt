package com.sliicy.meatdairytimer

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), DefaultLifecycleObserver {

    // Timer data:
    private var countDownTimer: CountDownTimer? = null
    private var timerRunning: Boolean = false
    private var timeLeftInMillis: Long = 0
    private var endTime: Long = 0

    // User controls:
    private lateinit var buttonCountDown: Button
    private lateinit var buttonLookup: Button
    private lateinit var spinner: Spinner
    private lateinit var textViewTime: TextView

    // Channel used to deliver notifications:
    private val channelID: String = "0"
    private val notificationID: Int = 0

    private fun loadSettings() {
        val sp = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        var savedMinhag = sp.getInt("Minhag", 0)
        if (spinner.adapter.count <= savedMinhag)
            savedMinhag = 0
        spinner.setSelection(savedMinhag)
    }

    private fun saveSettings() {
        val sp = getSharedPreferences("sharedPrefs", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sp.edit()
        editor.apply {
            editor.putInt("Minhag", spinner.selectedItemPosition)
        }.apply()
    }

    //region Activity Handling
    private val lifecycleEventObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME ) {
            removeNotification()
        } else if ( event == Lifecycle.Event.ON_PAUSE ) {
            if (timerRunning)
                displayNotification(getString(R.string.you_can_eat_dairy_at), destinationTime(), true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)
        lifecycle.addObserver( lifecycleEventObserver )
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // Initialize controls:
        buttonCountDown = findViewById(R.id.buttonCountDown)
        buttonLookup = findViewById(R.id.buttonLookup)
        spinner = findViewById(R.id.spinnerMinhag)
        textViewTime = findViewById(R.id.textViewTime)

        createNotificationChannel()
        setupSpinner()
        loadSettings()
        updateCountDownText()
        buttonCountDown.setOnClickListener {
            if (timerRunning)
                stopTimer()
            else
                startTimer()
        }
        buttonLookup.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://halachipedia.com/index.php?title=Waiting_between_Meat_and_Milk"))
            startActivity(browserIntent)
        }
    }

    override fun onDestroy() {
        super<AppCompatActivity>.onDestroy()
        stopTimer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("millisLeft", timeLeftInMillis)
        outState.putBoolean("timerRunning", timerRunning)
        outState.putLong("endTime", endTime)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        timeLeftInMillis = savedInstanceState.getLong("millisLeft")
        timerRunning = savedInstanceState.getBoolean("timerRunning")
        updateCountDownText()
        updateCountDownButton()
        if (timerRunning) {
            endTime = savedInstanceState.getLong("endTime")
            timeLeftInMillis = endTime - System.currentTimeMillis()
            startTimer()
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    //endregion

    //region Notification Handling
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

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun displayNotification(title: String, description: String, sticky: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this,0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(this,0, intent, 0)
        }
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_notification_icon)
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
    
    private fun vibrate() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator

        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vib.vibrate(VibrationEffect.createOneShot(3000, 1))
        } else {
            val pattern = longArrayOf(0, 3000)
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, -1)
        }
    }

    //endregion

    private fun startTimer() {
        if (!timerRunning)
            timeLeftInMillis = getMillisecondsFromSpinner()
        endTime = System.currentTimeMillis() + timeLeftInMillis

        spinner.isEnabled = false

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
            }

            override fun onFinish() {
                timerRunning = false
                updateCountDownButton()
                removeNotification()
                displayNotification(getString(R.string.you_can_eat_dairy_now), convertToPlural(spinner.selectedItem.toString()) + " " + getString(R.string.elapsed_since) + " " + startTime() + ".", false)
                buttonCountDown.text = getString(R.string.start_countdown)
                spinner.isEnabled = true
                timeLeftInMillis = getMillisecondsFromSpinner()
                try {
                    val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val r = RingtoneManager.getRingtone(applicationContext, notification)
                    r.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                vibrate()
            }
        }.start()
        timerRunning = true
        updateCountDownButton()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        spinner.isEnabled = true
        timerRunning = false
        timeLeftInMillis = getMillisecondsFromSpinner()
        updateCountDownText()
        updateCountDownButton()
    }

    private fun updateCountDownText() {
        if (timerRunning)
            (getString(R.string.remaining_time) + " " + getStringTime(timeLeftInMillis.toInt() / 1000)).also { textViewTime.text = it }
        else
            textViewTime.text = ""
    }

    private fun updateCountDownButton() {
        if (timerRunning)
            buttonCountDown.text = getString(R.string.stop)
        else
            buttonCountDown.text = getString(R.string.start_countdown)
    }

    private fun startTime(): String {
        val cal: Calendar = GregorianCalendar()
        cal.add(Calendar.MILLISECOND, - (getMillisecondsFromSpinner().toInt() - timeLeftInMillis.toInt()))
        val timeFormat = SimpleDateFormat("hh:mm aa", Locale.US)
        timeFormat.timeZone = cal.timeZone
        return timeFormat.format(cal.time)
    }

    private fun destinationTime(): String {
        val cal: Calendar = GregorianCalendar()
        cal.add(Calendar.MILLISECOND, timeLeftInMillis.toInt())
        val timeFormat = SimpleDateFormat("hh:mm aa", Locale.US)
        timeFormat.timeZone = cal.timeZone
        return timeFormat.format(cal.time)
    }

    private fun setupSpinner() {
        val minhagArray = resources.getStringArray(R.array.minhag_array)
        val ad: ArrayAdapter<*> = ArrayAdapter<Any?>(this, android.R.layout.simple_spinner_item, minhagArray)
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
                updateCountDownText()
            }
            override fun onNothingSelected(parentView: AdapterView<*>?) {
            }
        }
    }

    private fun getMillisecondsFromSpinner(): Long {
        var timeAmount = 0L
        when (spinner.selectedItemPosition) {
            0 -> {
                timeAmount = 3600000 * 6
            }
            1 -> {
                timeAmount = 3600000 * 5 + 60000 * 31
            }
            2 -> {
                timeAmount = 3600000 * 5 + 60000
            }
            3 -> {
                timeAmount = 3600000 * 3
            }
            4 -> {
                timeAmount = 3600000
            }
        }
        return timeAmount
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
        var time: Int = inputTime
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
}