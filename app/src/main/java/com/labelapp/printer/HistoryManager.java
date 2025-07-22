package com.labelapp.printer;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HistoryManager - Manages autocomplete history for manufacturer, importer, and product fields
 * Supports Bulgarian Cyrillic text with proper normalization and filtering
 */
public class HistoryManager {
    
    private static final String PREFS_NAME = "label_app_history";
    private static final int MAX_ENTRIES_PER_FIELD = 50;
    private static final int MAX_SUGGESTIONS = 3;
    
    // SharedPreferences keys for search (normalized) data
    private static final String KEY_MANUFACTURER_SEARCH = "manufacturer_search";
    private static final String KEY_IMPORTER_SEARCH = "importer_search";
    private static final String KEY_PRODUCT_SEARCH = "product_search";
    
    // SharedPreferences keys for display (original casing) data
    private static final String KEY_MANUFACTURER_DISPLAY = "manufacturer_display";
    private static final String KEY_IMPORTER_DISPLAY = "importer_display";
    private static final String KEY_PRODUCT_DISPLAY = "product_display";
    
    // Bulgarian locale for proper text normalization
    private static final Locale BULGARIAN_LOCALE = new Locale("bg", "BG");
    
    // Singleton instance
    private static HistoryManager instance;
    private SharedPreferences prefs;
    private Context context;
    
    
    public enum FieldType {
        MANUFACTURER,
        IMPORTER,
        PRODUCT
    }
    
    private HistoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized HistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Add a value to history for the specified field type
     * Only adds if the value is not empty and not already present
     */
    public void addValue(FieldType fieldType, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        
        String trimmedValue = value.trim();
        String normalizedValue = normalizeText(trimmedValue);
        
        String searchKey = getSearchKey(fieldType);
        String displayKey = getDisplayKey(fieldType);
        
        // Get current values
        Set<String> searchValues = new HashSet<>(prefs.getStringSet(searchKey, new HashSet<>()));
        Set<String> displayValues = new HashSet<>(prefs.getStringSet(displayKey, new HashSet<>()));
        
        // Check if normalized value already exists
        if (searchValues.contains(normalizedValue)) {
            return; // Duplicate found, don't add
        }
        
        // Add new values
        searchValues.add(normalizedValue);
        displayValues.add(trimmedValue);
        
        // Limit size (remove oldest if needed)
        // Note: Sets don't maintain order, so we'll just trim by conversion to list
        if (searchValues.size() > MAX_ENTRIES_PER_FIELD) {
            List<String> searchList = new ArrayList<>(searchValues);
            List<String> displayList = new ArrayList<>(displayValues);
            
            // Keep the most recent entries (simple approach)
            searchValues = new HashSet<>(searchList.subList(searchList.size() - MAX_ENTRIES_PER_FIELD, searchList.size()));
            displayValues = new HashSet<>(displayList.subList(displayList.size() - MAX_ENTRIES_PER_FIELD, displayList.size()));
        }
        
        // Save to SharedPreferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(searchKey, searchValues);
        editor.putStringSet(displayKey, displayValues);
        editor.apply();
    }
    
    /**
     * Get filtered suggestions based on user input
     * Returns display versions of matching entries
     */
    public List<String> getFilteredSuggestions(FieldType fieldType, String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String normalizedInput = normalizeText(userInput.trim());
        String searchKey = getSearchKey(fieldType);
        String displayKey = getDisplayKey(fieldType);
        
        Set<String> searchValues = prefs.getStringSet(searchKey, new HashSet<>());
        Set<String> displayValues = prefs.getStringSet(displayKey, new HashSet<>());
        
        List<String> suggestions = new ArrayList<>();
        List<String> searchList = new ArrayList<>(searchValues);
        List<String> displayList = new ArrayList<>(displayValues);
        
        // Find matching entries by iterating through display values and checking their normalized versions
        for (String displayValue : displayValues) {
            if (suggestions.size() >= MAX_SUGGESTIONS) {
                break;
            }
            
            // Normalize this display value to check against input
            String normalizedDisplayValue = normalizeText(displayValue);
            
            boolean isMatch = false;
            
            if (fieldType == FieldType.PRODUCT) {
                // Product field: Word-boundary matching (any word can start with input)
                isMatch = matchesAnyWord(normalizedDisplayValue, normalizedInput);
            } else {
                // Manufacturer/Importer: Character-by-character from beginning
                String normalizedInputNoSpaces = normalizedInput.replaceAll("\\s", "");
                String searchValueNoSpaces = normalizedDisplayValue.replaceAll("\\s", "");
                isMatch = searchValueNoSpaces.startsWith(normalizedInputNoSpaces);
            }
            
            if (isMatch) {
                suggestions.add(displayValue);
            }
        }
        
        return suggestions;
    }
    
    /**
     * Get all values for a field type (for debugging or admin purposes)
     */
    public List<String> getAllValues(FieldType fieldType) {
        String displayKey = getDisplayKey(fieldType);
        Set<String> displayValues = prefs.getStringSet(displayKey, new HashSet<>());
        return new ArrayList<>(displayValues);
    }
    
    /**
     * Clear history for a specific field type
     */
    public void clearHistory(FieldType fieldType) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(getSearchKey(fieldType));
        editor.remove(getDisplayKey(fieldType));
        editor.apply();
    }
    
    /**
     * Clear all history
     */
    public void clearAllHistory() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }
    
    
    /**
     * Check if user input matches the beginning of any word in the text
     * Used for product field to allow matching "Панталон" in "Мъжки Панталон"
     */
    private boolean matchesAnyWord(String text, String userInput) {
        if (text == null || userInput == null || userInput.isEmpty()) {
            return false;
        }
        
        // Split text into words and check each word
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith(userInput)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Normalize text for searching - handles Bulgarian Cyrillic properly
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        
        return text.toLowerCase(BULGARIAN_LOCALE).trim();
    }
    
    /**
     * Get SharedPreferences key for search data
     */
    private String getSearchKey(FieldType fieldType) {
        switch (fieldType) {
            case MANUFACTURER:
                return KEY_MANUFACTURER_SEARCH;
            case IMPORTER:
                return KEY_IMPORTER_SEARCH;
            case PRODUCT:
                return KEY_PRODUCT_SEARCH;
            default:
                throw new IllegalArgumentException("Unknown field type: " + fieldType);
        }
    }
    
    /**
     * Get SharedPreferences key for display data
     */
    private String getDisplayKey(FieldType fieldType) {
        switch (fieldType) {
            case MANUFACTURER:
                return KEY_MANUFACTURER_DISPLAY;
            case IMPORTER:
                return KEY_IMPORTER_DISPLAY;
            case PRODUCT:
                return KEY_PRODUCT_DISPLAY;
            default:
                throw new IllegalArgumentException("Unknown field type: " + fieldType);
        }
    }
}