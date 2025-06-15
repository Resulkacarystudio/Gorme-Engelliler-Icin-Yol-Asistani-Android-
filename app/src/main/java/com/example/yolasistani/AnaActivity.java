package com.example.yolasistani;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class AnaActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private final int REQ_CODE_SPEECH_INPUT = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ana);

        Button yolBtn = findViewById(R.id.yolBtn);
        Button marketBtn = findViewById(R.id.marketBtn);
        Button sesliBtn = findViewById(R.id.sesliSecimBtn);

        yolBtn.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));

        marketBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Market Asistanı henüz aktif değil", Toast.LENGTH_SHORT).show();
        });

        sesliBtn.setOnClickListener(v -> startVoiceRecognition());

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("tr", "TR"));
                tts.setSpeechRate(1.2f);
            }
        });
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Bir asistan ismi söyleyin...");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Sesli komut başlatılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spoken = results.get(0).toLowerCase(Locale.ROOT);

            if (spoken.contains("yol")) {
                tts.speak("Yol asistanı başlatılıyor", TextToSpeech.QUEUE_FLUSH, null, null);
                startActivity(new Intent(this, MainActivity.class));
            } else if (spoken.contains("market")) {
                tts.speak("Market asistanı henüz hazır değil", TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                tts.speak("Bu komutu anlayamadım", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
