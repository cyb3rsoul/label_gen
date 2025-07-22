// DOM elements
const form = document.getElementById('labelForm');
const previewBtn = document.getElementById('previewBtn');
const printBtn = document.getElementById('printBtn');
const previewArea = document.getElementById('previewArea');
const labelPreview = document.getElementById('labelPreview');

// Size type handling
const sizeTypeRadios = document.querySelectorAll('input[name="sizeType"]');
const sizesContainer = document.getElementById('sizesContainer');
const addSizeBtn = document.getElementById('addSizeBtn');

// Size options
const sizeLetterOptions = [
    { value: '', text: 'Изберете размер...' },
    { value: 'XS', text: 'XS' },
    { value: 'S', text: 'S' },
    { value: 'M', text: 'M' },
    { value: 'L', text: 'L' },
    { value: 'XL', text: 'XL' },
    { value: 'XXL', text: 'XXL' },
    { value: 'XXXL', text: 'XXXL' },
    { value: '-', text: '-' }
];

// Size row counter
let sizeRowCounter = 0;

// Origin handling
const originSelect = document.getElementById('origin');
const customOriginGroup = document.getElementById('customOriginGroup');

// Price handling
const priceEur = document.getElementById('priceEur');
const priceBgn = document.getElementById('priceBgn');
const autoConvert = document.getElementById('autoConvert');

// Dynamic materials handling
const materialsContainer = document.getElementById('materialsContainer');
const percentageTotal = document.getElementById('percentageTotal');

// Autocomplete elements
const manufacturerInput = document.getElementById('manufacturer');
const importerInput = document.getElementById('importer');
const productInput = document.getElementById('product');
const manufacturerDropdown = document.getElementById('manufacturerDropdown');
const importerDropdown = document.getElementById('importerDropdown');
const productDropdown = document.getElementById('productDropdown');

// Material options
const materialOptions = [
    { value: '', text: 'Изберете материал...' },
    { value: 'Памук', text: 'Памук' },
    { value: 'Полиестер', text: 'Полиестер' },
    { value: 'Еластан', text: 'Еластан' },
    { value: 'Вискоза', text: 'Вискоза' },
    { value: 'Вълна', text: 'Вълна' },
    { value: 'Найлон', text: 'Найлон' },
    { value: 'Коприна', text: 'Коприна' },
    { value: 'Лен', text: 'Лен' },
    { value: 'Акрил', text: 'Акрил' },
    { value: 'Металик', text: 'Металик' },
    { value: 'Спандекс', text: 'Спандекс' },
    { value: 'Полиамид', text: 'Полиамид' }
];

// Material row counter
let materialRowCounter = 0;

// Track if page is still initializing (to prevent auto-focus during load)
let pageInitializing = true;

// Track when we're auto-focusing a new material field (to prevent keyboard scroll conflict)
let autoFocusingMaterial = false;

// Exchange rate (EUR to BGN)
const EUR_TO_BGN_RATE = 1.9558;

// Initialize the form
document.addEventListener('DOMContentLoaded', function() {
    // Ensure page starts at the top
    window.scrollTo(0, 0);
    
    setupEventListeners();
    initializeMaterialRows();
    initializeSizeRows();
    setupAutocompleteEventListeners();
    setupMobileKeyboardHandling();
    
    // Mark initialization as complete after a short delay
    setTimeout(() => {
        pageInitializing = false;
    }, 500);
});


// Mobile keyboard handling for better UX
function setupMobileKeyboardHandling() {
    // Estimated keyboard height on mobile devices
    const KEYBOARD_HEIGHT = 350; // pixels
    const SAFE_PADDING = 50; // Extra padding above the field
    
    // Add focus listeners to all input and select elements
    const focusableElements = document.querySelectorAll('input, select, textarea');
    
    focusableElements.forEach(element => {
        element.addEventListener('focus', function() {
            // Skip keyboard scroll if we're auto-focusing a material field
            if (autoFocusingMaterial) {
                return;
            }
            
            // Small delay to ensure keyboard is opening
            setTimeout(() => {
                scrollToField(this);
            }, 200);
        });
    });
    
    // Also handle dynamically added elements (materials, sizes)
    document.addEventListener('focus', function(event) {
        if (event.target.matches('input, select, textarea')) {
            // Skip keyboard scroll if we're auto-focusing a material field
            if (autoFocusingMaterial) {
                return;
            }
            
            setTimeout(() => {
                scrollToField(event.target);
            }, 200);
        }
    }, true); // Use capture phase to catch dynamically added elements
    
    function scrollToField(field) {
        const fieldRect = field.getBoundingClientRect();
        const viewportHeight = window.innerHeight;
        const fieldTop = fieldRect.top + window.scrollY;
        
        // Calculate safe zone (viewport - keyboard - padding)
        const safeZoneHeight = viewportHeight - KEYBOARD_HEIGHT - SAFE_PADDING;
        
        // If field is in lower part of screen that would be covered by keyboard
        if (fieldRect.top > safeZoneHeight) {
            // Scroll so field appears in safe zone
            const targetScrollY = fieldTop - SAFE_PADDING;
            
            window.scrollTo({
                top: targetScrollY,
                behavior: 'smooth'
            });
        }
    }
}

// Autocomplete functionality
function setupAutocompleteEventListeners() {
    // Set up custom dropdowns for all three fields
    setupCustomAutocomplete(manufacturerInput, manufacturerDropdown, 'MANUFACTURER');
    setupCustomAutocomplete(importerInput, importerDropdown, 'IMPORTER');
    setupCustomAutocomplete(productInput, productDropdown, 'PRODUCT');
}

// Custom autocomplete setup for a specific field
function setupCustomAutocomplete(inputElement, dropdownElement, fieldType) {
    let highlightedIndex = -1;
    let currentSuggestions = [];
    
    // Input event - show suggestions after 1+ characters
    inputElement.addEventListener('input', function() {
        const userInput = this.value.trim();
        
        if (userInput.length === 0) {
            hideDropdown(dropdownElement);
            return;
        }
        
        // Get filtered suggestions from Android
        if (typeof Android !== 'undefined' && Android.getFilteredSuggestions) {
            const suggestionsJson = Android.getFilteredSuggestions(fieldType, userInput);
            const suggestions = JSON.parse(suggestionsJson);
            
            currentSuggestions = suggestions;
            showCustomDropdown(dropdownElement, suggestions, inputElement);
        }
    });
    
    // Focus event - don't show anything until user types
    inputElement.addEventListener('focus', function() {
        if (this.value.trim().length > 0) {
            // Re-trigger input event if there's already text
            this.dispatchEvent(new Event('input'));
        }
    });
    
    // Blur event - hide dropdown after short delay
    inputElement.addEventListener('blur', function() {
        setTimeout(() => {
            hideDropdown(dropdownElement);
        }, 200); // Delay allows clicking on dropdown items
    });
    
    // Keyboard navigation
    inputElement.addEventListener('keydown', function(e) {
        if (!dropdownElement.classList.contains('show')) return;
        
        const items = dropdownElement.querySelectorAll('.autocomplete-item:not(.no-results)');
        
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            highlightedIndex = Math.min(highlightedIndex + 1, items.length - 1);
            updateHighlight(items, highlightedIndex);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            highlightedIndex = Math.max(highlightedIndex - 1, -1);
            updateHighlight(items, highlightedIndex);
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (highlightedIndex >= 0 && highlightedIndex < items.length) {
                selectSuggestion(inputElement, dropdownElement, items[highlightedIndex].textContent);
            }
        } else if (e.key === 'Escape') {
            hideDropdown(dropdownElement);
        }
    });
    
}

function showCustomDropdown(dropdownElement, suggestions, inputElement) {
    dropdownElement.innerHTML = '';
    
    if (suggestions.length > 0) {
        suggestions.forEach((suggestion, index) => {
            const item = document.createElement('div');
            item.className = 'autocomplete-item';
            item.textContent = suggestion;
            
            item.addEventListener('click', function() {
                selectSuggestion(inputElement, dropdownElement, suggestion);
            });
            
            dropdownElement.appendChild(item);
        });
        dropdownElement.classList.add('show');
    }
}

function hideDropdown(dropdownElement) {
    dropdownElement.classList.remove('show');
}

function selectSuggestion(inputElement, dropdownElement, value) {
    inputElement.value = value;
    hideDropdown(dropdownElement);
    inputElement.focus();
}

function updateHighlight(items, highlightedIndex) {
    items.forEach((item, index) => {
        if (index === highlightedIndex) {
            item.classList.add('highlighted');
        } else {
            item.classList.remove('highlighted');
        }
    });
}


// Called from Android to save history values after successful print
function saveToHistory(manufacturer, importer, product) {
    if (typeof Android !== 'undefined' && Android.saveHistoryValues) {
        Android.saveHistoryValues(manufacturer || '', importer || '', product || '');
    }
}

function setupEventListeners() {
    // Size type change - handle clicks on radio option containers
    const radioOptions = document.querySelectorAll('.radio-option');
    radioOptions.forEach(option => {
        option.addEventListener('click', function() {
            const radio = this.querySelector('input[type="radio"]');
            if (radio && !radio.checked) {
                radio.checked = true;
                recreateSizeRows();
            }
        });
    });
    
    // Also handle direct radio button changes (for accessibility)
    sizeTypeRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            recreateSizeRows();
        });
    });
    
    // Add size button
    addSizeBtn.addEventListener('click', addSizeRow);

    // Origin change
    originSelect.addEventListener('change', function() {
        if (this.value === 'Друго') {
            customOriginGroup.style.display = 'block';
            document.getElementById('customOrigin').required = true;
        } else {
            customOriginGroup.style.display = 'none';
            document.getElementById('customOrigin').required = false;
        }
    });

    // Currency auto-conversion (BGN to EUR only)
    priceBgn.addEventListener('input', function() {
        if (this.value) {
            const eurValue = (parseFloat(this.value) / EUR_TO_BGN_RATE).toFixed(2);
            priceEur.value = eurValue;
        } else {
            priceEur.value = '';
        }
    });

    // Auto-hide keyboard on price field "Enter" key
    priceBgn.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            this.blur(); // Hide keyboard
        }
    });


    // Button events
    previewBtn.addEventListener('click', previewLabel);
    printBtn.addEventListener('click', handlePrint);

    // Form reset
    form.addEventListener('reset', function() {
        setTimeout(() => {
            initializeMaterialRows();
            initializeSizeRows();
            previewArea.style.display = 'none';
        }, 10);
    });
}

// Dynamic Material Row Functions
function initializeMaterialRows() {
    materialsContainer.innerHTML = '';
    materialRowCounter = 0;
    addMaterialRow(); // Start with one row
}

function createMaterialSelect() {
    const select = document.createElement('select');
    select.className = 'material-type';
    
    materialOptions.forEach(option => {
        const optionElement = document.createElement('option');
        optionElement.value = option.value;
        optionElement.textContent = option.text;
        select.appendChild(optionElement);
    });
    
    return select;
}

function addMaterialRow() {
    materialRowCounter++;
    const rowId = `material-row-${materialRowCounter}`;
    
    const row = document.createElement('div');
    row.className = 'material-row adding';
    row.id = rowId;
    
    // Create select dropdown
    const select = createMaterialSelect();
    select.name = `materialType${materialRowCounter}`;
    
    // Create percentage input
    const percentageInput = document.createElement('input');
    percentageInput.type = 'number';
    percentageInput.className = 'material-percentage';
    percentageInput.name = `materialPercentage${materialRowCounter}`;
    percentageInput.min = '1';
    percentageInput.max = '100';
    percentageInput.placeholder = '%';
    
    // Create percentage symbol
    const percentageSymbol = document.createElement('span');
    percentageSymbol.className = 'percentage-symbol';
    percentageSymbol.textContent = '%';
    
    // Create remove button (only if more than 1 row)
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn-remove-material';
    removeBtn.innerHTML = '×';
    removeBtn.title = 'Премахни материал';
    removeBtn.onclick = () => removeMaterialRow(rowId);
    
    // Add elements to row
    row.appendChild(select);
    row.appendChild(percentageInput);
    row.appendChild(percentageSymbol);
    
    // Only add remove button if we have more than 1 row or this isn't the first row
    if (materialsContainer.children.length > 0) {
        row.appendChild(removeBtn);
    }
    
    // Add event listeners
    select.addEventListener('change', function() {
        // After selecting material, focus on percentage input (only during user interaction)
        if (this.value && !pageInitializing) {
            autoFocusingMaterial = true;
            percentageInput.focus();
            // Clear the flag after a short delay
            setTimeout(() => {
                autoFocusingMaterial = false;
            }, 300);
        }
        updatePercentageTotal();
        checkAutoAddRow();
    });
    percentageInput.addEventListener('input', function() {
        updatePercentageTotal();
    });
    percentageInput.addEventListener('change', function() {
        updatePercentageTotal();
        checkAutoAddRow(); // Only check on 'change' (when user finishes typing)
    });
    percentageInput.addEventListener('blur', function() {
        checkAutoAddRow(); // Also check when user leaves the field
    });
    
    // Add row to container
    materialsContainer.appendChild(row);
    
    // Update remove buttons visibility
    updateRemoveButtons();
    
    // Update percentage total
    updatePercentageTotal();
    
    // Note: No auto-focus to prevent page scrolling on initialization
}

function removeMaterialRow(rowId) {
    const row = document.getElementById(rowId);
    if (!row) return;
    
    row.classList.add('removing');
    
    setTimeout(() => {
        row.remove();
        updatePercentageTotal();
        updateRemoveButtons();
    }, 300);
}

function updateRemoveButtons() {
    const rows = materialsContainer.querySelectorAll('.material-row');
    
    rows.forEach((row, index) => {
        const removeBtn = row.querySelector('.btn-remove-material');
        
        if (rows.length === 1) {
            // Hide remove button if only one row
            if (removeBtn) {
                removeBtn.style.display = 'none';
            }
        } else {
            // Show remove button and add if not exists
            if (!removeBtn) {
                const newRemoveBtn = document.createElement('button');
                newRemoveBtn.type = 'button';
                newRemoveBtn.className = 'btn-remove-material';
                newRemoveBtn.innerHTML = '×';
                newRemoveBtn.title = 'Премахни материал';
                newRemoveBtn.onclick = () => removeMaterialRow(row.id);
                row.appendChild(newRemoveBtn);
            } else {
                removeBtn.style.display = 'flex';
            }
        }
    });
}

function checkAutoAddRow() {
    const rows = materialsContainer.querySelectorAll('.material-row');
    
    // Check if ALL existing rows are completely filled
    const allRowsFilled = Array.from(rows).every(row => {
        const rowSelect = row.querySelector('.material-type');
        const rowPercentage = row.querySelector('.material-percentage');
        return rowSelect.value && rowPercentage.value;
    });
    
    // Calculate current total percentage
    let currentTotal = 0;
    rows.forEach(row => {
        const percentageInput = row.querySelector('.material-percentage');
        if (percentageInput && percentageInput.value) {
            currentTotal += parseFloat(percentageInput.value) || 0;
        }
    });
    
    // Only add new row if:
    // 1. All current rows are completely filled
    // 2. We haven't reached the maximum limit
    // 3. Total percentage is less than 100%
    // 4. We don't already have an auto-add in progress
    if (allRowsFilled && rows.length < 10 && currentTotal < 100 && !materialsContainer.dataset.adding) {
        materialsContainer.dataset.adding = 'true';
        
        setTimeout(() => {
            addMaterialRow();
            delete materialsContainer.dataset.adding;
            
            // Focus on the newly added material field (only during user interaction)
            if (!pageInitializing) {
                setTimeout(() => {
                    const newRows = materialsContainer.querySelectorAll('.material-row');
                    const lastRow = newRows[newRows.length - 1];
                    const newSelect = lastRow.querySelector('.material-type');
                    if (newSelect) {
                        autoFocusingMaterial = true;
                        newSelect.focus();
                        
                        // After focus, manually trigger keyboard-aware scrolling for the new field
                        setTimeout(() => {
                            // Calculate keyboard-safe position for the new material field
                            const fieldRect = newSelect.getBoundingClientRect();
                            const viewportHeight = window.innerHeight;
                            const KEYBOARD_HEIGHT = 350;
                            const SAFE_PADDING = 50;
                            const fieldTop = fieldRect.top + window.scrollY;
                            const safeZoneHeight = viewportHeight - KEYBOARD_HEIGHT - SAFE_PADDING;
                            
                            // Always scroll to make the new field visible above keyboard
                            const targetScrollY = fieldTop - SAFE_PADDING;
                            
                            window.scrollTo({
                                top: targetScrollY,
                                behavior: 'smooth'
                            });
                            
                            // Clear the flag after scroll completes
                            setTimeout(() => {
                                autoFocusingMaterial = false;
                            }, 200);
                        }, 100);
                    }
                }, 50);
            }
        }, 150);
    }
}

// Dynamic Size Row Functions
function initializeSizeRows() {
    sizesContainer.innerHTML = '';
    sizeRowCounter = 0;
    addSizeRow(); // Start with one row
}

function createSizeInput() {
    const sizeType = document.querySelector('input[name="sizeType"]:checked').value;
    
    if (sizeType === 'letters') {
        const select = document.createElement('select');
        select.className = 'size-input';
        
        sizeLetterOptions.forEach(option => {
            const optionElement = document.createElement('option');
            optionElement.value = option.value;
            optionElement.textContent = option.text;
            select.appendChild(optionElement);
        });
        
        return select;
    } else {
        const input = document.createElement('input');
        input.type = 'number';
        input.className = 'size-input';
        input.min = '20';
        input.max = '60';
        input.step = '2';
        input.placeholder = 'Въведете размер';
        
        return input;
    }
}

function addSizeRow() {
    sizeRowCounter++;
    const rowId = `size-row-${sizeRowCounter}`;
    
    const row = document.createElement('div');
    row.className = 'size-row adding';
    row.id = rowId;
    
    // Create size input
    const sizeInput = createSizeInput();
    sizeInput.name = `size${sizeRowCounter}`;
    sizeInput.className = 'size-input';
    
    // Create quantity controls container
    const quantityContainer = document.createElement('div');
    quantityContainer.className = 'quantity-controls';
    
    // Create quantity label
    const quantityLabel = document.createElement('span');
    quantityLabel.className = 'quantity-label';
    quantityLabel.textContent = 'Брой:';
    
    // Create quantity decrease button
    const decreaseBtn = document.createElement('button');
    decreaseBtn.type = 'button';
    decreaseBtn.className = 'btn-quantity btn-decrease';
    decreaseBtn.innerHTML = '−';
    decreaseBtn.title = 'Намали количеството';
    
    // Create quantity input
    const quantityInput = document.createElement('input');
    quantityInput.type = 'number';
    quantityInput.className = 'quantity-input';
    quantityInput.name = `quantity${sizeRowCounter}`;
    quantityInput.value = '1';
    quantityInput.min = '1';
    quantityInput.max = '99';
    
    // Create quantity increase button
    const increaseBtn = document.createElement('button');
    increaseBtn.type = 'button';
    increaseBtn.className = 'btn-quantity btn-increase';
    increaseBtn.innerHTML = '+';
    increaseBtn.title = 'Увеличи количеството';
    
    // Create remove button (only if more than 1 row)
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn-remove-size';
    removeBtn.innerHTML = '×';
    removeBtn.title = 'Премахни размер';
    removeBtn.onclick = () => removeSizeRow(rowId);
    
    // Add quantity control event listeners
    decreaseBtn.addEventListener('click', function() {
        const currentValue = parseInt(quantityInput.value) || 1;
        if (currentValue > 1) {
            quantityInput.value = currentValue - 1;
        }
    });
    
    increaseBtn.addEventListener('click', function() {
        const currentValue = parseInt(quantityInput.value) || 1;
        if (currentValue < 99) {
            quantityInput.value = currentValue + 1;
        }
    });
    
    // Ensure quantity input stays within bounds
    quantityInput.addEventListener('input', function() {
        let value = parseInt(this.value);
        if (isNaN(value) || value < 1) {
            this.value = 1;
        } else if (value > 99) {
            this.value = 99;
        }
    });
    
    // Assemble quantity controls
    quantityContainer.appendChild(quantityLabel);
    quantityContainer.appendChild(decreaseBtn);
    quantityContainer.appendChild(quantityInput);
    quantityContainer.appendChild(increaseBtn);
    
    // Add elements to row
    row.appendChild(sizeInput);
    row.appendChild(quantityContainer);
    
    // Only add remove button if we have more than 1 row
    if (sizesContainer.children.length > 0) {
        row.appendChild(removeBtn);
    }
    
    // Add row to container
    sizesContainer.appendChild(row);
    
    // Update remove buttons visibility
    updateSizeRemoveButtons();
    
    // Note: No auto-focus to prevent page scrolling on initialization
}

function removeSizeRow(rowId) {
    const row = document.getElementById(rowId);
    if (!row) return;
    
    row.classList.add('removing');
    
    setTimeout(() => {
        row.remove();
        updateSizeRemoveButtons();
    }, 300);
}

function updateSizeRemoveButtons() {
    const rows = sizesContainer.querySelectorAll('.size-row');
    
    rows.forEach((row, index) => {
        const removeBtn = row.querySelector('.btn-remove-size');
        
        if (rows.length === 1) {
            // Hide remove button if only one row
            if (removeBtn) {
                removeBtn.style.display = 'none';
            }
        } else {
            // Show remove button and add if not exists
            if (!removeBtn) {
                const newRemoveBtn = document.createElement('button');
                newRemoveBtn.type = 'button';
                newRemoveBtn.className = 'btn-remove-size';
                newRemoveBtn.innerHTML = '×';
                newRemoveBtn.title = 'Премахни размер';
                newRemoveBtn.onclick = () => removeSizeRow(row.id);
                row.appendChild(newRemoveBtn);
            } else {
                removeBtn.style.display = 'flex';
            }
        }
    });
}

function recreateSizeRows() {
    // Store current values with quantities
    const currentValues = [];
    const sizeRows = sizesContainer.querySelectorAll('.size-row');
    sizeRows.forEach(row => {
        const sizeInput = row.querySelector('.size-input');
        const quantityInput = row.querySelector('.quantity-input');
        if (sizeInput && sizeInput.value) {
            currentValues.push({
                size: sizeInput.value,
                quantity: quantityInput ? parseInt(quantityInput.value) || 1 : 1
            });
        }
    });
    
    // Clear and recreate
    sizesContainer.innerHTML = '';
    sizeRowCounter = 0;
    
    // Add rows with preserved values
    if (currentValues.length === 0) {
        addSizeRow();
    } else {
        currentValues.forEach(valueObj => {
            addSizeRow();
            const lastRow = sizesContainer.lastElementChild;
            const lastSizeInput = lastRow.querySelector('.size-input');
            const lastQuantityInput = lastRow.querySelector('.quantity-input');
            
            lastSizeInput.value = valueObj.size;
            if (lastQuantityInput) {
                lastQuantityInput.value = valueObj.quantity;
            }
        });
    }
}


function updatePercentageTotal() {
    let total = 0;
    
    const percentageInputs = materialsContainer.querySelectorAll('.material-percentage');
    
    percentageInputs.forEach(input => {
        const value = parseFloat(input.value) || 0;
        total += value;
    });
    percentageTotal.textContent = `Общо: ${total}%`;
    
    // Update styling based on total
    percentageTotal.classList.remove('valid', 'invalid');
    if (total === 100) {
        percentageTotal.classList.add('valid');
    } else if (total > 0 && total !== 100) {
        percentageTotal.classList.add('invalid');
    }
    
    return total;
}



function collectFormData() {
    const formData = new FormData(form);
    const data = {};
    
    // Collect basic fields
    data.product = formData.get('product') || '';
    data.importer = formData.get('importer') || '';
    data.manufacturer = formData.get('manufacturer') || '';
    
    // Handle origin
    data.origin = formData.get('origin') === 'Друго' 
        ? formData.get('customOrigin') || ''
        : formData.get('origin') || '';
    
    // Handle multiple sizes with quantities
    data.sizes = [];
    const sizeRows = sizesContainer.querySelectorAll('.size-row');
    
    sizeRows.forEach(row => {
        const sizeInput = row.querySelector('.size-input');
        const quantityInput = row.querySelector('.quantity-input');
        
        if (sizeInput && quantityInput && sizeInput.value) {
            const quantity = parseInt(quantityInput.value) || 1;
            data.sizes.push({
                size: sizeInput.value,
                quantity: quantity
            });
        }
    });
    
    // Handle dynamic materials
    data.materials = [];
    const materialRows = materialsContainer.querySelectorAll('.material-row');
    
    materialRows.forEach(row => {
        const typeSelect = row.querySelector('.material-type');
        const percentageInput = row.querySelector('.material-percentage');
        
        if (typeSelect && percentageInput && typeSelect.value && percentageInput.value) {
            data.materials.push({
                type: typeSelect.value,
                percentage: parseInt(percentageInput.value)
            });
        }
    });
    
    // Handle prices
    data.priceEur = formData.get('priceEur') ? parseFloat(formData.get('priceEur')).toFixed(2) : '';
    data.priceBgn = formData.get('priceBgn') ? parseFloat(formData.get('priceBgn')).toFixed(2) : '';
    
    return data;
}

function validateForm() {
    const data = collectFormData();
    const errors = [];
    
    // Required fields
    if (!data.product.trim()) errors.push('Артикулът е задължителен');
    if (!data.origin.trim()) errors.push('Произходът е задължителен');
    if (!data.manufacturer.trim()) errors.push('Производителят е задължителен');
    if (!data.importer.trim()) errors.push('Вносителят е задължителен');
    if (data.sizes.length === 0) errors.push('Поне един размер е задължителен');
    
    // Field length limits (50 characters max)
    if (data.product.length > 50) errors.push('Артикулът не може да превишава 50 символа');
    if (data.origin.length > 50) errors.push('Произходът не може да превишава 50 символа');
    if (data.manufacturer.length > 50) errors.push('Производителят не може да превишава 50 символа');
    if (data.importer.length > 50) errors.push('Вносителят не може да превишава 50 символа');
    
    // Price length limits (50 characters max)
    if (data.priceEur && data.priceEur.length > 50) errors.push('Цената в EUR не може да превишава 50 символа');
    if (data.priceBgn && data.priceBgn.length > 50) errors.push('Цената в BGN не може да превишава 50 символа');
    
    // Material validation
    if (data.materials.length === 0) {
        errors.push('Поне един материал е задължителен');
    } else {
        const total = data.materials.reduce((sum, mat) => sum + mat.percentage, 0);
        if (total !== 100) {
            errors.push(`Процентите на материалите трябва да са общо 100% (текущо: ${total}%)`);
        }
    }
    
    // Price validation (only BGN required, EUR auto-calculated)
    if (!data.priceBgn) {
        errors.push('Цената в BGN е задължителна');
    }
    
    return {
        isValid: errors.length === 0,
        errors: errors,
        data: data
    };
}

function generateMultipleLabelsHTML(data) {
    // Calculate total number of labels
    const totalLabels = data.sizes.reduce((sum, sizeObj) => sum + sizeObj.quantity, 0);
    
    let labelIndex = 0;
    let labelsHTML = '';
    
    // Generate labels for each size based on quantity
    data.sizes.forEach(sizeObj => {
        for (let i = 0; i < sizeObj.quantity; i++) {
            labelIndex++;
            const labelData = { ...data, size: sizeObj.size };
            labelsHTML += `
                <div class="label-container">
                    <div class="label-header-info">
                        <h4>Етикет ${labelIndex} от ${totalLabels} - Размер: ${sizeObj.size} (${i + 1} от ${sizeObj.quantity})</h4>
                    </div>
                    ${generateSingleLabelHTML(labelData)}
                </div>
            `;
        }
    });
    
    return labelsHTML;
}

function generateSingleLabelHTML(data) {
    // Create materials list with smart column layout
    let materialsHTML = '';
    
    if (data.materials.length <= 3) {
        // Single column for 1-3 materials
        materialsHTML = `<div class="materials-single-column">
            ${data.materials.map(material => 
                `<div class="material-item">${material.percentage}% ${material.type.toUpperCase()}</div>`
            ).join('')}
        </div>`;
    } else {
        // Two columns for 4+ materials
        const midpoint = Math.ceil(data.materials.length / 2);
        const leftColumn = data.materials.slice(0, midpoint);
        const rightColumn = data.materials.slice(midpoint);
        
        materialsHTML = `<div class="materials-two-columns">
            <div class="materials-column-left">
                ${leftColumn.map(material => 
                    `<div class="material-item">${material.percentage}% ${material.type.toUpperCase()}</div>`
                ).join('')}
            </div>
            <div class="materials-column-right">
                ${rightColumn.map(material => 
                    `<div class="material-item">${material.percentage}% ${material.type.toUpperCase()}</div>`
                ).join('')}
            </div>
        </div>`;
    }
    
    // Create price display with auto-sizing
    let priceHTML = '';
    let sizeClass = '';
    
    // Calculate total price text length for auto-sizing
    let totalPriceLength = 0;
    if (data.priceEur && data.priceBgn) {
        totalPriceLength = `${data.priceBgn} лв | ${data.priceEur}€`.length;
    } else if (data.priceEur) {
        totalPriceLength = `${data.priceEur}€`.length;
    } else if (data.priceBgn) {
        totalPriceLength = `${data.priceBgn} лв`.length;
    }
    
    // Apply size class based on length
    if (totalPriceLength > 15) {
        sizeClass = 'very-long-price';
    } else if (totalPriceLength > 10) {
        sizeClass = 'long-price';
    }
    
    if (data.priceEur && data.priceBgn) {
        priceHTML = `<div class="price-container ${sizeClass}">
            <div class="price-label">Цена:</div>
            <div class="price-dual">
                <span class="price-bgn">${data.priceBgn} лв</span>
                <span class="price-separator">|</span>
                <span class="price-eur">${data.priceEur}€</span>
            </div>
        </div>`;
    } else if (data.priceEur) {
        priceHTML = `<div class="price-container ${sizeClass}">
            <div class="price-label">Цена:</div>
            <div class="price-single">${data.priceEur}€</div>
        </div>`;
    } else if (data.priceBgn) {
        priceHTML = `<div class="price-container ${sizeClass}">
            <div class="price-label">Цена:</div>
            <div class="price-single">${data.priceBgn} лв</div>
        </div>`;
    }
    
    return `
        <div class="garment-label">
            <div class="label-header">
                <div class="size-badge">Размер ${data.size}</div>
            </div>
            
            <div class="label-section origin-section">
                <div class="section-title">ПРОИЗХОД</div>
                <div class="section-content">${data.origin.toUpperCase()}</div>
            </div>
            
            <div class="label-section manufacturer-section">
                <div class="section-title">ПРОИЗВОДИТЕЛ</div>
                <div class="section-content manufacturer-name">${data.manufacturer}</div>
            </div>
            
            <div class="label-section importer-section">
                <div class="section-title">ВНОСИТЕЛ</div>
                <div class="section-content importer-name">${data.importer}</div>
            </div>
            
            <div class="label-section product-section">
                <div class="section-title">АРТИКУЛ</div>
                <div class="section-content">${data.product}</div>
            </div>
            
            <div class="label-section materials-section">
                <div class="section-title">СЪСТАВ</div>
                <div class="materials-list">
                    ${materialsHTML}
                </div>
            </div>
            
            <div class="label-footer">
                ${priceHTML}
            </div>
        </div>
    `;
}

function previewLabel() {
    const validation = validateForm();
    
    if (!validation.isValid) {
        showValidationErrors(validation.errors);
        return;
    }
    
    // Hide any previous errors
    hideValidationErrors();
    
    const labelsHTML = generateMultipleLabelsHTML(validation.data);
    
    // Show preview
    labelPreview.innerHTML = labelsHTML;
    previewArea.style.display = 'block';
    
    // Scroll to preview
    previewArea.scrollIntoView({ behavior: 'smooth' });
}


function handlePrint() {
    const validation = validateForm();
    
    if (!validation.isValid) {
        showValidationErrors(validation.errors);
        return;
    }
    
    // Hide any previous errors
    hideValidationErrors();
    
    // Call Android bridge for printing
    if (typeof Android !== 'undefined' && Android.printLabel) {
        // Convert sizes array to Android-compatible format (flatten quantities)
        const androidData = { ...validation.data };
        androidData.sizes = [];
        
        validation.data.sizes.forEach(sizeObj => {
            for (let i = 0; i < sizeObj.quantity; i++) {
                androidData.sizes.push(sizeObj.size);
            }
        });
        
        Android.printLabel(JSON.stringify(androidData));
    } else {
        // Fallback for web testing
        previewLabel();
    }
}


// Utility function for Android bridge (when integrated)
function printLabel(data) {
    if (typeof Android !== 'undefined' && Android.printLabel) {
        // Called from Android WebView
        Android.printLabel(JSON.stringify(data));
    } else {
        // Called from regular browser - show preview instead
        previewLabel();
    }
}

// Export for Android bridge
window.printLabelData = function() {
    const validation = validateForm();
    if (validation.isValid) {
        // Convert sizes array to Android-compatible format (flatten quantities)
        const androidData = { ...validation.data };
        androidData.sizes = [];
        
        validation.data.sizes.forEach(sizeObj => {
            for (let i = 0; i < sizeObj.quantity; i++) {
                androidData.sizes.push(sizeObj.size);
            }
        });
        
        return JSON.stringify(androidData);
    }
    return null;
};

// Improved Error Display Functions
function showValidationErrors(errors) {
    const errorMessages = document.getElementById('errorMessages');
    const errorList = document.getElementById('errorList');
    
    // Clear previous errors
    errorList.innerHTML = '';
    clearFieldErrors();
    
    // Add each error to the list
    errors.forEach(error => {
        const li = document.createElement('li');
        li.textContent = error;
        errorList.appendChild(li);
    });
    
    // Show error container
    errorMessages.style.display = 'block';
    
    // Highlight fields with errors
    highlightErrorFields(errors);
    
    // Scroll to error messages
    errorMessages.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function hideValidationErrors() {
    const errorMessages = document.getElementById('errorMessages');
    errorMessages.style.display = 'none';
    clearFieldErrors();
}

function clearFieldErrors() {
    // Remove error classes from all field groups
    const fieldGroups = document.querySelectorAll('.field-group');
    fieldGroups.forEach(group => {
        group.classList.remove('field-error');
    });
}

function highlightErrorFields(errors) {
    // Map error messages to field IDs for highlighting
    const fieldMappings = {
        'Артикулът е задължителен': 'product',
        'Произходът е задължителен': 'origin',
        'Производителят е задължителен': 'manufacturer',
        'Вносителят е задължителен': 'importer',
        'Поне един размер е задължителен': 'sizesContainer',
        'Поне един материал е задължителен': 'materialsContainer',
        'Цената в BGN е задължителна': 'priceBgn'
    };
    
    errors.forEach(error => {
        // Check for direct field mappings
        const fieldId = fieldMappings[error];
        if (fieldId) {
            const field = document.getElementById(fieldId);
            if (field) {
                const fieldGroup = field.closest('.field-group');
                if (fieldGroup) {
                    fieldGroup.classList.add('field-error');
                }
            }
        }
        
        // Check for percentage errors (materials)
        if (error.includes('Процентите на материалите') || error.includes('материал')) {
            const materialsContainer = document.getElementById('materialsContainer');
            if (materialsContainer) {
                const fieldGroup = materialsContainer.closest('.field-group');
                if (fieldGroup) {
                    fieldGroup.classList.add('field-error');
                }
            }
        }
        
        // Check for price errors
        if (error.includes('цена') || error.includes('EUR') || error.includes('BGN')) {
            const priceEur = document.getElementById('priceEur');
            const priceBgn = document.getElementById('priceBgn');
            if (priceEur) {
                const fieldGroup = priceEur.closest('.field-group');
                if (fieldGroup) fieldGroup.classList.add('field-error');
            }
            if (priceBgn) {
                const fieldGroup = priceBgn.closest('.field-group');
                if (fieldGroup) fieldGroup.classList.add('field-error');
            }
        }
    });
}

// Clear errors when user starts typing/selecting
document.addEventListener('DOMContentLoaded', function() {
    // Add event listeners to clear errors when user interacts with fields
    const inputs = document.querySelectorAll('input, select');
    inputs.forEach(input => {
        input.addEventListener('input', hideValidationErrors);
        input.addEventListener('change', hideValidationErrors);
    });
});

