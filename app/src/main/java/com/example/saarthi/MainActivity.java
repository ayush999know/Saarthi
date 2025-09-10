package com.example.saarthi;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private EditText nameEditText, ageEditText, peopleCountEditText;
    private Button startButton;
    private ProgressBar progressBar;
    private SignInButton googleSignInButton;
    private Spinner languageSpinner;
    private TextView orText;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private String currentLanguage = "English"; // default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI
        nameEditText = findViewById(R.id.nameEditText);
        ageEditText = findViewById(R.id.ageEditText);
        peopleCountEditText = findViewById(R.id.peopleCountEditText);
        startButton = findViewById(R.id.startButton);
        progressBar = findViewById(R.id.progressBar);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        languageSpinner = findViewById(R.id.languageSpinner);
        orText = findViewById(R.id.orText);

        // Language options
        String[] languages = {"English", "हिन्दी"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, languages);
        languageSpinner.setAdapter(adapter);

        // Set default text = English
        setEnglishTexts();

        // Handle language change
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    setEnglishTexts();
                    currentLanguage = "English";
                } else {
                    setHindiTexts();
                    currentLanguage = "Hindi";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Google Sign-In setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Launcher for Google Sign-In
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        if (task.isSuccessful()) {
                            GoogleSignInAccount account = task.getResult();
                            firebaseAuthWithGoogle(account);
                        } else {
                            Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Already logged in?
        checkCurrentUser();

        // Guest account flow
        startButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String ageStr = ageEditText.getText().toString().trim();
            String peopleStr = peopleCountEditText.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                nameEditText.setError(currentLanguage.equals("English") ?
                        "Enter your name" : "अपना नाम दर्ज करें");
                return;
            }
            if (TextUtils.isEmpty(ageStr)) {
                ageEditText.setError(currentLanguage.equals("English") ?
                        "Enter your age" : "अपनी उम्र दर्ज करें");
                return;
            }
            if (TextUtils.isEmpty(peopleStr)) {
                peopleCountEditText.setError(currentLanguage.equals("English") ?
                        "Enter number of people" : "लोगों की संख्या दर्ज करें");
                return;
            }

            createGuestAccount();
        });

        // Google sign-in flow
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());
    }

    private void setEnglishTexts() {
        nameEditText.setHint("Your Name");
        ageEditText.setHint("Your Age");
        peopleCountEditText.setHint("Number of People");
        startButton.setText("Start");
        orText.setText("OR");
    }

    private void setHindiTexts() {
        nameEditText.setHint("आपका नाम");
        ageEditText.setHint("आपकी उम्र");
        peopleCountEditText.setHint("लोगों की संख्या");
        startButton.setText("शुरू करें");
        orText.setText("या");
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        progressBar.setVisibility(View.VISIBLE);
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                goToHome();
            } else {
                Toast.makeText(this, "Authentication Failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void createGuestAccount() {
        progressBar.setVisibility(View.VISIBLE);
        startButton.setEnabled(false);

        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            progressBar.setVisibility(View.GONE);
            startButton.setEnabled(true);

            if (task.isSuccessful()) {
                goToHome();
            } else {
                Toast.makeText(MainActivity.this, "Guest Authentication failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToHome();
        }
    }

    private void goToHome() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkCurrentUser();
    }
}
