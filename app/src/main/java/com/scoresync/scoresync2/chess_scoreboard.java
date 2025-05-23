package com.scoresync.scoresync2;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.Locale;

public class chess_scoreboard extends AppCompatActivity {

    private TextView player1Clock, player2Clock;
    private LinearLayout player1Tap, player2Tap;
    private ImageButton resetButton, pauseButton;

    private long player1TimeLeft = 600000; // 10 minutes in milliseconds
    private long player2TimeLeft = 600000; // 10 minutes in milliseconds
    private long timeIncrement = 0; // Time increment per move in milliseconds

    private Handler handler = new Handler();
    private Runnable clockRunnable;
    private boolean isRunning = false;
    private boolean isPlayer1Turn = true;
    private boolean isPaused = false;
    private int setTime = 1;
    private String gameId;
    private Button addPlayerBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chess_scoreboard);

        gameId = "game_" + System.currentTimeMillis();
        setTime = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("CHESS_MINUTES_PER_MATCH", 10);

        player1TimeLeft = setTime * 60000L;
        player2TimeLeft = setTime * 60000L;

        // Initialize views
        player1Clock = findViewById(R.id.tv_chess_timer);
        player2Clock = findViewById(R.id.tv_chess_timer2);
        player1Tap = findViewById(R.id.tapright);
        player2Tap = findViewById(R.id.tapleft);
        resetButton = findViewById(R.id.btn_restart);
        pauseButton = findViewById(R.id.btn_play_pause);
        addPlayerBtn = findViewById(R.id.btn_add_players);

        // Set initial clock values
        updateClockText();

        // Player 1 tap area
        player1Tap.setOnClickListener(v -> {
            if (isRunning && isPlayer1Turn) {
                switchTurn();
            }
        });

        // Player 2 tap area
        player2Tap.setOnClickListener(v -> {
            if (isRunning && !isPlayer1Turn) {
                switchTurn();
            }
        });

        // Reset button
        resetButton.setOnClickListener(v -> resetClock());

        // Pause/Start button
        pauseButton.setOnClickListener(v -> {
            if (isPaused) {
                startClock();
            } else {
                pauseClock();
            }
        });

        addPlayerBtn.setOnClickListener(view -> {
            AddPlayerDialog dialog = new AddPlayerDialog(this);
            dialog.setGameId(gameId);
            dialog.setGameType("ADD PLAYER (CHESS)");
            dialog.show(getSupportFragmentManager(), "AddPlayerDialog");
        });

        // Initialize the clock runnable
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && !isPaused) {
                    if (isPlayer1Turn) {
                        player1TimeLeft -= 1000;
                        if (player1TimeLeft <= 0) {
                            player1TimeLeft = 0;
                            gameOver(false); // Player 1 loses on time
                        }
                    } else {
                        player2TimeLeft -= 1000;
                        if (player2TimeLeft <= 0) {
                            player2TimeLeft = 0;
                            gameOver(true); // Player 2 loses on time
                        }
                    }
                    updateClockText();
                }
                handler.postDelayed(this, 1000);
            }
        };

        // Start the clock
        startClock();
    }

    private void startClock() {
        if (!isRunning) {
            isRunning = true;
            handler.post(clockRunnable);
        }
        isPaused = false;
    }

    private void pauseClock() {
        isPaused = true;
    }

    private void stopClock() {
        isRunning = false;
        handler.removeCallbacks(clockRunnable);
    }

    private void resetClock() {
        stopClock();
        player1TimeLeft = setTime * 60000L; // Reset to 10 minutes
        player2TimeLeft = setTime * 60000L; // Reset to 10 minutes
        isPlayer1Turn = true;
        isPaused = false;
        updateClockText();
        // Highlight active player
        highlightActivePlayer();
    }

    private void switchTurn() {
        if (isPlayer1Turn) {
            player1TimeLeft += timeIncrement; // Add time increment
            isPlayer1Turn = false;
        } else {
            player2TimeLeft += timeIncrement; // Add time increment
            isPlayer1Turn = true;
        }
        updateClockText();
        highlightActivePlayer();
    }

    private void updateClockText() {
        // Format time as MM:SS
        String player1Text = String.format(Locale.getDefault(),
                "%02d:%02d",
                (player1TimeLeft / 60000) % 60,
                (player1TimeLeft / 1000) % 60);

        String player2Text = String.format(Locale.getDefault(),
                "%02d:%02d",
                (player2TimeLeft / 60000) % 60,
                (player2TimeLeft / 1000) % 60);

        player1Clock.setText(player1Text);
        player2Clock.setText(player2Text);
    }

    private void highlightActivePlayer() {
        if (isPlayer1Turn) {
            player1Clock.setBackgroundColor(getResources().getColor(R.color.active_player));
            player2Clock.setBackgroundColor(getResources().getColor(R.color.inactive_player));
        } else {
            player1Clock.setBackgroundColor(getResources().getColor(R.color.inactive_player));
            player2Clock.setBackgroundColor(getResources().getColor(R.color.active_player));
        }
    }

    private void gameOver(boolean player1Wins) {
        stopClock();
        String winner = player1Wins ? "Player 1" : "Player 2";
        // Show game over message or perform other end-game actions
        // You can add a Toast or dialog here
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isPaused && isRunning) {
            startClock();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopClock();
        handler.removeCallbacksAndMessages(null);
    }
}