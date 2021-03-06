package me.blog.korn123.easydiary.activities

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Vibrator
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import com.simplemobiletools.commons.extensions.*
import kotlinx.android.synthetic.main.activity_diary_reminder.*
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.extensions.config
import me.blog.korn123.easydiary.helper.DIARY_INSERT_MODE
import me.blog.korn123.easydiary.helper.EasyDiaryDbHelper
import me.blog.korn123.easydiary.helper.MODE_REMINDER
import me.blog.korn123.easydiary.helper.TransitionHelper
import me.blog.korn123.easydiary.models.Alarm

class DiaryReminderActivity : EasyDiaryActivity() {


    /***************************************************************************************************
     *   global properties
     *
     ***************************************************************************************************/
    private var mAlarm: Alarm? = null
    private val swipeGuideFadeHandler = Handler()
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var lastVolumeValue = 0.1f
    private var didVibrate = false
    private var dragDownX = 0f

    /***************************************************************************************************
     *   override functions
     *
     ***************************************************************************************************/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary_reminder)
        showOverLockScreen()

        val alarmId = intent.getIntExtra(DevActivity.ALARM_ID, -1)
        mAlarm = EasyDiaryDbHelper.readAlarmBy(alarmId)

        setupVibrator()
//        setupAudio()
        setupEvent()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
    }


    /***************************************************************************************************
     *   etc functions
     *
     ***************************************************************************************************/
    private fun setupEvent() {
        setupAlarmButtons()
    }

    private fun setupAlarmButtons() {
        reminder_draggable_background.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulsing_animation))
        reminder_draggable_background.applyColorFilter(getAdjustedPrimaryColor())

        reminder_dismiss.applyColorFilter(config.textColor)
        reminder_draggable.applyColorFilter(config.textColor)
        reminder_snooze.applyColorFilter(config.textColor)

        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f

        reminder_dismiss.onGlobalLayout {
            minDragX = reminder_snooze.left.toFloat()
            maxDragX = reminder_dismiss.left.toFloat()
            initialDraggableX = reminder_draggable.left.toFloat()
        }

        reminder_draggable.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    reminder_draggable_background.animate().alpha(0f)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    if (!didVibrate) {
                        reminder_draggable.animate().x(initialDraggableX).withEndAction {
                            reminder_draggable_background.animate().alpha(0.2f)
                        }

                        reminder_guide.animate().alpha(1f).start()
                        swipeGuideFadeHandler.removeCallbacksAndMessages(null)
                        swipeGuideFadeHandler.postDelayed({
                            reminder_guide.animate().alpha(0f).start()
                        }, 2000L)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    reminder_draggable.x = Math.min(maxDragX, Math.max(minDragX, event.rawX - dragDownX))
                    if (reminder_draggable.x >= maxDragX - 50f) {
                        if (!didVibrate) {
                            reminder_draggable.performHapticFeedback()
                            didVibrate = true
                            finishActivity()
                        }
                    } else if (reminder_draggable.x <= minDragX + 50f) {
                        if (!didVibrate) {
                            reminder_draggable.performHapticFeedback()
                            didVibrate = true
//                            snoozeAlarm()
                            finishActivity()
                            TransitionHelper.startActivityWithTransition(this, Intent(this, DiaryInsertActivity::class.java).apply {
                                putExtra(DIARY_INSERT_MODE, MODE_REMINDER)
                            })
                        }
                    }
                }
            }
            true
        }
    }

    private fun setupVibrator() {
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(100, 300, 100, 700, 300, 2000)
        vibrator?.vibrate(pattern, 1)
    }

    private fun setupAudio() {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setDataSource(this@DiaryReminderActivity, soundUri)
                setVolume(lastVolumeValue, lastVolumeValue)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun destroyPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
    }

    private fun finishActivity() {
        mAlarm?.let {
            scheduleNextAlarm(it, true)
        }
        destroyPlayer()
        finish()
        overridePendingTransition(0, 0)
    }
}