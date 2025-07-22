package com.labelapp.printer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.core.content.ContextCompat;

import com.brother.sdk.lmprinter.Channel;
import com.brother.sdk.lmprinter.PrinterSearchResult;
import com.brother.sdk.lmprinter.PrinterSearcher;

import java.util.HashMap;

public class PrinterManager {
    
    private static final String ACTION_USB_PERMISSION = "com.labelapp.printer.USB_PERMISSION";
    
    // Singleton instance
    private static PrinterManager instance;
    
    private Context context;
    private UsbManager usbManager;
    private PrinterCallback callback;
    private Channel printerChannel = null;
    
    // Simple state tracking
    private Context activityContext;
    
    // USB permission retry mechanism
    private int permissionRetryCount = 0;
    private static final int MAX_PERMISSION_RETRIES = 3;
    private android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    public enum PrinterState {
        INITIALIZING,
        SEARCHING_PRINTER,
        REQUESTING_PERMISSION,
        SUCCESS,
        ERROR
    }
    
    public interface PrinterCallback {
        void onStateChanged(PrinterState state, String message);
        void onSuccess();
        void onError(String error);
    }
    
    // Singleton getInstance method
    public static PrinterManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrinterManager(context.getApplicationContext());
        }
        return instance;
    }
    
    private PrinterManager(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
    }
    
    public void setCallback(PrinterCallback callback) {
        this.callback = callback;
    }
    
    public void setActivityContext(Context activityContext) {
        this.activityContext = activityContext;
    }
    
    /**
     * Calculate dynamic delay based on device performance and API level
     * Older/slower devices get longer delays
     */
    private long calculatePermissionDelay() {
        int apiLevel = android.os.Build.VERSION.SDK_INT;
        
        // Base delay increases for older Android versions
        long baseDelay = 2000; // 2 seconds default
        
        if (apiLevel < 26) { // Pre-Android 8.0
            baseDelay = 4000; // 4 seconds
        } else if (apiLevel < 29) { // Pre-Android 10
            baseDelay = 3000; // 3 seconds
        }
        
        // Add exponential backoff for retries
        long retryMultiplier = (long) Math.pow(1.5, permissionRetryCount);
        
        return Math.min(baseDelay * retryMultiplier, 10000); // Cap at 10 seconds
    }
    
    public void startDetection() {
        // Reset retry count for new detection attempt
        permissionRetryCount = 0;
        attemptDetection();
    }
    
    private void attemptDetection() {
        callback.onStateChanged(PrinterState.INITIALIZING, permissionRetryCount > 0 ? 
            "Повторен опит за свързване..." : "Инициализиране...");
        
        // Find Brother device
        UsbDevice brotherDevice = findBrotherDevice();
        if (brotherDevice == null) {
            callback.onError("Няма намерен Brother принтер. Моля, свържете QL-800 чрез USB.");
            return;
        }
        
        // Check if we already have permission
        if (usbManager.hasPermission(brotherDevice)) {
            // We have permission, search directly
            performPrinterSearch();
        } else {
            // Need permission - register receiver and request
            registerUsbReceiver();
            requestUsbPermission(brotherDevice);
        }
    }
    
    private UsbDevice findBrotherDevice() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            String deviceName = device.getDeviceName();
            if (deviceName != null && (deviceName.contains("QL-") || device.getVendorId() == 0x04f9)) {
                return device;
            }
        }
        return null;
    }
    
    private void requestUsbPermission(UsbDevice device) {
        callback.onStateChanged(PrinterState.REQUESTING_PERMISSION, "Моля, разрешете достъп до USB устройството");
        
        Context contextToUse = activityContext != null ? activityContext : context;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(contextToUse, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        
        usbManager.requestPermission(device, permissionIntent);
    }
    
    private void performPrinterSearch() {
        callback.onStateChanged(PrinterState.SEARCHING_PRINTER, "Търсене на принтер...");
        
        new Thread(() -> {
            try {
                PrinterSearchResult result = PrinterSearcher.startUSBSearch(context);
                
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (result.getError().getCode() == com.brother.sdk.lmprinter.PrinterSearchError.ErrorCode.NoError) {
                        if (result.getChannels().isEmpty()) {
                            callback.onError("Няма намерени Brother принтери по USB. Моля, проверете връзката.");
                        } else {
                            printerChannel = result.getChannels().get(0);
                            callback.onSuccess();
                        }
                    } else {
                        callback.onError("Грешка при търсене на принтери: " + result.getError().getCode());
                    }
                });
                
            } catch (Exception e) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    callback.onError("Изключение при търсене на принтер: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void registerUsbReceiver() {
        try {
            Context contextToUse = activityContext != null ? activityContext : context;
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.registerReceiver(contextToUse, usbReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            } else {
                contextToUse.registerReceiver(usbReceiver, filter);
            }
        } catch (Exception e) {
            android.util.Log.e("PrinterManager", "Failed to register USB receiver: " + e.getMessage());
        }
    }
    
    private void unregisterUsbReceiver() {
        try {
            Context contextToUse = activityContext != null ? activityContext : context;
            contextToUse.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            // Ignore - receiver not registered
        }
    }
    
    // Enhanced USB Permission BroadcastReceiver with dynamic delay and retry logic
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                
                // Unregister receiver immediately
                unregisterUsbReceiver();
                
                // Use dynamic delay based on device performance
                long delay = calculatePermissionDelay();
                
                String delayMessage = permissionRetryCount > 0 ? 
                    "Изчакване за отговор (" + (delay / 1000) + " сек)..." : 
                    "Обработване на разрешението...";
                callback.onStateChanged(PrinterState.REQUESTING_PERMISSION, delayMessage);
                
                // Wait dynamically calculated time then act on the result
                retryHandler.postDelayed(() -> {
                    handlePermissionResult(permissionGranted);
                }, delay);
            }
        }
    };
    
    /**
     * Handle permission result with retry logic
     */
    private void handlePermissionResult(boolean permissionGranted) {
        if (permissionGranted) {
            // Permission granted - verify device is still available and search
            UsbDevice brotherDevice = findBrotherDevice();
            if (brotherDevice != null && usbManager.hasPermission(brotherDevice)) {
                performPrinterSearch();
            } else {
                // Device disappeared or permission lost - retry if possible
                attemptRetryOrFail("USB устройството не е достъпно след получаване на разрешение.");
            }
        } else {
            // Permission denied - retry if possible
            attemptRetryOrFail("Достъпът до USB бе отказан.");
        }
    }
    
    /**
     * Attempt retry or show final error
     */
    private void attemptRetryOrFail(String baseErrorMessage) {
        if (permissionRetryCount < MAX_PERMISSION_RETRIES) {
            permissionRetryCount++;
            
            // Show retry message
            String retryMessage = baseErrorMessage + " Повторен опит " + permissionRetryCount + "/" + MAX_PERMISSION_RETRIES + "...";
            callback.onStateChanged(PrinterState.INITIALIZING, retryMessage);
            
            // Wait before retry to avoid overwhelming the system
            retryHandler.postDelayed(() -> {
                attemptDetection();
            }, 1000); // 1 second before retry
            
        } else {
            // All retries exhausted
            String finalError = baseErrorMessage + " Опитани са " + MAX_PERMISSION_RETRIES + " пъти. " +
                "Моля, рестартирайте приложението или проверете USB връзката.";
            callback.onError(finalError);
        }
    }
    
    public Channel getPrinterChannel() {
        return printerChannel;
    }
    
    public void cleanup() {
        unregisterUsbReceiver();
        
        // Cancel any pending retry operations
        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }
        
        printerChannel = null;
        permissionRetryCount = 0;
    }
    
    public boolean isPrinterReady() {
        return printerChannel != null;
    }
    
    public void redetectPrinter() {
        printerChannel = null;
        startDetection();
    }
}