package com.scoresync.scoresync2;


import static android.content.Context.MODE_PRIVATE;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddFoulDialog extends Dialog {


    private Context context;
    private int skibidiFoulCount = 0;
    private int sigmaFoulCount = 0;
    private String selectedSkibidiPlayer = "";
    private String selectedSigmaPlayer = "";
    private List<String> skibidiPlayers = new ArrayList<>();
    private List<String> sigmaPlayers = new ArrayList<>();
    private String team1Name = "Meliodas"; // Make these match your teams
    private String team2Name = "Zeldris";
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    CollectionReference playersRef = db.collection("players");

    List<String> skibidiPlayersArray = new ArrayList<>();
    private String gameId;

    public AddFoulDialog(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    private String playerDir;

    public interface FoulUpdateListener {
        void onFoulsUpdated(int team1Fouls, int team2Fouls);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_dialog_add_foul);

        // Initialize views
        TextView tvSkibidiFoulCount = findViewById(R.id.tvSkibidiFoulCount);
        TextView tvSigmaFoulCount = findViewById(R.id.tvSigmaFoulCount);
        Spinner spinnerSkibidi = findViewById(R.id.spinnerSkibidiPlayers);
        Spinner spinnerSigma = findViewById(R.id.spinnerSigmaPlayers);
        Button btnAddSkibidiFoul = findViewById(R.id.btnAddSkibidiFoul);
        Button btnAddSigmaFoul = findViewById(R.id.btnAddSigmaFoul);
        Button btnSave = findViewById(R.id.btnSaveFouls);
        Button btnClose = findViewById(R.id.btnCloseFouls);
        TextView team1Label = findViewById(R.id.team1_lbl);
        TextView team2Label = findViewById(R.id.team2_lbl);

        team1Name = context.getSharedPreferences("currentTeam", MODE_PRIVATE)
                .getString("team1name", "Team 1");

        team2Name = context.getSharedPreferences("currentTeam", MODE_PRIVATE)
                .getString("team2name", "Team 2");

        SharedPreferences prefs = context.getSharedPreferences("currentGame", MODE_PRIVATE);
        playerDir = prefs.getString("game", "players");
        Log.d("SharedPrefs", "Retrieved game path: " + playerDir);

        gameId = context.getSharedPreferences("currentGame", MODE_PRIVATE)
                .getString("game_id", "game_0");

        team1Label.setText(team1Name);
        team2Label.setText(team2Name);

        // Load players from Firestore
        loadPlayers(team1Name, skibidiPlayers, spinnerSkibidi, btnAddSkibidiFoul);
        loadPlayers(team2Name, sigmaPlayers, spinnerSigma, btnAddSigmaFoul);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                skibidiPlayers
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSkibidi.setAdapter(adapter);


        // Spinner selections
        spinnerSkibidi.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSkibidiPlayer = skibidiPlayers.get(position);
                btnAddSkibidiFoul.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnAddSkibidiFoul.setVisibility(View.GONE);
            }
        });

        spinnerSigma.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSigmaPlayer = sigmaPlayers.get(position);
                btnAddSigmaFoul.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnAddSigmaFoul.setVisibility(View.GONE);
            }
        });

        // Add foul buttons
        btnAddSkibidiFoul.setOnClickListener(v -> {
            skibidiFoulCount++;
            tvSkibidiFoulCount.setText(String.valueOf(skibidiFoulCount));
        });

        btnAddSigmaFoul.setOnClickListener(v -> {
            sigmaFoulCount++;
            tvSigmaFoulCount.setText(String.valueOf(sigmaFoulCount));
        });

        // Save/Close buttons
        btnSave.setOnClickListener(v -> {
            saveFoulsToFirestore();
            // Update the main scoreboard if needed
            if (context instanceof Basketball_Scoreboard) {
                ((Basketball_Scoreboard) context).updateFoulsDisplay();
            }
        });

        btnClose.setOnClickListener(v -> dismiss());
    }

    private void loadPlayers(String teamName, List<String> playersList, Spinner spinner, Button addButton) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Log.d("Firestore", playerDir);
        db.collection(playerDir + "/players/")
                .whereEqualTo("team", teamName)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        playersList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String playerName = document.getString("name");
                            if (playerName != null) {
                                playersList.add(playerName);
                            }
                        }

                        if (playersList.isEmpty()) {
                            playersList.add("No players available");
                            addButton.setVisibility(View.GONE);
                        }

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                context, android.R.layout.simple_spinner_item, playersList);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinner.setAdapter(adapter);
                    } else {
                        Toast.makeText(context, "Error loading players", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveFoulsToFirestore() {
        // Validate player selections
        if (selectedSkibidiPlayer.isEmpty() || selectedSigmaPlayer.isEmpty() ||
                selectedSkibidiPlayer.equals("No players available") ||
                selectedSigmaPlayer.equals("No players available")) {
            Toast.makeText(context, "Please select valid players from both teams", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare foul data
        Map<String, Object> foulData = new HashMap<>();
        foulData.put("gameId", gameId);
        foulData.put("team1", team1Name);
        foulData.put("team1Player", selectedSkibidiPlayer);
        foulData.put("team1Fouls", skibidiFoulCount);
        foulData.put("team2", team2Name);
        foulData.put("team2Player", selectedSigmaPlayer);
        foulData.put("team2Fouls", sigmaFoulCount);
        foulData.put("timestamp", System.currentTimeMillis());
        foulData.put("sportType", "Basketball");

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // First check if a document with this gameId already exists
        db.collection("fouls")
                .whereEqualTo("gameId", gameId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            // Document exists - update it
                            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                            document.getReference().update(foulData)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(context, "Fouls updated successfully", Toast.LENGTH_SHORT).show();
                                        if (listener != null) {
                                            listener.onFoulsUpdated(skibidiFoulCount, sigmaFoulCount);
                                        }
                                        dismiss();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(context, "Error updating fouls: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            // Document doesn't exist - create new one
                            db.collection("fouls")
                                    .add(foulData)
                                    .addOnSuccessListener(documentReference -> {
                                        Toast.makeText(context, "Fouls saved successfully", Toast.LENGTH_SHORT).show();
                                        if (listener != null) {
                                            listener.onFoulsUpdated(skibidiFoulCount, sigmaFoulCount);
                                        }
                                        dismiss();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(context, "Error saving fouls: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        Toast.makeText(context, "Error checking fouls: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }



    public static void show(Context context) {
        AddFoulDialog dialog = new AddFoulDialog(context);
        dialog.show();
    }

    // Modify the constructor
    private FoulUpdateListener listener;

    public AddFoulDialog(@NonNull Context context, FoulUpdateListener listener) {
        super(context);
        this.context = context;
        this.listener = listener;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
}