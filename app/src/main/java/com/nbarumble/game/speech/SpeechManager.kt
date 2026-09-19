package com.nbarumble.game.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Thin wrapper over Android's free SpeechRecognizer with a hold-to-speak
 * lifecycle: startListening() while the user holds the mic, stopListening()
 * on release.
 *
 * Samsung's speech stack (Soda/NetworkSpeechRecognizer) can strand a long-lived
 * recognizer so that final results silently never arrive. To sidestep that,
 * every session gets a FRESH SpeechRecognizer: startListening() destroys the
 * previous one and creates a new one, and a watchdog guarantees a session
 * always terminates even if the service never calls back.
 */
class SpeechManager(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onRecognized(texts: List<String>)
        fun onNotRecognized(message: String)
        fun onListeningStarted()
        fun onListeningStopped()
        fun onBusyChanged(busy: Boolean)
    }

    private val appContext = context.applicationContext

    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var busy = false
    private var destroyed = false

    private val handler = Handler(Looper.getMainLooper())
    private var sessionToken = 0L
    private var startTimeMs = 0L
    private var lastEndMs = -SETTLE_MS

    private val available = SpeechRecognizer.isRecognitionAvailable(appContext)

    val isAvailable: Boolean get() = available

    fun startListening() {
        // While a session is in flight (listening or waiting for its result)
        // a new press must NOT tear it down — it would interrupt the recording.
        if (destroyed || active || busy || !available) return
        val sinceEnd = SystemClock.elapsedRealtime() - lastEndMs
        if (sinceEnd < SETTLE_MS) {
            // The previous session may still be winding down (especially after
            // an error). Defer this press by the remaining settle time instead
            // of starting instantly — starting too fast is what makes the UI lag.
            Log.d(TAG, "defer start by ${SETTLE_MS - sinceEnd}ms")
            val token = sessionToken
            handler.postDelayed({
                if (!destroyed && !active && token == sessionToken) {
                    startNow()
                }
            }, SETTLE_MS - sinceEnd + 20L)
            return
        }
        startNow()
    }

    private fun startNow() {
        if (destroyed || active || busy || !available) return
        active = true
        setBusy(true)
        Log.d(TAG, "startListening")
        startTimeMs = SystemClock.elapsedRealtime()
        try {
            // Tear the old recognizer down off the critical path so the UI
            // thread never blocks on the speech service.
            val old = recognizer
            recognizer = null
            if (old != null) {
                val token = sessionToken
                handler.postDelayed({
                    if (!destroyed && token == sessionToken) {
                        try {
                            old.destroy()
                        } catch (_: Throwable) {
                        }
                    }
                }, DESTROY_DELAY_MS)
            }
            val newRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(newRecognitionListener())
            }
            recognizer = newRecognizer
            sessionToken++
            startWatchdog(sessionToken)
            newRecognizer.startListening(createIntent())
        } catch (t: Throwable) {
            Log.e(TAG, "startListening failed", t)
            active = false
            lastEndMs = SystemClock.elapsedRealtime()
            releaseSession()
            listener.onNotRecognized("Speech service unavailable — try again")
        }
    }

    fun stopListening() {
        if (!active || destroyed) return
        // A quick tap is a click, not speech — cancel silently instead of
        // letting the recognizer announce SPEECH_TIMEOUT/NO_MATCH.
        if (SystemClock.elapsedRealtime() - startTimeMs < MIN_HOLD_MS) {
            Log.d(TAG, "tap too short — cancelling silently")
            active = false
            setBusy(false)
            lastEndMs = SystemClock.elapsedRealtime()
            try {
                recognizer?.cancel()
            } catch (_: Throwable) {
            }
            return
        }
        Log.d(TAG, "stopListening")
        try {
            recognizer?.stopListening()
        } catch (_: Throwable) {
            // already stopped
        }
    }

    fun cancel() {
        if (!active || destroyed) return
        Log.d(TAG, "cancel")
        active = false
        setBusy(false)
        lastEndMs = SystemClock.elapsedRealtime()
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
    }

    fun destroy() {
        destroyed = true
        sessionToken++
        recognizer?.destroy()
        recognizer = null
        active = false
        busy = false
    }

    /** Sessions receive results a second or two after the release; cap it defensively. */
    private fun startWatchdog(token: Long) {
        handler.postDelayed({
            if (!destroyed && active && token == sessionToken) {
                Log.d(TAG, "watchdog fired")
                active = false
                releaseSession()
                listener.onListeningStopped()
                listener.onNotRecognized("Didn't catch that — try again")
            }
        }, WATCHDOG_MS)
    }

    private fun createIntent(): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        return intent
    }

    /**
     * Each session installs its own listener so that late callbacks from a
     * destroyed recognizer can never corrupt an active session.
     */
    private fun newRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (destroyed || !active) return
                Log.d(TAG, "onReadyForSpeech")
                listener.onListeningStarted()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (destroyed) return
                Log.d(TAG, "onEndOfSpeech")
                listener.onListeningStopped()
            }

            override fun onError(error: Int) {
                if (destroyed) return
                Log.d(TAG, "onError=$error")
                if (!active) return
                active = false
                releaseSession()
                listener.onListeningStopped()
                listener.onNotRecognized(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                if (destroyed || !active) return
                Log.d(TAG, "onResults")
                active = false
                releaseSession()
                val texts = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                    ?.distinct()
                    .orEmpty()
                if (texts.isEmpty()) {
                    listener.onNotRecognized("Didn't catch that — try again")
                } else {
                    Log.d(TAG, "recognized: ${texts.joinToString(" | ")}")
                    listener.onRecognized(texts)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            private fun errorMessage(code: Int): String = when (code) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that — try again"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Too fast — try again"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                SpeechRecognizer.ERROR_AUDIO -> "Couldn't hear you — try again"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service unavailable"
                else -> "Couldn't hear you — try again"
            }
        }
    }

    /** Tear down the recognizer for a finished session so the next one starts clean. */
    private fun releaseSession() {
        sessionToken++
        setBusy(false)
        lastEndMs = SystemClock.elapsedRealtime()
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun setBusy(value: Boolean) {
        if (busy == value) return
        busy = value
        listener.onBusyChanged(value)
    }

    companion object {
        private const val TAG = "SpeechManager"
        private const val WATCHDOG_MS = 8_000L
        private const val MIN_HOLD_MS = 200L
        private const val SETTLE_MS = 800L
        private const val DESTROY_DELAY_MS = 300L
    }
}