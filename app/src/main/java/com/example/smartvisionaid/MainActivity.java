package com.example.smartvisionaid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String TAG               = "MAINACTIVITY";
    private static final long  SPEAK_INTERVAL_MS = 4000;
    private static final int PERMISSION_REQUEST = 200;

    private YoloDetector detector;
    private ObstacleAlertManager obstacleAlert;
    private DepthEstimator depthEstimator;
    private FaceDetectorHelper faceDetectorHelper;
    private FaceRecognizer faceRecognizer;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final AtomicBoolean ttsIsSpeaking = new AtomicBoolean(false);

    private ExecutorService cameraExecutor;
    private TextView statusText;
    private TextView micStatus;
    private OverlayView overlayView;

    private volatile boolean isDetecting   = false;
    private long  lastSpeakTime = 0;

    private volatile Bitmap lastBitmap;
    private volatile int lastBitmapWidth;

    private SpeechRecognizer speechRecognizer;
    private Handler mainHandler;
    private boolean isListening   = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        micStatus = findViewById(R.id.micStatus);
        overlayView = findViewById(R.id.overlayView);
        mainHandler= new Handler(Looper.getMainLooper());
        cameraExecutor = Executors.newSingleThreadExecutor();

        detector = new YoloDetector(this);
        obstacleAlert = new ObstacleAlertManager(this);
        depthEstimator = new DepthEstimator(this);
        faceDetectorHelper = new FaceDetectorHelper();
        faceRecognizer = new FaceRecognizer(this);

        checkPermissionsAndInit();
    }

    private void checkPermissionsAndInit() {
        String[] needed = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };
        List<String> missing = new ArrayList<>();
        for (String p : needed)
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED)
                missing.add(p);

        if (missing.isEmpty()) init();
        else ActivityCompat.requestPermissions(
                this, missing.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int req,
                                           String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == PERMISSION_REQUEST) {
            boolean ok = true;
            for (int g : grants)
                if (g != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            if (ok) init();
            else statusText.setText("Camera and mic permissions required");
        }
    }

    private void init() {
        startCamera();
        setupTts();
    }


    private void setupTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                ttsReady = true;

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        ttsIsSpeaking.set(true);
                        mainHandler.post(() -> {
                            if (speechRecognizer != null && isListening) {
                                speechRecognizer.stopListening();
                                isListening = false;
                            }
                        });
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        ttsIsSpeaking.set(false);
                        mainHandler.postDelayed(
                                MainActivity.this::startListening, 800);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        ttsIsSpeaking.set(false);
                        mainHandler.postDelayed(
                                MainActivity.this::startListening, 500);
                    }
                });

                mainHandler.postDelayed(() -> {
                    speak("Say detect to start");
                    mainHandler.postDelayed(this::setupSpeechRecognizer, 500);
                }, 500);

            } else {
                Log.e(TAG, "TTS init failed");
                mainHandler.postDelayed(() -> {
                    setupSpeechRecognizer();
                    startListening();
                }, 1000);
            }
        });
    }

    private void speak(String text) {
        if (tts != null && ttsReady) {
            ttsIsSpeaking.set(true);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                    "utt_" + System.currentTimeMillis());
        }
    }


    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            runOnUiThread(() -> statusText.setText(
                    "Speech recognition not available"));
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                Log.d(TAG, "Mic ready");
                runOnUiThread(() -> {
                    micStatus.setText("● Mic listening");
                    micStatus.setTextColor(0xFF00FF00);
                    if (!isDetecting)
                        statusText.setText("Say 'detect' to start");
                });
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                runOnUiThread(() -> {
                    micStatus.setText("● Mic off");
                    micStatus.setTextColor(0xFFFFFFFF);
                });
                List<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                Log.d(TAG, "Results: " + matches);
                if (matches != null && !matches.isEmpty())
                    handleVoiceCommand(
                            matches.get(0).toLowerCase(Locale.US));

                if (!ttsIsSpeaking.get())
                    mainHandler.postDelayed(
                            MainActivity.this::startListening, 300);
            }

            @Override
            public void onPartialResults(Bundle partial) {
                List<String> p = partial.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (p != null && !p.isEmpty()) {
                    String s = p.get(0).toLowerCase(Locale.US);
                    Log.d(TAG, "Partial: " + s);
                }
            }

            @Override
            public void onError(int error) {
                isListening = false;
                runOnUiThread(() -> {
                    micStatus.setText("● Mic off");
                    micStatus.setTextColor(0xFFFFFFFF);
                });
                Log.d(TAG, "Speech error: " + error);
                if (ttsIsSpeaking.get()) return;

                long delay;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        delay = 200; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        delay = 2000;
                        mainHandler.post(() -> {
                            if (speechRecognizer != null)
                                speechRecognizer.destroy();
                            setupSpeechRecognizer();
                        });
                        break;
                    default: delay = 800;
                }
                mainHandler.postDelayed(
                        MainActivity.this::startListening, delay);
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onEndOfSpeech() { isListening = false; }
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEvent(int t, Bundle p) {}
        });
    }

    private void startListening() {
        if (speechRecognizer == null) setupSpeechRecognizer();
        if (isListening) return;
        if (ttsIsSpeaking.get()) {
            Log.d(TAG, "startListening blocked — TTS speaking");
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        intent.putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        intent.putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L);

        try {
            speechRecognizer.startListening(intent);
            Log.d(TAG, "startListening called");
        } catch (Exception e) {
            Log.e(TAG, "startListening exception: " + e.getMessage());
            isListening = false;
            mainHandler.postDelayed(this::startListening, 1000);
        }
    }

    private void handleVoiceCommand(String spoken) {
        if (spoken.contains("i see") || spoken.contains("starting")
                || spoken.contains("detection") || spoken.contains("stopped")
                || spoken.contains("say detect")) {
            Log.d(TAG, "Ignoring TTS echo: " + spoken);
            return;
        }

        if (spoken.startsWith("remember this person as") || spoken.startsWith("remember this face as")) {
            String name = spoken.replace("remember this person as", "")
                    .replace("remember this face as", "")
                    .trim();
            Log.d(TAG, "Enroll command heard. Name = '" + name + "'");
            Bitmap frame = lastBitmap;
            if (frame != null && !name.isEmpty()) {
                Log.d(TAG, "Frame size: " + frame.getWidth() + "x" + frame.getHeight());
                faceDetectorHelper.detect(frame, faces -> {
                    Log.d(TAG, "Faces found for enrollment: " + faces.size());
                    if (!faces.isEmpty()) {
                        Bitmap crop = FaceDetectorHelper.cropFace(frame, faces.get(0).getBoundingBox());
                        faceRecognizer.enrollPerson(name, crop);
                        speak("Saved " + name);
                    } else {
                        speak("No face detected");
                    }
                });
            } else {
                speak("Camera not ready yet");
            }
            return;
        }

        if (spoken.contains("detect") || spoken.contains("start")) {
            if (!isDetecting) {
                isDetecting = true;
                runOnUiThread(() -> statusText.setText("Detecting..."));
                speak("Starting");
                Log.d(TAG, "Detection STARTED");
            }
        } else if (spoken.contains("stop") || spoken.contains("pause")) {
            if (isDetecting) {
                isDetecting = false;
                runOnUiThread(() -> overlayView.setResults(
                        new ArrayList<>(), null));
                runOnUiThread(() -> statusText.setText(
                        "Stopped. Say detect to resume."));
                speak("Stopped");
                Log.d(TAG, "Detection STOPPED");
            }
        }
    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                PreviewView previewView = findViewById(R.id.previewView);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    try {
                        Bitmap bmp = BitmapUtils.fromImageProxy(imageProxy);
                        if (bmp != null) {
                            lastBitmap = bmp;
                            lastBitmapWidth = bmp.getWidth();
                        }

                        if (!isDetecting) {
                            runOnUiThread(() -> overlayView.setResults(new ArrayList<>(), null));
                            return;
                        }

                        if (bmp != null) {
                            List<Detection> detections = detector.detect(bmp);
                            runOnUiThread(() -> overlayView.setResults(
                                    detections, detector.getLabels(), bmp.getWidth(), bmp.getHeight()));
                            announceDetections(detections);
                        }
                    } finally {
                        imageProxy.close();
                    }
                });


                provider.unbindAll();
                provider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, analysis);

                Log.d(TAG, "Camera started");

            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }


    /**
     * Builds the spoken sentence for the current frame's detections.
     * Face recognition is asynchronous (ML Kit), so "person" entries are
     * filled in only after their face lookup returns — the sentence is
     * only spoken once every entry (sync or async) has resolved, so a
     * recognized name always replaces "a person" rather than being
     * announced as a separate, trailing sentence.
     */
    private void announceDetections(List<Detection> detections) {
        if (detections.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastSpeakTime < SPEAK_INTERVAL_MS) return;
        lastSpeakTime = now;

        detections.sort((a, b) -> Float.compare(
                b.getWidth() * b.getHeight(),
                a.getWidth() * a.getHeight()));

        if (detections.size() > 3)
            detections = detections.subList(0, 3);

        final List<Detection> finalDetections = detections;
        final Bitmap frame = lastBitmap;
        final int frameWidth = lastBitmapWidth;

        final String[] phrases = new String[finalDetections.size()];
        final List<String> seenLabels = new ArrayList<>();
        final AtomicInteger pending = new AtomicInteger(0);

        for (int i = 0; i < finalDetections.size(); i++) {
            Detection d = finalDetections.get(i);
            String label = detector.getLabelForClass(d.classId);

            if (seenLabels.contains(label)) {
                phrases[i] = null;
                continue;
            }
            seenLabels.add(label);

            String dirDesc = DirectionHelper.getDirection(d);
            float dist = DistanceEstimator.estimateDistanceMeters(label, d.getWidth(), frameWidth);
            String distDesc = DistanceEstimator.describeDistance(dist);
            String suffix = dirDesc + (distDesc.isEmpty() ? "" : ", " + distDesc);

            if (obstacleAlert.shouldAlert(dist)) {
                obstacleAlert.vibrateAlert();
                speak("Warning! " + label + " very close " + dirDesc);
            }

            if (label.equals("person") && frame != null) {
                int px1 = Math.max(0, (int) (d.x1 * frame.getWidth()));
                int py1 = Math.max(0, (int) (d.y1 * frame.getHeight()));
                int px2 = Math.min(frame.getWidth(), (int) (d.x2 * frame.getWidth()));
                int py2 = Math.min(frame.getHeight(), (int) (d.y2 * frame.getHeight()));

                if (px2 > px1 && py2 > py1) {
                    pending.incrementAndGet();
                    final int idx = i;
                    Bitmap personCrop = Bitmap.createBitmap(frame, px1, py1, px2 - px1, py2 - py1);

                    faceDetectorHelper.detect(personCrop, faces -> {
                        String name = null;
                        if (!faces.isEmpty()) {
                            Bitmap faceCrop = FaceDetectorHelper.cropFace(
                                    personCrop, faces.get(0).getBoundingBox());
                            name = faceRecognizer.recognize(faceCrop);
                        }
                        phrases[idx] = (name != null ? name : "a person") + " " + suffix;

                        if (pending.decrementAndGet() == 0) {
                            speakFinalSentence(phrases);
                        }
                    });
                    continue; // phrases[i] filled in asynchronously above
                }
            }

            phrases[i] = "a " + label + " " + suffix;
        }

        // If nothing needed async face lookup, speak immediately
        if (pending.get() == 0) {
            speakFinalSentence(phrases);
        }
    }

    private void speakFinalSentence(String[] phrasesArr) {
        List<String> phrases = new ArrayList<>();
        for (String p : phrasesArr) if (p != null) phrases.add(p);
        if (phrases.isEmpty()) return;

        String sentence;
        if (phrases.size() == 1) {
            sentence = "I see " + phrases.get(0);
        } else if (phrases.size() == 2) {
            sentence = "I see " + phrases.get(0) + ", and " + phrases.get(1);
        } else {
            sentence = "I see " + phrases.get(0) + ", "
                    + phrases.get(1) + ", and " + phrases.get(2);
        }

        Log.d(TAG, "Announcing: " + sentence);
        runOnUiThread(() -> statusText.setText(sentence));
        speak(sentence);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (speechRecognizer != null && !isListening && !ttsIsSpeaking.get())
            mainHandler.postDelayed(this::startListening, 800);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        cameraExecutor.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}