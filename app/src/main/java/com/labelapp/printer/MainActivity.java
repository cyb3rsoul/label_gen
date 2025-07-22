package com.labelapp.printer;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.brother.sdk.lmprinter.Channel;
import com.brother.sdk.lmprinter.PrinterDriver;
import com.brother.sdk.lmprinter.PrinterDriverGenerator;
import com.brother.sdk.lmprinter.PrinterDriverGenerateResult;
import com.brother.sdk.lmprinter.setting.QLPrintSettings;
import com.brother.sdk.lmprinter.PrinterModel;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Data classes for label generation
class Material {
    final int percentage;
    final String type;

    Material(int percentage, String type) {
        this.percentage = percentage;
        this.type = type;
    }
}

class LabelData {
    final String size;
    final String product;
    final String origin;
    final List<Material> materials;
    final String manufacturer;
    final String importer;
    final String priceEur;
    final String priceBgn;

    LabelData(JSONObject json) throws JSONException {
        this.size = json.optString("size", "");
        this.product = json.optString("product", "");
        this.origin = json.optString("origin", "").toUpperCase();
        this.manufacturer = json.optString("manufacturer", "");
        this.importer = json.optString("importer", "");
        this.priceEur = json.optString("priceEur", null);
        this.priceBgn = json.optString("priceBgn", null);

        this.materials = new ArrayList<>();
        JSONArray materialsArray = json.optJSONArray("materials");
        if (materialsArray != null) {
            for (int i = 0; i < materialsArray.length(); i++) {
                JSONObject matJson = materialsArray.getJSONObject(i);
                materials.add(new Material(matJson.optInt("percentage"), matJson.optString("type")));
            }
        }
    }
}

/**
 * A dedicated class to handle the drawing of a garment label onto a Canvas.
 * This class translates the provided CSS styles and HTML structure into Android Canvas drawing commands.
 * It dynamically calculates the height of the label based on its content.
 */
class LabelDrawer {

    // --- Drawing Constants ---
    // Target dimensions: Narrow width × Tall height (matches preview exactly)
    // Canvas dimensions: Same as HTML preview - narrow and tall
    private static final float CSS_WIDTH = 234f;
    private static final float TARGET_WIDTH = 234f; // Back to original 20mm width
    private static final float MIN_HEIGHT = 200f; // Reverted back to original
    private static final float SCALE = 1.0f; // Logical scale
    private static final float BITMAP_SCALE = 3.0f; // Higher resolution for crisp text

    private static final float LABEL_WIDTH = TARGET_WIDTH;
    private static final float PADDING = 10f * SCALE; // Reverted back to original
    private static final float CONTENT_WIDTH = LABEL_WIDTH - (PADDING * 2);
    private static final float MAIN_BORDER_STROKE = 3f * SCALE;
    private static final float CORNER_RADIUS = 4f * SCALE;

    // --- Paint Objects ---
    private final Paint borderPaint, sizeBadgeFillPaint, sectionFillPaint, thinLinePaint, thickLinePaint;
    private final TextPaint sizeBadgeTextPaint, sectionTitlePaint, sectionContentPaint, materialItemPaint, manufacturerNamePaint, importerNamePaint, priceLabelPaint, priceValuePaint, priceSeparatorPaint;

    /**
     * Constructor initializes all Paint objects needed for drawing.
     * This is efficient as they are created only once.
     */
    public LabelDrawer() {
        // --- Regular Paints ---
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStrokeWidth(MAIN_BORDER_STROKE);
        borderPaint.setAntiAlias(true);

        sizeBadgeFillPaint = new Paint();
        sizeBadgeFillPaint.setStyle(Paint.Style.FILL);
        sizeBadgeFillPaint.setColor(Color.BLACK);
        sizeBadgeFillPaint.setAntiAlias(true);

        sectionFillPaint = new Paint();
        sectionFillPaint.setStyle(Paint.Style.FILL);
        sectionFillPaint.setAntiAlias(true);

        thinLinePaint = new Paint();
        thinLinePaint.setStrokeWidth(1f * SCALE);
        thinLinePaint.setAntiAlias(true);

        thickLinePaint = new Paint();
        thickLinePaint.setColor(Color.BLACK);
        thickLinePaint.setStrokeWidth(2f * SCALE);
        thickLinePaint.setAntiAlias(true);

        // materialsLeftBorderPaint removed - no longer needed

        // --- Text Paints (all sizes increased by 15%) ---
        sizeBadgeTextPaint = createTextPaint(15.18f, Color.WHITE, true, 0.03f); // Increased by 20% from 12.65f
        sizeBadgeTextPaint.setTextAlign(Paint.Align.CENTER);

        sectionTitlePaint = createTextPaint(12.075f, Color.rgb(102, 102, 102), true, 0.03f); // 8.05f * 1.5
        sectionContentPaint = createTextPaint(18f, Color.BLACK, true, 0.02f); // Increased from 15.525f for better visibility
        materialItemPaint = createTextPaint(13.8f, Color.BLACK, true, 0f); // 9.2f * 1.5
        manufacturerNamePaint = createTextPaint(15f, Color.BLACK, true, 0f); // Increased from 12.075f for better visibility
        importerNamePaint = createTextPaint(15f, Color.BLACK, true, 0f); // Increased from 12.075f for better visibility

        priceLabelPaint = createTextPaint(22f, Color.rgb(102, 102, 102), true, 0.03f); // Increased for better visibility
        priceValuePaint = createTextPaint(45f, Color.BLACK, true, 0f); // Increased significantly for prominence
        priceSeparatorPaint = createTextPaint(42f, Color.rgb(102, 102, 102), true, 0f); // Increased to match
    }

    /**
     * Helper to create a configured TextPaint object.
     */
    private TextPaint createTextPaint(float textSize, int color, boolean isBold, float letterSpacing) {
        TextPaint paint = new TextPaint();
        paint.setColor(color);
        paint.setTextSize(textSize * SCALE);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, isBold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        paint.setFilterBitmap(true);
        paint.setLetterSpacing(letterSpacing);
        return paint;
    }

    /**
     * The main entry point for creating the label bitmap.
     * It orchestrates the entire drawing process.
     *
     * @param data The parsed label data.
     * @return A bitmap of the generated label, perfectly cropped to its content.
     */
    public Bitmap createLabelBitmap(LabelData data) {
        // Create a high-resolution bitmap for crisp text
        int bitmapWidth = (int) (LABEL_WIDTH * BITMAP_SCALE);
        int bitmapHeight = (int) (1200 * BITMAP_SCALE);
        Bitmap initialBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(initialBitmap);
        canvas.drawColor(Color.WHITE);
        
        // Scale the canvas for high-resolution drawing
        canvas.scale(BITMAP_SCALE, BITMAP_SCALE);

        // --- Draw all sections and track the vertical position ---
        float currentY = PADDING;
        currentY = drawHeader(canvas, data, currentY);
        currentY = drawOriginSection(canvas, data, currentY);
        currentY = drawManufacturerSection(canvas, data, currentY);
        currentY = drawImporterSection(canvas, data, currentY);
        currentY = drawProductSection(canvas, data, currentY);
        currentY = drawMaterialsSection(canvas, data, currentY);
        
        // Calculate content height WITHOUT price to position price at bottom
        float contentEndY = currentY;
        
        // Calculate price section height
        float priceHeight = calculatePriceHeight(data);
        
        // Position price closer to content, ignore MIN_HEIGHT constraint
        float priceStartY = contentEndY + (4f * SCALE);
        currentY = drawPriceSection(canvas, data, priceStartY);

        // Calculate final height based on content, ensuring proper padding
        float contentHeight = currentY + PADDING + 2f; // Reverted back to original
        float finalHeight = Math.max(contentHeight, MIN_HEIGHT);

        // Draw the outer border on the initial bitmap
        RectF finalLabelRect = new RectF(
            MAIN_BORDER_STROKE / 2,
            MAIN_BORDER_STROKE / 2,
            LABEL_WIDTH - MAIN_BORDER_STROKE / 2,
            finalHeight - MAIN_BORDER_STROKE / 2
        );
        canvas.drawRoundRect(finalLabelRect, CORNER_RADIUS, CORNER_RADIUS, borderPaint);

        // Crop the bitmap to the calculated height (with high resolution)
        int finalBitmapWidth = (int) (LABEL_WIDTH * BITMAP_SCALE);
        int finalBitmapHeight = (int) (finalHeight * BITMAP_SCALE);
        return Bitmap.createBitmap(initialBitmap, 0, 0, finalBitmapWidth, finalBitmapHeight);
    }

    private float drawHeader(Canvas canvas, LabelData data, float startY) {
        float currentY = startY;
        String sizeText = "Размер " + data.size; // Add "Размер" prefix

        // Measure size text to get its height for vertical alignment.
        Rect sizeBounds = new Rect();
        sizeBadgeTextPaint.getTextBounds(sizeText, 0, sizeText.length(), sizeBounds);
        float sizeTextHeight = sizeBounds.height();

        // --- Draw Size Badge (centered) ---
        float sizeBadgePaddingX = 6f * SCALE;
        float sizeBadgePaddingY = 2f * SCALE;
        float sizeTextWidth = sizeBadgeTextPaint.measureText(sizeText);
        float badgeWidth = sizeTextWidth + (sizeBadgePaddingX * 2);
        float badgeHeight = (sizeBadgeTextPaint.descent() - sizeBadgeTextPaint.ascent()) + (sizeBadgePaddingY * 2);

        RectF badgeRect = new RectF(
            (LABEL_WIDTH - badgeWidth) / 2, // Center horizontally
            currentY,
            (LABEL_WIDTH + badgeWidth) / 2,
            currentY + badgeHeight
        );
        canvas.drawRoundRect(badgeRect, 3f * SCALE, 3f * SCALE, sizeBadgeFillPaint);

        // Draw centered text inside the badge.
        float textX = badgeRect.centerX();
        float textY = badgeRect.centerY() - ((sizeBadgeTextPaint.descent() + sizeBadgeTextPaint.ascent()) / 2);
        canvas.drawText(sizeText, textX, textY, sizeBadgeTextPaint);

        // Update Y position past the size badge + margins & border.
        currentY += badgeHeight + (6f * SCALE); // padding-bottom
        canvas.drawLine(PADDING, currentY, LABEL_WIDTH - PADDING, currentY, thickLinePaint);
        currentY += 4f * SCALE; // margin-bottom (reduced by 50%)

        return currentY;
    }

    private float drawProductSection(Canvas canvas, LabelData data, float startY) {
        float currentY = startY + (2f * SCALE); // section padding-top (reduced by 50%)
        float sectionPadding = 6f * SCALE; // Reverted back to original
        String title = "АРТИКУЛ";
        String content = data.product;

        float titleHeight = getTextHeight(title, sectionTitlePaint);
        float contentHeight = getTextHeight(content, sectionContentPaint);
        float sectionHeight = titleHeight + contentHeight + (sectionPadding * 2) + (2f * SCALE); // 2f = title margin-bottom

        RectF sectionRect = new RectF(PADDING, currentY, LABEL_WIDTH - PADDING, currentY + sectionHeight);
        // Remove background rectangles - keep only the rect for positioning
        // sectionFillPaint.setColor(Color.rgb(255, 255, 255));
        // thinLinePaint.setColor(Color.rgb(221, 221, 221));
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, sectionFillPaint);
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, thinLinePaint);

        float textY = currentY + sectionPadding;
        drawCenteredText(canvas, title, sectionRect.centerX(), textY, sectionTitlePaint);
        textY += titleHeight + (2f * SCALE);
        drawCenteredText(canvas, content, sectionRect.centerX(), textY, sectionContentPaint);

        return sectionRect.bottom + (2f * SCALE); // margin-bottom (reduced further to save space)
    }

    private float drawOriginSection(Canvas canvas, LabelData data, float startY) {
        float currentY = startY + (2f * SCALE); // section padding-top (reduced by 50%)
        float sectionPadding = 6f * SCALE; // Reverted back to original
        String title = "ПРОИЗХОД";
        String content = data.origin;

        float titleHeight = getTextHeight(title, sectionTitlePaint);
        float contentHeight = getTextHeight(content, sectionContentPaint);
        float sectionHeight = titleHeight + contentHeight + (sectionPadding * 2) + (2f * SCALE); // 2f = title margin-bottom

        RectF sectionRect = new RectF(PADDING, currentY, LABEL_WIDTH - PADDING, currentY + sectionHeight);
        // Remove background rectangles - keep only the rect for positioning
        // sectionFillPaint.setColor(Color.rgb(255, 255, 255));
        // thinLinePaint.setColor(Color.rgb(221, 221, 221));
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, sectionFillPaint);
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, thinLinePaint);

        float textY = currentY + sectionPadding;
        canvas.drawText(title, sectionRect.left + sectionPadding, textY - sectionTitlePaint.ascent(), sectionTitlePaint);
        textY += titleHeight + (2f * SCALE);
        canvas.drawText(content, sectionRect.left + sectionPadding, textY - sectionContentPaint.ascent(), sectionContentPaint);

        return sectionRect.bottom + (2f * SCALE); // margin-bottom (reduced further to save space)
    }

    private float drawMaterialsSection(Canvas canvas, LabelData data, float startY) {
        float currentY = startY + (2f * SCALE); // section padding-top (reduced by 50%)
        float fullLeftOffset = PADDING; // No left padding since we removed the border

        // Draw separating line above Contents section (like the one below)
        canvas.drawLine(PADDING, currentY, LABEL_WIDTH - PADDING, currentY, thickLinePaint);
        currentY += 4f * SCALE; // margin-bottom after line

        // Draw title
        String title = "СЪСТАВ";
        float titleHeight = getTextHeight(title, sectionTitlePaint);
        canvas.drawText(title, fullLeftOffset, currentY - sectionTitlePaint.ascent(), sectionTitlePaint);
        currentY += titleHeight + (2f * SCALE); // margin-bottom

        float materialTextHeight = getTextHeight("Test", materialItemPaint);
        float lineSpacing = materialTextHeight + (5f * SCALE); // text height + larger gap for much better readability

        if (data.materials.size() <= 3) {
            // Single column
            for (Material material : data.materials) {
                String text = String.format(Locale.US, "%d%% %s", material.percentage, material.type.toUpperCase());
                canvas.drawText(text, fullLeftOffset, currentY - materialItemPaint.ascent(), materialItemPaint);
                currentY += lineSpacing;
            }
        } else {
            // Two columns
            int midpoint = (int) Math.ceil(data.materials.size() / 2.0);
            List<Material> leftColumn = data.materials.subList(0, midpoint);
            List<Material> rightColumn = data.materials.subList(midpoint, data.materials.size());

            float gap = 8f * SCALE;
            float columnWidth = (CONTENT_WIDTH - gap) / 2;
            float rightColumnX = fullLeftOffset + columnWidth + gap;
            float dividerX = rightColumnX - (gap / 2);

            float yLeft = currentY;
            float yRight = currentY;

            for (Material material : leftColumn) {
                String text = String.format(Locale.US, "%d%% %s", material.percentage, material.type.toUpperCase());
                canvas.drawText(text, fullLeftOffset, yLeft - materialItemPaint.ascent(), materialItemPaint);
                yLeft += lineSpacing;
            }
            for (Material material : rightColumn) {
                String text = String.format(Locale.US, "%d%% %s", material.percentage, material.type.toUpperCase());
                canvas.drawText(text, rightColumnX, yRight - materialItemPaint.ascent(), materialItemPaint);
                yRight += lineSpacing;
            }
            currentY = Math.max(yLeft, yRight);

            // Draw column divider
            thinLinePaint.setColor(Color.rgb(221, 221, 221));
            canvas.drawLine(dividerX, startY + (4f * SCALE), dividerX, currentY - lineSpacing, thinLinePaint);
        }

        return currentY + (2f * SCALE); // margin-bottom (reduced further to save space)
    }

    private float drawManufacturerSection(Canvas canvas, LabelData data, float startY) {
        float currentY = startY + (2f * SCALE); // section padding-top (reduced by 50%)
        float sectionPadding = 6f * SCALE; // Reverted back to original
        String title = "ПРОИЗВОДИТЕЛ";
        String content = data.manufacturer;

        float titleHeight = getTextHeight(title, sectionTitlePaint);
        float contentHeight = getTextHeight(content, manufacturerNamePaint);
        float sectionHeight = titleHeight + contentHeight + (sectionPadding * 2) + (2f * SCALE); // 2f = title margin-bottom

        RectF sectionRect = new RectF(PADDING, currentY, LABEL_WIDTH - PADDING, currentY + sectionHeight);
        // Remove background rectangles - keep only the rect for positioning
        // sectionFillPaint.setColor(Color.rgb(255, 255, 255));
        // thinLinePaint.setColor(Color.rgb(204, 204, 204));
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, sectionFillPaint);
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, thinLinePaint);

        float textY = currentY + sectionPadding;
        canvas.drawText(title, sectionRect.left + sectionPadding, textY - sectionTitlePaint.ascent(), sectionTitlePaint);
        textY += titleHeight + (2f * SCALE);
        canvas.drawText(content, sectionRect.left + sectionPadding, textY - manufacturerNamePaint.ascent(), manufacturerNamePaint);

        return sectionRect.bottom + (2f * SCALE); // margin-bottom (reduced further to save space)
    }

    private float drawImporterSection(Canvas canvas, LabelData data, float startY) {
        float currentY = startY + (2f * SCALE); // section padding-top (reduced by 50%)
        float sectionPadding = 6f * SCALE; // Reverted back to original
        String title = "ВНОСИТЕЛ";
        String content = data.importer;

        float titleHeight = getTextHeight(title, sectionTitlePaint);
        float contentHeight = getTextHeight(content, importerNamePaint);
        float sectionHeight = titleHeight + contentHeight + (sectionPadding * 2) + (2f * SCALE); // 2f = title margin-bottom

        RectF sectionRect = new RectF(PADDING, currentY, LABEL_WIDTH - PADDING, currentY + sectionHeight);
        // Remove background rectangles - keep only the rect for positioning
        // sectionFillPaint.setColor(Color.rgb(255, 255, 255));
        // thinLinePaint.setColor(Color.rgb(204, 204, 204));
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, sectionFillPaint);
        // canvas.drawRoundRect(sectionRect, 3f * SCALE, 3f * SCALE, thinLinePaint);

        float textY = currentY + sectionPadding;
        canvas.drawText(title, sectionRect.left + sectionPadding, textY - sectionTitlePaint.ascent(), sectionTitlePaint);
        textY += titleHeight + (2f * SCALE);
        canvas.drawText(content, sectionRect.left + sectionPadding, textY - importerNamePaint.ascent(), importerNamePaint);

        return sectionRect.bottom + (2f * SCALE); // margin-bottom (reduced further to save space)
    }

    private float calculatePriceHeight(LabelData data) {
        if (data.priceEur == null && data.priceBgn == null) {
            return 0f; // No price section
        }
        
        // Calculate two-row layout: "Цена:" + gap + price values + padding + border
        float priceLabelHeight = getTextHeight("Цена:", priceLabelPaint);
        float priceValueHeight = getTextHeight("Test", priceValuePaint);
        float lineGap = 4f * SCALE;
        return priceLabelHeight + lineGap + priceValueHeight + (7f * SCALE); // 2f top padding + 4f border spacing + 1f bottom padding
    }
    
    private float drawPriceSection(Canvas canvas, LabelData data, float startY) {
        float currentY = startY;

        // Draw top border
        currentY += 1f * SCALE; // half the padding-top
        canvas.drawLine(PADDING, currentY, LABEL_WIDTH - PADDING, currentY, thickLinePaint);
        currentY += 1f * SCALE; // half the margin-top

        // --- Price Logic ---
        if (data.priceEur == null && data.priceBgn == null) {
            return currentY; // No price to draw
        }

        // 1. Start with default large size
        priceValuePaint.setTextSize(45f * SCALE);
        priceSeparatorPaint.setTextSize(42f * SCALE);

        // 2. Calculate actual text width and reduce size if too wide
        float availableWidth = LABEL_WIDTH - (6f * SCALE); // 6px safety margin
        String testText = "";
        if (data.priceEur != null && data.priceBgn != null) {
            testText = data.priceBgn + " лв | " + data.priceEur + "€";
        } else if (data.priceEur != null) {
            testText = data.priceEur + "€";
        } else if (data.priceBgn != null) {
            testText = data.priceBgn + " лв";
        }
        
        float testWidth = priceValuePaint.measureText(testText);
        if (testWidth > availableWidth) {
            // Too wide, reduce to medium size
            priceValuePaint.setTextSize(35f * SCALE);
            priceSeparatorPaint.setTextSize(32f * SCALE);
            testWidth = priceValuePaint.measureText(testText);
            
            if (testWidth > availableWidth) {
                // Still too wide, reduce to small size
                priceValuePaint.setTextSize(25f * SCALE);
                priceSeparatorPaint.setTextSize(23f * SCALE);
                testWidth = priceValuePaint.measureText(testText);
                
                if (testWidth > availableWidth) {
                    // Still too wide, reduce to very small size
                    priceValuePaint.setTextSize(18f * SCALE);
                    priceSeparatorPaint.setTextSize(16f * SCALE);
                }
            }
        }

        String priceLabelText = "Цена:";
        float priceGap = 6f * SCALE;
        float lineGap = 4f * SCALE; // Gap between label and price lines

        // First row: "Цена:" centered
        float priceLabelY = currentY + 2f * SCALE; // Minimal top padding
        drawCenteredText(canvas, priceLabelText, LABEL_WIDTH / 2, priceLabelY, priceLabelPaint);

        // Second row: Prices centered  
        float priceContentY = priceLabelY + lineGap + getTextHeight("Test", priceLabelPaint) + getTextHeight("Test", priceValuePaint);

        // 3. Draw based on which prices are available
        if (data.priceEur != null && data.priceBgn != null) {
            String bgnText = data.priceBgn + " лв"; // BGN first
            String eurText = data.priceEur + "€"; // Euro symbol after number
            String sepText = "|";
            float bgnWidth = priceValuePaint.measureText(bgnText);
            float eurWidth = priceValuePaint.measureText(eurText);
            float sepWidth = priceSeparatorPaint.measureText(sepText);

            float totalPriceWidth = bgnWidth + eurWidth + sepWidth + (priceGap * 2);
            float pricePadding = 3f * SCALE; // Safety padding from borders
            float remainingWidth = LABEL_WIDTH - (pricePadding * 2);
            float currentX = pricePadding + (remainingWidth - totalPriceWidth) / 2;

            canvas.drawText(bgnText, currentX, priceContentY, priceValuePaint);
            currentX += bgnWidth + priceGap;
            canvas.drawText(sepText, currentX, priceContentY, priceSeparatorPaint);
            currentX += sepWidth + priceGap;
            canvas.drawText(eurText, currentX, priceContentY, priceValuePaint);

        } else {
            String priceText = (data.priceEur != null) ? data.priceEur + "€" : data.priceBgn + " лв"; // Euro symbol after number
            float priceWidth = priceValuePaint.measureText(priceText);
            float pricePadding = 3f * SCALE; // Safety padding from borders
            float remainingWidth = LABEL_WIDTH - (pricePadding * 2);
            float currentX = pricePadding + (remainingWidth - priceWidth) / 2;

            canvas.drawText(priceText, currentX, priceContentY, priceValuePaint);
        }
        return priceContentY + 1f; // Add 1 pixel padding after price
    }

    // --- Utility Helpers ---
    private float getTextHeight(String text, Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.height();
    }

    private void drawCenteredText(Canvas canvas, String text, float centerX, float topY, TextPaint paint) {
        float textX = centerX - (paint.measureText(text) / 2);
        canvas.drawText(text, textX, topY - paint.ascent(), paint);
    }
}

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private PrinterManager printerManager;
    private String pendingPrintData = null;
    private LabelDrawer labelDrawer;
    private HistoryManager historyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        printerManager = PrinterManager.getInstance(this);
        labelDrawer = new LabelDrawer();
        historyManager = HistoryManager.getInstance(this);
        
        printerManager.setCallback(new PrinterManager.PrinterCallback() {
            @Override
            public void onStateChanged(PrinterManager.PrinterState state, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    
                    // Notify JavaScript about status change
                    if (webView != null) {
                        webView.evaluateJavascript("if (typeof onPrinterStatusChanged === 'function') onPrinterStatusChanged('" + 
                            message.replace("'", "\\'") + "');", null);
                    }
                });
            }
            
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (pendingPrintData != null) {
                        Toast.makeText(MainActivity.this, "Принтерът е свързан! Започва печат...", Toast.LENGTH_SHORT).show();
                        
                        Channel printerChannel = printerManager.getPrinterChannel();
                        if (printerChannel != null) {
                            generateAndPrintLabels(pendingPrintData, printerChannel);
                        }
                        
                        pendingPrintData = null;
                    } else {
                        // Notify JavaScript that printer is ready
                        if (webView != null) {
                            webView.evaluateJavascript("if (typeof onPrinterReady === 'function') onPrinterReady();", null);
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Грешка в принтера: " + error, Toast.LENGTH_LONG).show();
                    
                    // Notify JavaScript about error
                    if (webView != null) {
                        webView.evaluateJavascript("if (typeof onPrinterError === 'function') onPrinterError('" + 
                            error.replace("'", "\\'") + "');", null);
                    }
                    
                    if (pendingPrintData != null) {
                        pendingPrintData = null;
                    }
                });
            }
        });
        
        webView = findViewById(R.id.webView);
        setupWebView();
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                // Check printer status when page loads
                if (printerManager != null && printerManager.isPrinterReady()) {
                    // Printer is ready, notify JavaScript
                    view.evaluateJavascript("if (typeof onPrinterReady === 'function') onPrinterReady();", null);
                } else {
                    // Printer not ready, start detection and show loading
                    view.evaluateJavascript("if (typeof onPrinterConnecting === 'function') onPrinterConnecting();", null);
                    printerManager.startDetection();
                }
            }
        });
        
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void printLabel(String labelData) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Печат на етикети...", Toast.LENGTH_SHORT).show();
                
                Channel printerChannel = printerManager.getPrinterChannel();
                if (printerChannel != null) {
                    generateAndPrintLabels(labelData, printerChannel);
                } else {
                    pendingPrintData = labelData;
                    printerManager.startDetection();
                }
            });
        }
        
        @JavascriptInterface
        public boolean isPrinterReady() {
            return printerManager != null && printerManager.isPrinterReady();
        }
        
        @JavascriptInterface
        public void checkPrinterStatus() {
            runOnUiThread(() -> {
                if (printerManager != null && !printerManager.isPrinterReady()) {
                    printerManager.startDetection();
                }
            });
        }
        
        @JavascriptInterface
        public String getFilteredSuggestions(String fieldType, String userInput) {
            try {
                HistoryManager.FieldType type = HistoryManager.FieldType.valueOf(fieldType);
                List<String> suggestions = historyManager.getFilteredSuggestions(type, userInput);
                return new JSONArray(suggestions).toString();
            } catch (Exception e) {
                return "[]";
            }
        }
        
        @JavascriptInterface
        public String getAllSuggestions(String fieldType) {
            try {
                HistoryManager.FieldType type = HistoryManager.FieldType.valueOf(fieldType);
                List<String> suggestions = historyManager.getAllValues(type);
                return new JSONArray(suggestions).toString();
            } catch (Exception e) {
                return "[]";
            }
        }
        
        @JavascriptInterface
        public void saveHistoryValues(String manufacturer, String importer, String product) {
            if (manufacturer != null && !manufacturer.trim().isEmpty()) {
                historyManager.addValue(HistoryManager.FieldType.MANUFACTURER, manufacturer.trim());
            }
            if (importer != null && !importer.trim().isEmpty()) {
                historyManager.addValue(HistoryManager.FieldType.IMPORTER, importer.trim());
            }
            if (product != null && !product.trim().isEmpty()) {
                historyManager.addValue(HistoryManager.FieldType.PRODUCT, product.trim());
            }
        }
        
        @JavascriptInterface
        public void showDebugToast(String message) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void generateAndPrintLabels(String labelData, Channel printerChannel) {
        try {
            JSONObject formData = new JSONObject(labelData);
            JSONArray sizesArray = formData.getJSONArray("sizes");
            
            if (sizesArray.length() == 0) {
                Toast.makeText(this, "Няма намерени размери", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Toast.makeText(this, "Генериране на " + sizesArray.length() + " етикет(и)...", Toast.LENGTH_SHORT).show();
            processNextLabel(formData, sizesArray, 0, printerChannel);
            
        } catch (Exception e) {
            Toast.makeText(this, "Грешка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void processNextLabel(JSONObject formData, JSONArray sizesArray, int currentIndex, Channel printerChannel) {
        if (currentIndex >= sizesArray.length()) {
            Toast.makeText(this, "Всички етикети са отпечатани успешно!", Toast.LENGTH_LONG).show();
            
            // Save to history after successful printing of all labels
            try {
                String manufacturer = formData.optString("manufacturer", "");
                String importer = formData.optString("importer", "");
                String product = formData.optString("product", "");
                
                if (!manufacturer.trim().isEmpty()) {
                    historyManager.addValue(HistoryManager.FieldType.MANUFACTURER, manufacturer.trim());
                }
                if (!importer.trim().isEmpty()) {
                    historyManager.addValue(HistoryManager.FieldType.IMPORTER, importer.trim());
                }
                if (!product.trim().isEmpty()) {
                    historyManager.addValue(HistoryManager.FieldType.PRODUCT, product.trim());
                }
            } catch (Exception e) {
                // Log but don't show error to user for history saving
            }
            return;
        }
        
        try {
            String currentSize = sizesArray.getString(currentIndex);
            JSONObject labelForSize = new JSONObject(formData.toString());
            labelForSize.put("size", currentSize);
            
            Toast.makeText(this, "Печат на етикет " + (currentIndex + 1) + "/" + sizesArray.length() + " (Размер: " + currentSize + ")", Toast.LENGTH_SHORT).show();
            
            createBeautifulCanvasLabel(labelForSize, () -> {
                processNextLabel(formData, sizesArray, currentIndex + 1, printerChannel);
            }, printerChannel);
            
        } catch (Exception e) {
            Toast.makeText(this, "Грешка при създаване на етикет " + (currentIndex + 1) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private interface PrintCallback {
        void onPrintComplete();
    }
    
    private void createBeautifulCanvasLabel(JSONObject labelData, PrintCallback callback, Channel printerChannel) {
        try {
            // Parse the JSON data into our LabelData object
            LabelData data = new LabelData(labelData);
            
            // Use the new LabelDrawer to create the bitmap
            Bitmap bitmap = labelDrawer.createLabelBitmap(data);
            
            // TODO: Remove debug mode for production
            // For now, show debug bitmap - change to direct print for production
            boolean DEBUG_MODE = false; // Set to true for debugging, false for production
            
            if (DEBUG_MODE) {
                showDebugBitmap(bitmap, callback);
            } else {
                // Direct print without debug dialog
                printImageToPrinter(bitmap, printerChannel, callback);
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Грешка при създаване на етикет: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void printImageToPrinter(Bitmap bitmap, Channel printerChannel, PrintCallback callback) {
        if (printerChannel == null) {
            Toast.makeText(this, "Няма свързан принтер", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                PrinterDriverGenerateResult driverResult = PrinterDriverGenerator.openChannel(printerChannel);
                if (driverResult.getError().getCode() != com.brother.sdk.lmprinter.OpenChannelError.ErrorCode.NoError) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Неуспешно отваряне на принтера", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                PrinterDriver driver = driverResult.getDriver();
                
                QLPrintSettings printSettings = new QLPrintSettings(PrinterModel.QL_800);
                printSettings.setLabelSize(QLPrintSettings.LabelSize.RollW62);
                printSettings.setAutoCut(true);
                printSettings.setWorkPath(getCacheDir().getAbsolutePath());
                
                // High-quality thermal printer settings - ErrorDiffusion for better text quality
                printSettings.setHalftone(com.brother.sdk.lmprinter.setting.PrintImageSettings.Halftone.ErrorDiffusion);
                printSettings.setScaleMode(com.brother.sdk.lmprinter.setting.PrintImageSettings.ScaleMode.FitPageAspect);
                printSettings.setPrintOrientation(com.brother.sdk.lmprinter.setting.PrintImageSettings.Orientation.Portrait);
                printSettings.setImageRotation(com.brother.sdk.lmprinter.setting.PrintImageSettings.Rotation.Rotate270);
                printSettings.setHAlignment(com.brother.sdk.lmprinter.setting.PrintImageSettings.HorizontalAlignment.Center);
                printSettings.setVAlignment(com.brother.sdk.lmprinter.setting.PrintImageSettings.VerticalAlignment.Top);
                printSettings.setPrintQuality(com.brother.sdk.lmprinter.setting.PrintImageSettings.PrintQuality.Best);
                printSettings.setResolution(com.brother.sdk.lmprinter.setting.PrintImageSettings.Resolution.High);
                
                com.brother.sdk.lmprinter.PrintError printResult = driver.printImage(bitmap, printSettings);
                driver.closeChannel();
                
                runOnUiThread(() -> {
                    if (printResult.getCode() == com.brother.sdk.lmprinter.PrintError.ErrorCode.NoError) {
                        if (callback != null) {
                            callback.onPrintComplete();
                        }
                    } else {
                        Toast.makeText(this, "Печатът неуспешен: " + printResult.getCode(), Toast.LENGTH_LONG).show();
                    }
                    
                    // Clean up bitmap after printing
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Изключение при печат: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    
                    // Clean up bitmap on error
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                });
            }
        }).start();
    }

    private void showDebugBitmap(Bitmap bitmap, PrintCallback callback) {
        // Create ImageView to display the bitmap
        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        // Create layout with padding
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        layout.addView(imageView);
        
        // Create and show dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Debug: Generated Label Bitmap")
                .setView(layout)
                .setPositiveButton("Continue Print", (dialog, which) -> {
                    // Continue with actual printing
                    Channel printerChannel = printerManager.getPrinterChannel();
                    if (printerChannel != null) {
                        printImageToPrinter(bitmap, printerChannel, callback);
                    } else {
                        Toast.makeText(this, "Няма свързан принтер", Toast.LENGTH_SHORT).show();
                        // Clean up bitmap if printing is not possible
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Clean up bitmap on cancel
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    dialog.dismiss();
                })
                .show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up printer manager when activity is destroyed
        if (printerManager != null) {
            printerManager.cleanup();
        }
    }
}