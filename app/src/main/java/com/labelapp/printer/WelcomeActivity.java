package com.labelapp.printer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {
    
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView statusIcon;
    private Button retryButton;
    private Button skipButton;
    
    private PrinterManager printerManager;
    
    // Welcome states
    enum WelcomeState {
        INITIALIZING,
        CHECKING_USB,
        REQUESTING_PERMISSION,
        SEARCHING_PRINTER,
        SUCCESS,
        ERROR_NO_DEVICE,
        ERROR_NO_PERMISSION,
        ERROR_NO_PRINTER
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        
        initializeViews();
        setupEventListeners();
        initializePrinterManager();
        
        // Start the printer detection flow automatically
        startPrinterDetection();
    }
    
    private void initializeViews() {
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        statusIcon = findViewById(R.id.statusIcon);
        retryButton = findViewById(R.id.retryButton);
        skipButton = findViewById(R.id.skipButton);
    }
    
    private void setupEventListeners() {
        retryButton.setOnClickListener(v -> {
            hideButtons();
            showProgress();
            startPrinterDetection();
        });
        
        skipButton.setOnClickListener(v -> {
            navigateToMainActivity();
        });
    }
    
    private void initializePrinterManager() {
        printerManager = PrinterManager.getInstance(this);
        printerManager.setActivityContext(this); // Set activity context for permission requests
        printerManager.setCallback(new PrinterManager.PrinterCallback() {
            @Override
            public void onStateChanged(PrinterManager.PrinterState state, String message) {
                runOnUiThread(() -> {
                    // Map PrinterManager.PrinterState to WelcomeState
                    WelcomeState welcomeState;
                    switch (state) {
                        case INITIALIZING:
                            welcomeState = WelcomeState.INITIALIZING;
                            break;
                        case REQUESTING_PERMISSION:
                            welcomeState = WelcomeState.REQUESTING_PERMISSION;
                            break;
                        case SEARCHING_PRINTER:
                            welcomeState = WelcomeState.SEARCHING_PRINTER;
                            break;
                        default:
                            welcomeState = WelcomeState.INITIALIZING;
                            break;
                    }
                    updateUIForState(welcomeState, message);
                });
            }
            
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    updateUIForState(WelcomeState.SUCCESS, "Принтерът е готов!");
                    // Navigate to main activity after short delay
                    new Handler().postDelayed(() -> {
                        navigateToMainActivity();
                    }, 2000);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (error.contains("намерен Brother принтер") || error.contains("USB връзката")) {
                        updateUIForState(WelcomeState.ERROR_NO_DEVICE, "Моля, свържете QL-800 принтера");
                    } else if (error.contains("разрешение") || error.contains("permission")) {
                        updateUIForState(WelcomeState.ERROR_NO_PERMISSION, "Достъпът до USB е необходим");
                    } else {
                        updateUIForState(WelcomeState.ERROR_NO_PRINTER, error);
                    }
                });
            }
        });
    }
    
    private void startPrinterDetection() {
        updateUIForState(WelcomeState.INITIALIZING, "Инициализиране...");
        printerManager.startDetection();
    }
    
    private void updateUIForState(Object state, String message) {
        statusText.setText(message);
        
        if (state == WelcomeState.SUCCESS) {
            showSuccess();
        } else if (state.toString().startsWith("ERROR")) {
            showError();
        } else {
            showProgress();
        }
    }
    
    private void showProgress() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        statusIcon.setVisibility(TextView.GONE);
        hideButtons();
    }
    
    private void showSuccess() {
        progressBar.setVisibility(ProgressBar.GONE);
        statusIcon.setVisibility(TextView.VISIBLE);
        statusIcon.setText("✓");
        statusIcon.setBackgroundResource(R.drawable.status_circle);
        hideButtons();
    }
    
    private void showError() {
        progressBar.setVisibility(ProgressBar.GONE);
        statusIcon.setVisibility(TextView.VISIBLE);
        statusIcon.setText("✗");
        statusIcon.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
        showButtons();
    }
    
    private void showButtons() {
        retryButton.setVisibility(Button.VISIBLE);
        skipButton.setVisibility(Button.VISIBLE);
    }
    
    private void hideButtons() {
        retryButton.setVisibility(Button.GONE);
        skipButton.setVisibility(Button.GONE);
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        // Pass the printer channel to MainActivity if available
        if (printerManager != null && printerManager.getPrinterChannel() != null) {
            intent.putExtra("PRINTER_READY", true);
        }
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Re-register the callback in case it was lost during permission dialog
        if (printerManager != null) {
            printerManager.setActivityContext(this);
            printerManager.setCallback(new PrinterManager.PrinterCallback() {
                @Override
                public void onStateChanged(PrinterManager.PrinterState state, String message) {
                    runOnUiThread(() -> {
                        // Map PrinterManager.PrinterState to WelcomeState
                        WelcomeState welcomeState;
                        switch (state) {
                            case INITIALIZING:
                                welcomeState = WelcomeState.INITIALIZING;
                                break;
                            case REQUESTING_PERMISSION:
                                welcomeState = WelcomeState.REQUESTING_PERMISSION;
                                break;
                            case SEARCHING_PRINTER:
                                welcomeState = WelcomeState.SEARCHING_PRINTER;
                                break;
                            default:
                                welcomeState = WelcomeState.INITIALIZING;
                                break;
                        }
                        updateUIForState(welcomeState, message);
                    });
                }
                
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        updateUIForState(WelcomeState.SUCCESS, "Принтерът е готов!");
                        // Navigate to main activity after short delay
                        new Handler().postDelayed(() -> {
                            navigateToMainActivity();
                        }, 2000);
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (error.contains("намерен Brother принтер") || error.contains("USB връзката")) {
                            updateUIForState(WelcomeState.ERROR_NO_DEVICE, "Моля, свържете QL-800 принтера");
                        } else if (error.contains("разрешение") || error.contains("permission")) {
                            updateUIForState(WelcomeState.ERROR_NO_PERMISSION, "Достъпът до USB е необходим");
                        } else {
                            updateUIForState(WelcomeState.ERROR_NO_PRINTER, error);
                        }
                    });
                }
            });
            
            // KEY FIX: Check if printer is already ready or restart detection
            if (printerManager.isPrinterReady()) {
                updateUIForState(WelcomeState.SUCCESS, "Принтерът е готов!");
                new Handler().postDelayed(() -> navigateToMainActivity(), 2000);
            } else {
                // Restart detection since the callback was missed
                printerManager.redetectPrinter();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't cleanup the singleton PrinterManager here - it's shared across activities
        // Only cleanup when the app is truly finishing
    }
}