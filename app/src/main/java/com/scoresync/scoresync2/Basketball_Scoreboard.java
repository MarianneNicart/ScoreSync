package com.scoresync.scoresync2;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.scoresync.scoresync2.model.GameHistory;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Basketball_Scoreboard extends AppCompatActivity {

    private TextView team1FoulDisplay, team2FoulDisplay;
    private TextView team1ScoreDisplay, team2ScoreDisplay;
    private TextView periodCounter, mainTimer;
    private TextView team1Label, team2Label;
    private Button minusteam1, minusteam2;
    private Button addFoulButton;
    private ImageButton shuffleButton;
    private ImageButton startTimerButton;
    private CountDownTimer timer;
    private CountDownTimer shotclocktimer;
    private TextView shotclockview;
    private final long INTERVAL = 100;
    private boolean timerRunning = false;
    private long timeLeftInMillis = 720000; // 12 minutes in milliseconds
    private long shotclock = 24000;

    // Sound variables
    private SoundPool soundPool;
    private int buzzerSoundId, whistleSoundId;
    private boolean soundsLoaded = false;

    // Game state variables
    private int team1Score = 0;
    private int team2Score = 0;
    private int team1Fouls = 0;
    private int team2Fouls = 0;
    private int period = 1;
    private int setPeriod;
    private boolean isShuffled = false;
    private int overtime = 1;
    private int setTime;

    private boolean isInOvertime = false;
    private int currentOvertimes = 0;

    // 🔑 Declare a gameId field so it can be reused across dialogs/features
    private String gameId;
    private String gameType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basketball_scoreboard);

        // Force landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // 👇 Generate a unique gameId (timestamp-based or UUID also works)
        gameId = "game_" + System.currentTimeMillis();

        setTime = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("BASKETBALL_TIME", 12);
        setPeriod = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("BASKETBALL_PERIODS", 4);
        overtime = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("BASKETBALL_OVERTIME", 1);

        SharedPreferences.Editor editor = getSharedPreferences("currentGame", MODE_PRIVATE).edit();
        editor.putString("game_id", gameId).apply();

        timeLeftInMillis = setTime * 60000L;


        // Initialize views
        team1ScoreDisplay = findViewById(R.id.team1_score);
        team2ScoreDisplay = findViewById(R.id.team2_score);
        team1FoulDisplay = findViewById(R.id.team1_foul_display);
        team2FoulDisplay = findViewById(R.id.team2_foul_display);
        periodCounter = findViewById(R.id.period_counter);
        mainTimer = findViewById(R.id.main_timer);
        startTimerButton = findViewById(R.id.start_timer_button);
        minusteam1 = findViewById(R.id.minus_team1);
        minusteam2 = findViewById(R.id.minus_t2);
        shuffleButton = findViewById(R.id.shuffle_button);
        team1Label = findViewById(R.id.team1_name);
        team2Label = findViewById(R.id.team2_name);
        shotclockview = findViewById(R.id.shot_clock);

        // Initialize sounds
        initializeSounds();

        // Set initial values
        updateScoreDisplays();
        updatePeriodDisplay();
        updateTimerDisplay();

        // Setup button listeners
        setupTeam1Buttons();
        setupTeam2Buttons();
        setupTimerControls();
        setupSoundButtons();

        // Add Player Button
        Button addPlayerButton = findViewById(R.id.add_player_button);
        addPlayerButton.setOnClickListener(v -> {
            AddPlayerDialog dialog = new AddPlayerDialog(this);
            dialog.setGameId(gameId);
            dialog.setGameType("ADD PLAYER (BASKETBALL)");
            dialog.show(getSupportFragmentManager(), "AddPlayerDialog");
        });

        // Add Foul Button
        addFoulButton = findViewById(R.id.addFoulButton);
        addFoulButton.setOnClickListener(v -> {
            AddFoulDialog dialog = new AddFoulDialog(this, (team1Fouls, team2Fouls) -> {
                this.team1Fouls = team1Fouls;
                this.team2Fouls = team2Fouls;
                updateFoulDisplays();
            });
            dialog.show();
        });

        minusteam1.setOnClickListener(v -> {
            if(team1Score > 0){
                team1Score -= 1;
                updateScoreDisplays();
            }
        });

        minusteam2.setOnClickListener(v -> {
            if(team2Score > 0){
                team2Score -= 1;
                updateScoreDisplays();
            }
        });

        shuffleButton.setOnClickListener(v -> {
            String currentTeam1 = team1Label.getText().toString();
            String currentTeam2 = team2Label.getText().toString();
            String currentScore1 = Integer.toString(team1Score);
            String currentScore2 = Integer.toString(team2Score);
            String currentFoul1 = team1FoulDisplay.getText().toString();
            String currentFoul2 = team2FoulDisplay.getText().toString();

            if (!isShuffled) {
                // Swap teams (shuffle)
                team1Label.setText(currentTeam2);
                team2Label.setText(currentTeam1);
                team1ScoreDisplay.setText(currentScore2);
                team2ScoreDisplay.setText(currentScore1);
                team1FoulDisplay.setText(currentFoul2);
                team2FoulDisplay.setText(currentFoul1);
            } else {
                // Revert back to original
                team1Label.setText(currentTeam2); // (Or store original values)
                team2Label.setText(currentTeam1);
                team1ScoreDisplay.setText(currentScore1);
                team2ScoreDisplay.setText(currentScore2);
                team1FoulDisplay.setText(currentFoul2);
                team2FoulDisplay.setText(currentFoul1);
            }

            // Toggle the state
            isShuffled = !isShuffled;
        });
    }

    private void initializeSounds() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(audioAttributes)
                    .build();
        } else {
            soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        }

        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            soundsLoaded = true;
        });

        // Load your sound files (make sure these files exist in res/raw/)
        buzzerSoundId = soundPool.load(this, R.raw.buzzer_sound, 1);
        whistleSoundId = soundPool.load(this, R.raw.whistle_sound, 1);
    }

    private void setupSoundButtons() {
        ImageButton buzzerButton = findViewById(R.id.buzzer_button);
        ImageButton whistleButton = findViewById(R.id.whistle_button);

        buzzerButton.setOnClickListener(v -> {
            if (soundsLoaded) {
                soundPool.play(buzzerSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        });

        whistleButton.setOnClickListener(v -> {
            if (soundsLoaded) {
                soundPool.play(whistleSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
        });
    }

    private void playPeriodEndSound() {
        if (soundsLoaded) {
            soundPool.play(buzzerSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    private void setupTeam1Buttons() {
        findViewById(R.id.plus1_team1).setOnClickListener(v -> addPointsToTeam(1, 1));
        findViewById(R.id.plus2_team1).setOnClickListener(v -> addPointsToTeam(2, 1));
        findViewById(R.id.plus3_team1).setOnClickListener(v -> addPointsToTeam(3, 1));
    }

    private void setupTeam2Buttons() {
        findViewById(R.id.plus1_team2).setOnClickListener(v -> addPointsToTeam(1, 2));
        findViewById(R.id.plus2_team2).setOnClickListener(v -> addPointsToTeam(2, 2));
        findViewById(R.id.plus3_team2).setOnClickListener(v -> addPointsToTeam(3, 2));
    }

    private void setupTimerControls() {
        startTimerButton.setOnClickListener(v -> {
            if (timerRunning) {
                pauseTimer();
                pauseShotClock();
            } else {
                startTimer();
                resetShotClock(); // Reset shot clock when main timer starts
                startShotClock();
            }
        });
    }

    private void addPointsToTeam(int points, int teamNumber) {
        if (teamNumber == 1) {
            team1Score += points;
        } else {
            team2Score += points;
        }
        updateScoreDisplays();
    }

    private void updateScoreDisplays() {
        team1ScoreDisplay.setText(String.valueOf(team1Score));
        team2ScoreDisplay.setText(String.valueOf(team2Score));
    }

    private void updateFoulDisplays() {
        team1FoulDisplay.setText("Fouls: " + team1Fouls);
        team2FoulDisplay.setText("Fouls: " + team2Fouls);
    }

    private void updatePeriodDisplay() {
        periodCounter.setText(String.valueOf(period));
    }

    private void updateTimerDisplay() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeLeftFormatted = String.format("%02d:%02d", minutes, seconds);
        mainTimer.setText(timeLeftFormatted);
    }

    private void updateShotClockDisplay(){
        int seconds = (int) (shotclock/1000);
        String shotClockLeft = String.format("%02d", seconds);
        shotclockview.setText(shotClockLeft);
    }

    private void startShotClock() {
        shotclocktimer = new CountDownTimer(shotclock, INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
                shotclock = millisUntilFinished;
                updateShotClockDisplay();
            }

            @Override
            public void onFinish() {
                shotclock = 0;
                updateShotClockDisplay();
                // Handle what happens when shot clock expires
                shotClockExpired();
            }
        }.start();
    }

    private void resetShotClock() {
        if (shotclocktimer != null) {
            shotclocktimer.cancel();
        }
        shotclock = 24000; // Reset to 24 seconds
        updateShotClockDisplay();
    }

    // Call this when the game is paused
    private void pauseShotClock() {
        if (shotclocktimer != null) {
            shotclocktimer.cancel();
        }
    }

    // Handle what happens when shot clock expires
    private void shotClockExpired() {
        // Example: play a sound, show a message, etc.
        playPeriodEndSound();
    }

    private boolean isOvertime() {
        return period >= setPeriod && team1Score == team2Score;
    }

    private void saveGameHistory(boolean leftWon) {
        String leftName = team1Label.getText().toString().trim();
        String rightName = team2Label.getText().toString().trim();
        if (leftName.isEmpty()) leftName = "Team 1";
        if (rightName.isEmpty()) rightName = "Team 2";

        GameHistory history = new GameHistory();
        if (!isShuffled) {
            history.setTeam1Name(leftName);
            history.setTeam2Name(rightName);
            history.setTeam1Score(team1Score);
            history.setTeam2Score(team2Score);
            history.setWinner(leftWon ? leftName : rightName);
        } else {
            history.setTeam1Name(rightName);
            history.setTeam2Name(leftName);
            history.setTeam1Score(team2Score);
            history.setTeam2Score(team1Score);
            history.setWinner(leftWon ? rightName : leftName);
        }
        history.setSportType("Basketball");
        history.setDate(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));

        SharedPreferences prefs = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("GAME_HISTORY_LIST", "[]");
        Type type = new TypeToken<ArrayList<GameHistory>>(){}.getType();
        ArrayList<GameHistory> historyList = gson.fromJson(json, type);
        if (historyList == null) historyList = new ArrayList<>();
        historyList.add(history);
        prefs.edit().putString("GAME_HISTORY_LIST", gson.toJson(historyList)).apply();
    }


    private void startTimer() {
        timer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerDisplay();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                startTimerButton.setImageResource(R.drawable.play);
                playPeriodEndSound();

                if (isOvertime()) {
                    // Transition to overtime
                    isInOvertime = true;
                    currentOvertimes++;
                    timeLeftInMillis = overtime * 60000L; // 5 minutes

                    updatePeriodDisplay(); // Will show "OT1", "OT2", etc.
                    Toast.makeText(Basketball_Scoreboard.this,
                            "Overtime " + currentOvertimes + " started!",
                            Toast.LENGTH_LONG).show();

                    // Auto-start overtime timer
                    startTimer();
                    return;
                }
                else if (isInOvertime && team1Score == team2Score) {
                    // Additional overtime needed (score still tied after OT)
                    currentOvertimes++;
                    timeLeftInMillis = overtime * 60000;

                    updatePeriodDisplay();
                    Toast.makeText(Basketball_Scoreboard.this,
                            "Overtime " + currentOvertimes + " started!",
                            Toast.LENGTH_LONG).show();

                    startTimer();
                    return;
                }

                // Handle normal period progression
                if (period < setPeriod) {
                    period++;
                    timeLeftInMillis = setTime * 60000L; // Reset to 12 minutes
                    updatePeriodDisplay();

                    Toast.makeText(Basketball_Scoreboard.this,
                            "Period " + period + " ended!",
                            Toast.LENGTH_LONG).show();
                }
                // Game over condition
                else {
                    boolean team1Won = team1Score > team2Score;
                    saveGameHistory(team1Won);
                    String winnerName = (String) (team1Won ? team1Label.getText() : team1Label.getText());

                    Toast.makeText(Basketball_Scoreboard.this,
                            "Game Over! " + winnerName + " wins!",
                            Toast.LENGTH_LONG).show();

                    // Reset overtime state
                    isInOvertime = false;
                    currentOvertimes = 0;
                }
            }
        }.start();

        timerRunning = true;
        startTimerButton.setImageResource(R.drawable.pause);
    }

    private void pauseTimer() {
        if (timer != null) {
            timer.cancel();
        }
        timerRunning = false;
        startTimerButton.setImageResource(R.drawable.play);
    }

    public void updateFoulsDisplay() {
        FirebaseFirestore.getInstance().collection("fouls")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot latestFoul = queryDocumentSnapshots.getDocuments().get(0);
                        int team1Fouls = latestFoul.getLong("team1Fouls").intValue();
                        int team2Fouls = latestFoul.getLong("team2Fouls").intValue();

                        runOnUiThread(() -> {
                            team1FoulDisplay.setText("Fouls: " + team1Fouls);
                            team2FoulDisplay.setText("Fouls: " + team2Fouls);
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error getting fouls", e);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (timer != null) {
            timer.cancel();
        }
        if (shotclocktimer != null) {
            shotclocktimer.cancel();
        }
    }
}