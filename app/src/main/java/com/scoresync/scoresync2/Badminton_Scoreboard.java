package com.scoresync.scoresync2;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
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
import com.scoresync.scoresync2.model.GameHistory;

import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.lang.reflect.Type;

public class Badminton_Scoreboard extends AppCompatActivity {

    private int badmintonPointsPerSet = 21; //default score and set for badminton
    private int badmintonPointCap = 30;
    private int totalSets = 3;
    private int setsToWin = 2;
    private int team1Points = 0, team2Points = 0;
    private int team1Sets = 0, team2Sets = 0;
    private int team1Fouls = 0, team2Fouls = 0;
    private TextView roundCounter;
    private TextView bestOfCounter;
    private boolean isSwapped = false;
    private String gameId;
    private TextView team1FoulDisplay;
    private TextView team2FoulDisplay;
    private Button addPlayer;
    private Button playerAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_badminton_scoreboard);

        gameId = "game_" + System.currentTimeMillis();
        roundCounter = findViewById(R.id.round_counter);
        bestOfCounter = findViewById(R.id.best_of_counter);

        totalSets = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("BADMINTON_TOTAL_SETS", 3);
        setsToWin = (totalSets / 2) + 1;
        bestOfCounter.setText(String.valueOf(totalSets));

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        badmintonPointsPerSet = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE)
                .getInt("BADMINTON_POINTS_PER_SET", 21);

        TextView team1Score = findViewById(R.id.team1_score);
        TextView team2Score = findViewById(R.id.team2_score);
        TextView team1SetsView = findViewById(R.id.team1_sets);
        TextView team2SetsView = findViewById(R.id.team2_sets);
        team1FoulDisplay = findViewById(R.id.team1FoulCount);
        team2FoulDisplay = findViewById(R.id.team2FoulCount);
        addPlayer = findViewById(R.id.addPlayersButton);
        playerAction = findViewById(R.id.playersActionButton);

        Button plusTeam1 = findViewById(R.id.plus_team1);
        Button plusTeam2 = findViewById(R.id.plus_team2);

        Button minusTeam1 = findViewById(R.id.minus_team1);
        Button minusTeam2 = findViewById(R.id.minus_team2);

        // Swap button logic
        ImageButton swapButton = findViewById(R.id.swap_button);
        swapButton.setOnClickListener(v -> {
            TextView team1NameView = findViewById(R.id.team1_name);
            TextView team2NameView = findViewById(R.id.team2_name);
            CharSequence tempName = team1NameView.getText();
            team1NameView.setText(team2NameView.getText());
            team2NameView.setText(tempName);

            CharSequence tempScore = team1Score.getText();
            team1Score.setText(team2Score.getText());
            team2Score.setText(tempScore);

            CharSequence tempSets = team1SetsView.getText();
            team1SetsView.setText(team2SetsView.getText());
            team2SetsView.setText(tempSets);

            int tempPoints = team1Points;
            team1Points = team2Points;
            team2Points = tempPoints;

            int tempSetsInt = team1Sets;
            team1Sets = team2Sets;
            team2Sets = tempSetsInt;

            int tempFoulIInt = team1Fouls;
            team1Fouls = team2Fouls;
            team2Fouls = tempFoulIInt;

            isSwapped = !isSwapped;
        });

        plusTeam1.setOnClickListener(v -> {
            team1Points++;
            team1Score.setText(String.valueOf(team1Points));
            if (isSetWon(team1Points, team2Points)) {
                team1Sets++;
                team1SetsView.setText(String.valueOf(team1Sets));
                updateRoundCounter();
                team1Points = 0;
                team2Points = 0;
                team1Score.setText("0");
                team2Score.setText("0");
                if (team1Sets == setsToWin) {
                    saveGameHistory(true);
                    Toast.makeText(this, getCurrentWinnerName(true) + " wins the match!", Toast.LENGTH_LONG).show();
                    resetScoreboard(team1Score, team2Score, team1SetsView, team2SetsView);
                }
            }
        });

        if (minusTeam1 != null) {
            minusTeam1.setOnClickListener(v -> {
                if (team1Points > 0) {
                    team1Points--;
                    team1Score.setText(String.valueOf(team1Points));
                }
            });
        }

        plusTeam2.setOnClickListener(v -> {
            team2Points++;
            team2Score.setText(String.valueOf(team2Points));
            if (isSetWon(team2Points, team1Points)) {
                team2Sets++;
                team2SetsView.setText(String.valueOf(team2Sets));
                updateRoundCounter();
                team1Points = 0;
                team2Points = 0;
                team1Score.setText("0");
                team2Score.setText("0");
                if (team2Sets == setsToWin) {
                    saveGameHistory(false);
                    Toast.makeText(this, getCurrentWinnerName(false) + " wins the match!", Toast.LENGTH_LONG).show();
                    resetScoreboard(team1Score, team2Score, team1SetsView, team2SetsView);
                }
            }
        });

        if (minusTeam2 != null) {
            minusTeam2.setOnClickListener(v -> {
                if (team2Points > 0) {
                    team2Points--;
                    team2Score.setText(String.valueOf(team2Points));
                }
            });
        }

        addPlayer.setOnClickListener(view -> {
            AddPlayerDialog dialog = new AddPlayerDialog(this);
            dialog.setGameId(gameId);
            dialog.setGameType("ADD PLAYER (BADMINTON)");
            dialog.show(getSupportFragmentManager(), "AddPlayerDialog");
        });

        playerAction.setOnClickListener(view -> {
            AddFoulDialog dialog = new AddFoulDialog(this, (team1Fouls, team2Fouls) -> {
                this.team1Fouls = team1Fouls;
                this.team2Fouls = team2Fouls;
                updateFoulsDisplay();
            });
            dialog.show();
        });
    }

    private boolean isSetWon(int thisTeamPoints, int otherTeamPoints) {
        // Win by 2, unless cap is reached
        if (thisTeamPoints >= badmintonPointsPerSet && (thisTeamPoints - otherTeamPoints) >= 2) {
            return true;
        }
        if (thisTeamPoints == badmintonPointCap) {
            return true;
        }
        return false;
    }

    private void updateRoundCounter() {
        int round = team1Sets + team2Sets + 1;
        roundCounter.setText(String.valueOf(round));
    }

    private void resetScoreboard(TextView team1Score, TextView team2Score, TextView team1SetsView, TextView team2SetsView) {
        team1Points = 0;
        team2Points = 0;
        team1Sets = 0;
        team2Sets = 0;
        team1Score.setText("0");
        team2Score.setText("0");
        team1SetsView.setText("0");
        team2SetsView.setText("0");
        updateRoundCounter();
    }

    private String getCurrentWinnerName(boolean leftWon) {
        TextView team1NameView = findViewById(R.id.team1_name);
        TextView team2NameView = findViewById(R.id.team2_name);
        String leftName = team1NameView.getText().toString().trim();
        String rightName = team2NameView.getText().toString().trim();
        if (leftName.isEmpty()) leftName = "Team 1";
        if (rightName.isEmpty()) rightName = "Team 2";
        if (!isSwapped) {
            return leftWon ? leftName : rightName;
        } else {
            return leftWon ? rightName : leftName;
        }
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

    private void saveGameHistory(boolean leftWon) {
        TextView team1NameView = findViewById(R.id.team1_name);
        TextView team2NameView = findViewById(R.id.team2_name);
        String leftName = team1NameView.getText().toString().trim();
        String rightName = team2NameView.getText().toString().trim();
        if (leftName.isEmpty()) leftName = "Team 1";
        if (rightName.isEmpty()) rightName = "Team 2";

        GameHistory history = new GameHistory();
        if (!isSwapped) {
            history.setTeam1Name(leftName);
            history.setTeam2Name(rightName);
            history.setTeam1Score(team1Sets);
            history.setTeam2Score(team2Sets);
            history.setTeam1Sets(team1Sets);
            history.setTeam2Sets(team2Sets);
            history.setWinner(leftWon ? leftName : rightName);
        } else {
            history.setTeam1Name(rightName);
            history.setTeam2Name(leftName);
            history.setTeam1Score(team2Sets);
            history.setTeam2Score(team1Sets);
            history.setTeam1Sets(team2Sets);
            history.setTeam2Sets(team1Sets);
            history.setWinner(leftWon ? rightName : leftName);
        }
        history.setSportType("Badminton");
        history.setDate(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));

        SharedPreferences prefs = getSharedPreferences("ScoreSyncPrefs", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = prefs.getString("GAME_HISTORY_LIST", "[]");
        Type type = new com.google.gson.reflect.TypeToken<ArrayList<GameHistory>>(){}.getType();
        ArrayList<GameHistory> historyList = gson.fromJson(json, type);
        if (historyList == null) historyList = new ArrayList<>();
        historyList.add(history);
        prefs.edit().putString("GAME_HISTORY_LIST", gson.toJson(historyList)).apply();
    }
}