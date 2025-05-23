package com.scoresync.scoresync2;

import static android.content.Context.MODE_PRIVATE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.scoresync.scoresync2.model.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AddPlayerDialog extends DialogFragment {

    private EditText team1EditText, team2EditText, coach1EditText, coach2EditText, playerNameEditText;
    private Spinner teamSpinner;
    private TableLayout playersTable;
    private final ArrayList<Player> players = new ArrayList<>();
    private String gameId = "";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private int num = 1;
    private Context context;
    private String gameType;
    private TextView addLabel;

    public AddPlayerDialog(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.activity_add_player_dialog, null);

        // Initialize views
        team1EditText = view.findViewById(R.id.team1EditText);
        team2EditText = view.findViewById(R.id.team2EditText);
        coach1EditText = view.findViewById(R.id.coach1EditText);
        coach2EditText = view.findViewById(R.id.coach2EditText);
        playerNameEditText = view.findViewById(R.id.playerNameEditText);
        teamSpinner = view.findViewById(R.id.teamSpinner);
        playersTable = view.findViewById(R.id.playerTable);
        addLabel = view.findViewById(R.id.apTitle);

        addLabel.setText(gameType);

        // Set up buttons
        Button updateTeamsButton = view.findViewById(R.id.updateTeamsButton);
        updateTeamsButton.setOnClickListener(v -> setupTeamSpinner());

        Button addPlayerButton = view.findViewById(R.id.addPlayerButton);
        addPlayerButton.setOnClickListener(v -> addPlayer());

        builder.setView(view)
                .setTitle("Add Players")
                .setPositiveButton("Done", (dialog, which) -> saveToFirestore())
                .setNegativeButton("Cancel", (dialog, which) -> dismiss());

        setupTeamSpinner();

        // Create the dialog
        AlertDialog dialog = builder.create();

        // Prevent accidental dismissal
        dialog.setCanceledOnTouchOutside(false);

        // Adjust window parameters if needed
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        return dialog;
    }

    private void setupTeamSpinner() {
        String newTeam1 = team1EditText.getText().toString().isEmpty() ? "Team 1" : team1EditText.getText().toString();
        String newTeam2 = team2EditText.getText().toString().isEmpty() ? "Team 2" : team2EditText.getText().toString();

        // Save all to SharedPreferences
        SharedPreferences.Editor editor = requireContext().getSharedPreferences("currentTeam", MODE_PRIVATE).edit();
        editor.putString("team1name", newTeam1).apply();
        editor.putString("team2name", newTeam2).apply();

        ArrayList<String> teamOptions = new ArrayList<>();
        teamOptions.add(newTeam1);
        teamOptions.add(newTeam2);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                teamOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        teamSpinner.setAdapter(spinnerAdapter);

        // Update existing players' team names
        for (int i = 0; i < playersTable.getChildCount(); i++) {
            View rowView = playersTable.getChildAt(i);
            if (rowView instanceof TableRow) {
                TableRow row = (TableRow) rowView;
                if (row.getChildCount() >= 3) {
                    TextView teamText = (TextView) row.getChildAt(1);
                    // Only update if we have a corresponding player in the ArrayList
                    if (i < players.size()) {
                        Player player = players.get(i);
                        if (teamText.getText().toString().equals("Team 1")) {
                            teamText.setText(newTeam1);
                            player.setTeam(newTeam1);
                        } else if (teamText.getText().toString().equals("Team 2")) {
                            teamText.setText(newTeam2);
                            player.setTeam(newTeam2);
                        }
                    }
                }
            }
        }
    }
    private void addPlayer() {
        String playerName = playerNameEditText.getText().toString().trim();
        String selectedTeam = teamSpinner.getSelectedItem() != null ?
                teamSpinner.getSelectedItem().toString() : "";

        if (playerName.isEmpty() || selectedTeam.isEmpty()) {
            return;
        }

        // Get the coach name based on selected team
        String coachName = "";
        if (selectedTeam.equals(team1EditText.getText().toString()) ||
                (team1EditText.getText().toString().isEmpty() && selectedTeam.equals("Team 1"))) {
            coachName = coach1EditText.getText().toString();
        } else {
            coachName = coach2EditText.getText().toString();
        }

        // Create player with coach information
        Player player = new Player(playerName, selectedTeam, coachName, 0);
        players.add(player);

        TableRow row = new TableRow(requireContext());

        // Number
        TextView numText = new TextView(requireContext());
        numText.setText(String.valueOf(num));
        numText.setPadding(16, 16, 16, 16);

        // Player Name
        TextView nameText = new TextView(requireContext());
        nameText.setText(player.getName());
        nameText.setPadding(16, 16, 16, 16);

        // Team Name
        TextView teamText = new TextView(requireContext());
        teamText.setText(player.getTeam());
        teamText.setPadding(16, 16, 16, 16);

        // Coach Name (NEW)
        TextView coachText = new TextView(requireContext());
        coachText.setText(player.getCoach());
        coachText.setPadding(16, 16, 16, 16);

        // Foul Count
        TextView foulText = new TextView(requireContext());
        foulText.setText(String.valueOf(player.getFouls()));
        foulText.setPadding(16, 16, 16, 16);
        foulText.setInputType(InputType.TYPE_NULL);

        num += 1;
        // Add all views to the row (including coachText)
        row.addView(numText);
        row.addView(nameText);
        row.addView(teamText);
        row.addView(coachText);  // NEW
        row.addView(foulText);

        playersTable.addView(row);
        playerNameEditText.setText("");
    }

    private void saveToFirestore() {
        if (gameId == null || gameId.isEmpty()) {
            return;
        }

        String team1 = team1EditText.getText().toString().isEmpty() ?
                "Team 1" : team1EditText.getText().toString();
        String team2 = team2EditText.getText().toString().isEmpty() ?
                "Team 2" : team2EditText.getText().toString();
        String coach1 = coach1EditText.getText().toString();
        String coach2 = coach2EditText.getText().toString();

        // Save game data
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("team1", team1);
        gameData.put("team2", team2);
        gameData.put("coach1", coach1);
        gameData.put("coach2", coach2);

        // Get reference to the game document
        DocumentReference gameRef = db.collection("games").document(gameId);

        gameRef.set(gameData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Firestore", "Game saved at path: " + gameRef.getPath());

                    // Save players after game data is saved
                    for (Player player : players) {
                        Map<String, Object> playerData = new HashMap<>();
                        playerData.put("name", player.getName());
                        playerData.put("team", player.getTeam());
                        playerData.put("coach", player.getCoach());
                        playerData.put("fouls", player.getFouls());

                        // Add player to subcollection and get its path
                        gameRef.collection("players")
                                .add(playerData)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d("Firestore", "Player saved at path: " + documentReference.getPath());

                                    // Store full path in SharedPreferences
                                    SharedPreferences.Editor editor = context.getSharedPreferences("currentGame", MODE_PRIVATE).edit();
                                    editor.putString("game", gameRef.getPath());
                                    editor.apply();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Firestore", "Error saving player", e);
                                });

                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error saving game", e);
                });
    }
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }
}