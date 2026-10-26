package com.mihir.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Listens for one sentence with the TV's speech service and stops by itself after about
 * {@link #SILENCE_MS} without new words, like a voice assistant. Shows what is being heard live.
 * Falls back to the system's voice screen when the speech service can't be used directly.
 */
final class VoiceCapture {

    interface Callback {
        void onText(String text);
        /** The direct recognizer is unavailable; the caller should use the system voice screen. */
        void onFallback();
    }

    static final long SILENCE_MS = 3000;
    /** Give up if nobody says anything at all. */
    private static final long NO_SPEECH_MS = 8000;

    private final Activity act;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private PopupWindow window;
    private TextView heard;
    private String best = "";
    private boolean done;

    private final Runnable stopOnSilence = () -> { if (recognizer != null) recognizer.stopListening(); };

    VoiceCapture(Activity a, Callback c) {
        act = a;
        callback = c;
    }

    void start() {
        if (!SpeechRecognizer.isRecognitionAvailable(act)) { callback.onFallback(); return; }
        recognizer = SpeechRecognizer.createSpeechRecognizer(act);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { heard.setText("Pode falar…"); restartTimer(NO_SPEECH_MS); }
            @Override public void onBeginningOfSpeech() { restartTimer(SILENCE_MS + 2000); }
            @Override public void onRmsChanged(float db) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int type, Bundle b) { }

            @Override public void onPartialResults(Bundle b) {
                String t = first(b);
                if (t.isEmpty()) return;
                best = t;
                heard.setText(t);
                restartTimer(SILENCE_MS); // each new word pushes the end back by 3 s
            }

            @Override public void onResults(Bundle b) {
                String t = first(b);
                finish(t.isEmpty() ? best : t);
            }

            @Override public void onError(int error) {
                if (done) return;
                if (!best.isEmpty()) { finish(best); return; }
                boolean noAccess = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
                close();
                if (noAccess) callback.onFallback();
                else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    android.widget.Toast.makeText(act, "Não ouvi nada. Tente de novo.", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(act, "Reconhecimento de voz indisponível agora", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        showOverlay();
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, act.getPackageName());
        try {
            recognizer.startListening(i);
        } catch (RuntimeException e) {
            close();
            callback.onFallback();
        }
    }

    private void restartTimer(long ms) {
        handler.removeCallbacks(stopOnSilence);
        handler.postDelayed(stopOnSilence, ms);
    }

    private void finish(String text) {
        if (done) return;
        done = true;
        close();
        if (text != null && !text.trim().isEmpty()) callback.onText(text.trim());
        else android.widget.Toast.makeText(act, "Não ouvi nada. Tente de novo.", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void close() {
        handler.removeCallbacks(stopOnSilence);
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (RuntimeException ignored) { }
            recognizer = null;
        }
        if (window != null) { window.setOnDismissListener(null); window.dismiss(); window = null; }
    }

    private static String first(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() || r.get(0) == null ? "" : r.get(0);
    }

    /** A small card at the top: a red mic, the hint, and the words heard so far. Back cancels. */
    private void showOverlay() {
        LinearLayout box = new LinearLayout(act);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(16), dp(26), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0xF51C1C1E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        box.setBackground(bg);

        TextView mic = new TextView(act);
        mic.setText("🎤");
        mic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mic.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFFE53935);
        mic.setBackground(dot);
        box.addView(mic, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView hint = text(11, "sans-serif-medium", 0x99FFFFFF);
        hint.setText("NOVO COMPROMISSO · encerra sozinho após 3 s de silêncio · Voltar cancela");
        texts.addView(hint);
        heard = text(20, "sans-serif-light", Color.WHITE);
        heard.setText("Preparando o microfone…");
        heard.setMaxLines(2);
        texts.addView(heard);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMargins(dp(18), 0, 0, 0);
        box.addView(texts, tp);

        window = new PopupWindow(box, act.getResources().getDisplayMetrics().widthPixels * 2 / 3, -2, true);
        window.setElevation(dp(24));
        window.setOnDismissListener(() -> { window = null; if (!done) { done = true; close(); } });
        window.showAtLocation(act.getWindow().getDecorView(), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(40));
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }
}
