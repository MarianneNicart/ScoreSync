package com.scoresync.scoresync2;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.scoresync.scoresync2.model.GameHistory;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class volleyball_scoreboard extends AppCompatActivity {

    // UI Elements
    private TextView team1ScoreDisplay, team2ScoreDisplay, t1set, t2set;
    private TextView roundCounter, team1Label, team2Label, bestOfCounterv;
    private Button minusteam1, minusteam2, plusteam1, plusteam2;
    private ImageButton shuffleButton;

    // Score and round tracking
    private int team1Score = 0;
    private int team2Score = 0;
    private int team1Sets = 0;
    private int team2Sets = 0;
    private int round = 1;
    private int totalSets = 0;
    private int setsToWin = 2;
    private int volleyballPointsPerSet = 21;
    private boolean isSwapped = false;
    private String gameId;
    private int tiebreaker;

    private int team1Fouls = 0;
    private int team2Fouls = 0;
    private Button addPlayerBtn;
    private Button playerActionBtn;
    private TextView team1FoulDisplay;
    private TextView team2FoulDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_volleyball_scoreboard);

        // Force landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Link UI elements
        team1ScoreDisplay = findViewById(R.id.team1_score);
        team2ScoreDisplay = findViewById(R.id.team2_score);
        roundCounter = findViewById(R.id.round_counter);
        minusteam1 = findViewById(R.id.minus_team1v);
        minusteam2 = findViewById(R.id.minus_team2v);
        plusteam1 = findViewById(R.id.plus_team1);
        plusteam2 = findViewById(R.id.plus_team2);
        shuffleButton = findViewById(R.id.shuffle_button);
        team1Label = findViewById(R.id.team1_name);
        team2Label = findViewById(R.id.team2_name);
        bestOfCounterv = findViewById(R.id.bestOfCounterV);
        t1set = findViewById(R.id.team1_sets);
        t2set = findViewById(R.id.team2_sets);
        team1FoulDisplay = findViewById(R.id.team1Foul);
        team2FoulDisplay = findViewById(R.id.team2Foul);
        gameId = "game_" + System.currentTimeMillis();

        totalSets = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("VOLLEYBALL_TOTAL_SETS", 3);
        setsToWin = (totalSets / 2) + 1;
        bestOfCounterv.setText(String.valueOf(totalSets));

        addPlayerBtn = findViewById(R.id.addPlayersButton);
        playerActionBtn = findViewById(R.id.playersActionButton);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        volleyballPointsPerSet = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("VOLLEYBALL_POINTS_PER_SET", 21);

        tiebreaker = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("VOLLEYBALL_TIE_BREAKER", 2);

        // Initial UI update
        updateScores();
        updateRound();

        // Button listeners
        plusteam1.setOnClickListener(v -> {
            team1Score++;
            checkSetWin();
            updateScores();
            if (team1Sets == setsToWin) {
                Toast.makeText(this, getCurrentWinnerName(true) + " wins the match!", Toast.LENGTH_LONG).show();
                resetScoreboard();
            }

        });

        minusteam1.setOnClickListener(v -> {
            if (team1Score > 0) team1Score--;
            updateScores();
        });

        plusteam2.setOnClickListener(v -> {
            team2Score++;
            checkSetWin();
            updateScores();
            if (team2Sets == setsToWin) {
                Toast.makeText(this, getCurrentWinnerName(true) + " wins the match!", Toast.LENGTH_LONG).show();
                resetScoreboard();
            }
        });

        minusteam2.setOnClickListener(v -> {
            if (team2Score > 0) team2Score--;
            updateScores();
        });

        shuffleButton.setOnClickListener(v -> switchSides());

        addPlayerBtn.setOnClickListener(view -> {
            AddPlayerDialog dialog = new AddPlayerDialog(this);
            dialog.setGameId(gameId);
            dialog.setGameType("ADD PLAYER (VOLLEYBALL)");
            dialog.show(getSupportFragmentManager(), "AddPlayerDialog");
        });

        playerActionBtn.setOnClickListener(view -> {
            AddFoulDialog dialog = new AddFoulDialog(this, (team1Fouls, team2Fouls) -> {
                this.team1Fouls = team1Fouls;
                this.team2Fouls = team2Fouls;
                updateFoulsDisplay();
            });
            dialog.show();
        });
    }

    private void checkSetWin() {
        if (isSetWon(team1Score, team2Score)) {
            team1Sets++;
            t1set.setText(String.format("%s", team1Sets));
            saveGameHistory(true);
            resetForNextSet();
        } else if (isSetWon(team2Score, team1Score)) {
            team2Sets++;
            t2set.setText(String.format("%s", team2Sets));
            saveGameHistory(false);
            resetForNextSet();
        }
    }

    private void resetForNextSet() {
        team1Score = 0;
        team2Score = 0;
        round++;
        updateRound();
    }

    private boolean isSetWon(int thisTeamPoints, int otherTeamPoints) {
        // Win by 2, unless cap is reached
        if (thisTeamPoints >= volleyballPointsPerSet && (thisTeamPoints - otherTeamPoints) >= 2) {
            return true;
        }
        if (thisTeamPoints == (volleyballPointsPerSet + tiebreaker)) { // Typically volleyball sets cap at 2 points above the normal set point (e.g., 25 for normal 21)
            return true;
        }
        return false;
    }

    private void updateRound() {
        int currentRound = team1Sets + team2Sets + 1;
        roundCounter.setText(String.valueOf(currentRound));
    }

    private void resetScoreboard() {
        team1Score = 0;
        team2Score = 0;
        team1Sets = 0;
        team2Sets = 0;
        team1ScoreDisplay.setText("0");
        team2ScoreDisplay.setText("0");
        t1set.setText("0");
        t1set.setText("0");
        updateRound();
    }

    private String getCurrentWinnerName(boolean leftWon) {
        String leftName = team1Label.getText().toString().trim();
        String rightName = team2Label.getText().toString().trim();
        if (leftName.isEmpty()) leftName = "Team 1";
        if (rightName.isEmpty()) rightName = "Team 2";
        if (!isSwapped) {
            return leftWon ? leftName : rightName;
        } else {
            return leftWon ? rightName : leftName;
        }
    }

    private void saveGameHistory(boolean leftWon) {
        // Only save history when the match is complete
        if (team1Sets < setsToWin && team2Sets < setsToWin) {
            return;
        }

        String leftName = team1Label.getText().toString().trim();
        String rightName = team2Label.getText().toString().trim();
        if (leftName.isEmpty()) leftName = "Team 1";
        if (rightName.isEmpty()) rightName = "Team 2";

        GameHistory history = new GameHistory();
        if (!isSwapped) {
            history.setTeam1Name(leftName);
            history.setTeam2Name(rightName);
            history.setTeam1Score(team1Sets);  // Only saving final sets
            history.setTeam2Score(team2Sets);  // Only saving final sets
            history.setTeam1Sets(team1Sets);
            history.setTeam2Sets(team2Sets);
            history.setWinner(leftWon ? leftName : rightName);
        } else {
            history.setTeam1Name(rightName);
            history.setTeam2Name(leftName);
            history.setTeam1Score(team2Sets);  // Only saving final sets
            history.setTeam2Score(team1Sets);  // Only saving final sets
            history.setTeam1Sets(team2Sets);
            history.setTeam2Sets(team1Sets);
            history.setWinner(leftWon ? rightName : leftName);
        }
        history.setSportType("Volleyball");
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
    // Update score display
    private void updateScores() {
        team1ScoreDisplay.setText(String.valueOf(team1Score));
        team2ScoreDisplay.setText(String.valueOf(team2Score));
    }

    // Swap team sides and scores visually (not logically)
    private void switchSides() {
        // Swap scores visually
        int tempScore = team1Score;
        team1Score = team2Score;
        team2Score = tempScore;

        // Swap labels visually
        CharSequence tempLabel = team1Label.getText();
        team1Label.setText(team2Label.getText());
        team2Label.setText(tempLabel);

        int tempFoul = team1Fouls;
        team1Fouls = team2Fouls;
        team2Fouls = tempFoul;

        // Update swapped state
        isSwapped = !isSwapped;

        updateScores();
        updateFoulsDisplay();
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
        // Reset to portrait when exiting the activity
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}