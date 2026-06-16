package com.usbthermal.b4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * ESC/POS command builder for thermal receipt printers (80mm).
 * Generates raw byte sequences conforming to the Epson ESC/POS standard.
 *
 * Reference: Epson ESC/POS Application Programming Guide
 * Compatible with most 80mm thermal printers (Epson, Star, Bixolon, etc.)
 */
public class ESCPOSCommands {

    // ─── Printer constants ───────────────────────────────────────────────
    public static final int PAPER_WIDTH_80MM = 576;   // 80mm paper, max dots at standard DPI
    public static final int PAPER_WIDTH_76MM = 544;   // 76mm paper
    public static final int PAPER_WIDTH_72MM = 512;   // 72mm paper
    public static final int PAPER_WIDTH_56MM = 384;   // 56mm paper

    // Character counts per line (Font A, 12×24 dots, normal width)
    public static final int CHARS_80MM = 48;
    public static final int CHARS_76MM = 40;
    public static final int CHARS_72MM = 33;
    public static final int CHARS_56MM = 24;

    // Character counts per line (large font: Font A, double-width)
    public static final int CHARS_LARGE_80MM = 32;
    public static final int CHARS_LARGE_76MM = 27;
    public static final int CHARS_LARGE_72MM = 20;
    public static final int CHARS_LARGE_56MM = 16;

    // ─── Print width ─────────────────────────────────────────────────────
    private int paperWidthDots = PAPER_WIDTH_80MM;
    private int charsPerLine = CHARS_80MM;

    // ─── Alignment constants ─────────────────────────────────────────────
    public static final int ALIGN_LEFT   = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT  = 2;

    // ─── Barcode types ──────────────────────────────────────────────────
    public static final int BARCODE_UPCA   = 0;
    public static final int BARCODE_UPCE   = 1;
    public static final int BARCODE_EAN13  = 2;
    public static final int BARCODE_EAN8   = 3;
    public static final int BARCODE_CODE39 = 4;
    public static final int BARCODE_ITF    = 5;
    public static final int BARCODE_CODABAR    = 6;
    public static final int BARCODE_CODE93     = 72;
    public static final int BARCODE_CODE128    = 73;

    // ─── Cut type constants ──────────────────────────────────────────────
    public static final int CUT_FULL  = 0;  // Full cut
    public static final int CUT_PARTIAL = 1; // Partial cut (leaves one point attached)

    // ─── HRI position constants ─────────────────────────────────────────
    public static final int HRI_NOT_PRINTED   = 0;
    public static final int HRI_ABOVE         = 1;
    public static final int HRI_BELOW         = 2;
    public static final int HRI_BOTH          = 3;

    // ─── Codepage constants ─────────────────────────────────────────────
    public static final int CODEPAGE_USA          = 0;
    public static final int CODEPAGE_LATIN1       = 16;  // ISO 8859-1: Western Europe
    public static final int CODEPAGE_UTF8         = 30;  // Not standard ESC/POS, but common

    // ─── Instance state ──────────────────────────────────────────────────
    private ByteArrayOutputStream commandBuffer;

    public ESCPOSCommands() {
        this.commandBuffer = new ByteArrayOutputStream();
    }

    // =====================================================================
    //  INITIALIZATION COMMANDS
    // =====================================================================

    /**
     * Initialize printer - resets to power-on defaults.
     */
    public ESCPOSCommands initializePrinter() {
        writeBytes(new byte[]{0x1B, 0x40});  // ESC @
        return this;
    }

    // =====================================================================
    //  PAPER WIDTH CONFIGURATION
    // =====================================================================

    /**
     * Set paper width and update internal char-per-line calculations.
     * @param paperWidth PAPER_WIDTH_80MM, PAPER_WIDTH_76MM, PAPER_WIDTH_72MM, or PAPER_WIDTH_56MM
     */
    public ESCPOSCommands setPaperWidth(int paperWidth) {
        this.paperWidthDots = paperWidth;
        switch (paperWidth) {
            case PAPER_WIDTH_76MM: this.charsPerLine = CHARS_76MM; break;
            case PAPER_WIDTH_72MM: this.charsPerLine = CHARS_72MM; break;
            case PAPER_WIDTH_56MM: this.charsPerLine = CHARS_56MM; break;
            default: this.charsPerLine = CHARS_80MM; break;
        }
        return this;
    }

    /**
     * Set paper width by named string.
     * @param widthName "_80mm", "_76mm", "_72mm", or "_56mm"
     */
    public ESCPOSCommands setPaperWidthByName(String widthName) {
        if (widthName == null) return this;
        switch (widthName) {
            case "_76mm": return setPaperWidth(PAPER_WIDTH_76MM);
            case "_72mm": return setPaperWidth(PAPER_WIDTH_72MM);
            case "_56mm": return setPaperWidth(PAPER_WIDTH_56MM);
            default:      return setPaperWidth(PAPER_WIDTH_80MM);
        }
    }

    /**
     * Get current characters-per-line for normal Font A text.
     */
    public int getCharsPerLine() {
        return charsPerLine;
    }

    /**
     * Get characters-per-line for large (double-width) Font A text.
     */
    public int getCharsPerLineLarge() {
        switch (paperWidthDots) {
            case PAPER_WIDTH_76MM: return CHARS_LARGE_76MM;
            case PAPER_WIDTH_72MM: return CHARS_LARGE_72MM;
            case PAPER_WIDTH_56MM: return CHARS_LARGE_56MM;
            default: return CHARS_LARGE_80MM;
        }
    }

    // =====================================================================
    //  TEXT FORMATTING COMMANDS
    // =====================================================================

    /**
     * Set character font (0 = Font A, 1 = Font B).
     * Font A: 12x24 dots (default), Font B: 9x17 dots (smaller).
     */
    public ESCPOSCommands setFont(int fontIndex) {
        writeBytes(new byte[]{0x1B, 0x4D, (byte) fontIndex});  // ESC M n
        return this;
    }

    /**
     * Enable or disable bold text.
     */
    public ESCPOSCommands setBold(boolean enabled) {
        writeBytes(new byte[]{0x1B, 0x45, (byte) (enabled ? 1 : 0)});  // ESC E n
        return this;
    }

    /**
     * Enable or disable underline.
     * @param thickness 0 = off, 1 = 1-dot thin, 2 = 2-dot thick
     */
    public ESCPOSCommands setUnderline(int thickness) {
        writeBytes(new byte[]{0x1B, 0x2D, (byte) thickness});  // ESC - n
        return this;
    }

    /**
     * Set text alignment.
     * @param alignment 0 = left, 1 = center, 2 = right
     */
    public ESCPOSCommands setAlignment(int alignment) {
        writeBytes(new byte[]{0x1B, 0x61, (byte) alignment});  // ESC a n
        return this;
    }

    /**
     * Set character size (width and height multipliers, 1-8).
     */
    public ESCPOSCommands setCharSize(int widthMultiplier, int heightMultiplier) {
        // GS ! n:  bits 0-3 = height, bits 4-7 = width (0-based, so subtract 1)
        int n = ((widthMultiplier - 1) << 4) | (heightMultiplier - 1);
        writeBytes(new byte[]{0x1D, 0x21, (byte) n});  // GS ! n
        return this;
    }

    /**
     * Enable or disable reverse (white-on-black) printing.
     */
    public ESCPOSCommands setReverseMode(boolean enabled) {
        writeBytes(new byte[]{0x1D, 0x42, (byte) (enabled ? 1 : 0)});  // GS B n
        return this;
    }

    /**
     * Enable or disable upside-down printing.
     */
    public ESCPOSCommands setUpsideDown(boolean enabled) {
        writeBytes(new byte[]{0x1B, 0x7B, (byte) (enabled ? 1 : 0)});  // ESC { n
        return this;
    }

    /**
     * Enable or disable double-strike mode (text printed twice for emphasis).
     */
    public ESCPOSCommands setDoubleStrike(boolean enabled) {
        writeBytes(new byte[]{0x1B, 0x47, (byte) (enabled ? 1 : 0)});  // ESC G n
        return this;
    }

    /**
     * Enable or disable emphasized mode (bold-like, printer-dependent).
     */
    public ESCPOSCommands setEmphasized(boolean enabled) {
        writeBytes(new byte[]{0x1B, 0x45, (byte) (enabled ? 1 : 0)});  // ESC E n
        return this;
    }

    // =====================================================================
    //  LINE SPACING & FEED COMMANDS
    // =====================================================================

    /**
     * Set line spacing in dots. Default is ~30 dots (1/6 inch).
     * Range: 0-255 dots.
     */
    public ESCPOSCommands setLineSpacing(int dots) {
        writeBytes(new byte[]{0x1B, 0x33, (byte) dots});  // ESC 3 n
        return this;
    }

    /**
     * Feed paper by N lines (using current line spacing).
     */
    public ESCPOSCommands feedLines(int lineCount) {
        writeBytes(new byte[]{0x1B, 0x64, (byte) lineCount});  // ESC d n
        return this;
    }

    /**
     * Feed paper by N dots (ignores line spacing, precise).
     */
    public ESCPOSCommands feedDots(int dotCount) {
        writeBytes(new byte[]{0x1B, 0x4A, (byte) dotCount});  // ESC J n
        return this;
    }

    // =====================================================================
    //  PAPER CUT COMMANDS
    // =====================================================================

    /**
     * Cut paper with feed.
     * Per ESC/POS spec:
     *   GS V m      — m=0/48=full cut, m=1/49=partial cut (no feed)
     *   GS V m n    — m=65='A' feeds and partial cuts, m=66='B' feeds and full cuts
     * @param cutType CUT_FULL (0) or CUT_PARTIAL (1)
     * @param feedDotsBeforeCut feed N dots before cutting (prevents jam, ~17mm=100 dots typical)
     */
    public ESCPOSCommands cutPaper(int cutType, int feedDotsBeforeCut) {
        if (feedDotsBeforeCut > 0) {
            // GS V m n: m=65=partial, m=66=full; n = feed amount in dots
            int m = (cutType == CUT_FULL) ? 66 : 65;
            writeBytes(new byte[]{0x1D, 0x56, (byte) m, (byte) feedDotsBeforeCut});
        } else {
            // GS V m: simple cut without feed
            int m = (cutType == CUT_FULL) ? 48 : 49;
            writeBytes(new byte[]{0x1D, 0x56, (byte) m});
        }
        return this;
    }

    /**
     * Cut paper with default partial cut and 100-dot feed.
     */
    public ESCPOSCommands cutPaperPartial() {
        return cutPaper(CUT_PARTIAL, 100);
    }

    /**
     * Cut paper with full cut, 100-dot feed.
     */
    public ESCPOSCommands cutPaperFull() {
        return cutPaper(CUT_FULL, 100);
    }

    // =====================================================================
    // ─── Cash drawer type constants ──────────────────────────────────────
    public static final int DRAWER_STANDARD   = 0;  // ESC/POS: Epson, Bixolon, Generic
    public static final int DRAWER_CITIZEN    = 1;  // Citizen-specific timing
    public static final int DRAWER_STAR       = 2;  // Star Micronics (ESC BEL protocol)
    public static final int DRAWER_SUNMI      = 3;  // Sunmi/ProvaTech (DLE DC4 protocol)

    // ─── CASH DRAWER COMMANDS
    // =====================================================================

    /**
     * Pulse cash drawer kick-out connector (ESC/POS standard).
     * @param pin 0 = pin 2, 1 = pin 5
     * @param onTime pulse ON time (1-8, units of 100ms)
     * @param offTime pulse OFF time (1-8, units of 100ms)
     */
    public ESCPOSCommands pulseCashDrawer(int pin, int onTime, int offTime) {
        writeBytes(new byte[]{0x1B, 0x70, (byte) pin, (byte) onTime, (byte) offTime});  // ESC p m t1 t2
        return this;
    }

    /**
     * Open cash drawer using the specified manufacturer protocol.
     *
     * @param drawerType DRAWER_STANDARD (0), DRAWER_CITIZEN (1),
     *                   DRAWER_STAR (2), DRAWER_SUNMI (3)
     */
    public ESCPOSCommands openCashDrawer(int drawerType) {
        switch (drawerType) {
            case DRAWER_CITIZEN:
                // Citizen: ESC p 0 50 250  (pin 2, 500ms on, 2500ms off)
                writeBytes(new byte[]{0x1B, 0x70, 0x00, 0x32, (byte) 0xFA});
                break;
            case DRAWER_STAR:
                // Star Micronics: ESC BEL 0x0B 0x37 0x07  (non-standard protocol)
                writeBytes(new byte[]{0x1B, 0x07, 0x0B, 0x37, 0x07});
                break;
            case DRAWER_SUNMI:
                // Sunmi/ProvaTech: DLE DC4 0x00 0x00 0x00  (proprietary pulse)
                writeBytes(new byte[]{0x10, 0x14, 0x00, 0x00, 0x00});
                break;
            default: // STANDARD
                // Standard ESC/POS: ESC p 0 64 64  (pin 2, 200ms on, 200ms off)
                writeBytes(new byte[]{0x1B, 0x70, 0x00, 0x40, 0x40});
                break;
        }
        return this;
    }

    /**
     * Open cash drawer with standard ESC/POS settings (pin 2, 200ms on, 200ms off).
     */
    public ESCPOSCommands openCashDrawer() {
        return openCashDrawer(DRAWER_STANDARD);
    }

    // =====================================================================
    //  BARCODE COMMANDS
    // =====================================================================

    /**
     * Set barcode height in dots (default 162, ~1 inch on 203dpi printer).
     */
    public ESCPOSCommands setBarcodeHeight(int dots) {
        writeBytes(new byte[]{0x1D, 0x68, (byte) dots});  // GS h n
        return this;
    }

    /**
     * Set barcode width. n = 2-6 (thin:thick dot ratio).
     */
    public ESCPOSCommands setBarcodeWidth(int n) {
        writeBytes(new byte[]{0x1D, 0x77, (byte) n});  // GS w n
        return this;
    }

    /**
     * Set HRI (Human Readable Interpretation) character position.
     */
    public ESCPOSCommands setBarcodeHriPosition(int position) {
        writeBytes(new byte[]{0x1D, 0x48, (byte) position});  // GS H n
        return this;
    }

    /**
     * Set HRI character font (0 = Font A, 1 = Font B).
     */
    public ESCPOSCommands setBarcodeHriFont(int font) {
        writeBytes(new byte[]{0x1D, 0x66, (byte) font});  // GS f n
        return this;
    }

    /**
     * Print a barcode.
     * @param data The barcode data string
     * @param barcodeType One of the BARCODE_* constants
     */
    public ESCPOSCommands printBarcode(String data, int barcodeType) {
        byte[] dataBytes = data.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        // GS k m n d1...dk NUL
        writeByte((byte) 0x1D);  // GS
        writeByte((byte) 0x6B);  // k
        writeByte((byte) barcodeType); // m
        writeByte((byte) dataBytes.length); // n (for non-variable-length types, this is data length)
        writeBytes(dataBytes);
        writeByte((byte) 0x00);   // NUL terminator
        return this;
    }

    // =====================================================================
    //  QR CODE COMMANDS
    // =====================================================================

    /**
     * Print a QR Code (Model 2).
     * @param data The data to encode
     * @param moduleSize QR code module size (1-16, typical: 3-6)
     * @param errorCorrection 48=L(7%), 49=M(15%), 50=Q(25%), 51=H(30%)
     */
    public ESCPOSCommands printQRCode(String data, int moduleSize, int errorCorrection) {
        byte[] dataBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int payloadLen = dataBytes.length + 3;

        // Store data in symbol storage
        writeBytes(new byte[]{0x1D, 0x28, 0x6B});  // GS ( k
        writeByte((byte) payloadLen);  // pL (low byte)
        writeByte((byte) (payloadLen >> 8));  // pH (high byte)
        writeBytes(new byte[]{0x31, 0x50, 0x30});  // cn=49, fn=80, m=48 (model 2 auto)
        writeBytes(dataBytes);

        // Set module size
        writeBytes(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43});
        writeByte((byte) moduleSize);

        // Set error correction
        writeBytes(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45});
        writeByte((byte) errorCorrection);

        // Print QR code
        writeBytes(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30});

        return this;
    }

    /**
     * Print a QR Code with default settings (size=4, error correction=M).
     */
    public ESCPOSCommands printQRCodeSimple(String data) {
        return printQRCode(data, 4, 49);
    }

    // =====================================================================
    //  IMAGE / BITMAP COMMANDS
    // =====================================================================

    /**
     * Print a raster bit image in column format (NV graphics not used).
     * This is the most compatible method across printer models.
     *
     * @param imageData Raw 1-bit (monochrome) bitmap data, width bytes per row
     * @param widthPixels Width of the image in pixels
     * @param heightPixels Height of the image in pixels
     */
    public ESCPOSCommands printRasterBitImage(byte[] imageData, int widthPixels, int heightPixels) {
        int widthBytes = (widthPixels + 7) / 8;  // bytes per row
        int xL = widthBytes & 0xFF;
        int xH = (widthBytes >> 8) & 0xFF;
        int yL = heightPixels & 0xFF;
        int yH = (heightPixels >> 8) & 0xFF;

        // GS v 0 - print raster bit image, normal mode
        writeBytes(new byte[]{0x1D, 0x76, 0x30, 0x00});  // GS v 0 m=0 (normal)
        writeByte((byte) xL);
        writeByte((byte) xH);
        writeByte((byte) yL);
        writeByte((byte) yH);
        writeBytes(imageData);

        return this;
    }

    // =====================================================================
    //  STATUS & SENSOR COMMANDS
    // =====================================================================

    /**
     * Transmit real-time status.
     * @param n 1=printer status, 2=offline status, 3=error status, 4=paper roll sensor
     */
    public ESCPOSCommands requestStatus(int n) {
        writeBytes(new byte[]{0x10, 0x04, (byte) n});  // DLE EOT n
        return this;
    }

    /**
     * Transmit paper sensor status (check if paper is present).
     */
    public ESCPOSCommands requestPaperStatus() {
        return requestStatus(4);
    }

    // =====================================================================
    //  CODEPAGE / CHARACTER SET COMMANDS
    // =====================================================================

    /**
     * Set character code table (codepage).
     */
    public ESCPOSCommands setCodePage(int codePage) {
        writeBytes(new byte[]{0x1B, 0x74, (byte) codePage});  // ESC t n
        return this;
    }

    /**
     * Set international character set.
     * @param charset 0=USA, 1=France, 2=Germany, 3=UK, 4=Denmark I,
     *                5=Sweden, 6=Italy, 7=Spain I, 8=Japan, 9=Norway,
     *                10=Denmark II, 11=Spain II, 12=Latin America, 13=Korea
     */
    public ESCPOSCommands setInternationalCharset(int charset) {
        writeBytes(new byte[]{0x1B, 0x52, (byte) charset});  // ESC R n
        return this;
    }

    // =====================================================================
    //  MISC COMMANDS
    // =====================================================================

    /**
     * Enable or disable ASB (Auto Status Back) - printer sends status automatically.
     */
    public ESCPOSCommands setAutoStatusBack(boolean enabled) {
        writeBytes(new byte[]{0x1D, 0x61, (byte) (enabled ? 0xFF : 0x00)});  // GS a n
        return this;
    }

    /**
     * Generate a pulse/beep on the printer's buzzer (if equipped).
     * @param count Number of beeps (1-9)
     * @param duration Duration per beep in 50ms units (1-9)
     */
    public ESCPOSCommands beep(int count, int duration) {
        writeBytes(new byte[]{0x1B, 0x42, (byte) count, (byte) duration});  // ESC B n t (not standard on all)
        return this;
    }

    /**
     * Print and feed one line (carriage return + line feed).
     */
    public ESCPOSCommands newLine() {
        writeBytes(new byte[]{0x0A});  // LF
        return this;
    }

    /**
     * Append raw text to the print buffer.
     */
    public ESCPOSCommands appendText(String text) {
        try {
            commandBuffer.write(text.getBytes("UTF-8"));
        } catch (IOException e) {
            // ByteArrayOutputStream doesn't actually throw IOException
        }
        return this;
    }

    /**
     * Append raw text followed by a newline.
     */
    public ESCPOSCommands appendTextLine(String text) {
        appendText(text);
        return newLine();
    }

    /**
     * Append a separator/dividing line across the full 80mm width.
     */
    public ESCPOSCommands appendSeparator() {
        return appendSeparator('-', 48);
    }

    /**
     * Append a custom separator line.
     * @param ch The character to repeat
     * @param count Number of repetitions (48 = standard 80mm width for Font A)
     */
    public ESCPOSCommands appendSeparator(char ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return appendTextLine(sb.toString());
    }

    /**
     * Append a centered text line on 80mm paper.
     */
    public ESCPOSCommands appendCenteredLine(String text) {
        setAlignment(ALIGN_CENTER);
        appendTextLine(text);
        setAlignment(ALIGN_LEFT);
        return this;
    }

    // =====================================================================
    //  BUFFER MANAGEMENT
    // =====================================================================

    /**
     * Get the fully built command byte array ready for printing.
     */
    public byte[] getCommandBytes() {
        return commandBuffer.toByteArray();
    }

    /**
     * Get the current buffer length.
     */
    public int getBufferLength() {
        return commandBuffer.size();
    }

    /**
     * Clear the command buffer and reset all state.
     */
    public ESCPOSCommands reset() {
        commandBuffer.reset();
        return this;
    }

    /**
     * Convenience: reset, initialize printer, and return self.
     */
    public ESCPOSCommands startNewJob() {
        reset();
        return initializePrinter();
    }

    // =====================================================================
    //  PRIVATE HELPERS
    // =====================================================================

    private void writeByte(byte b) {
        commandBuffer.write(b);
    }

    private void writeBytes(byte[] bytes) {
        try {
            commandBuffer.write(bytes);
        } catch (IOException e) {
            // ByteArrayOutputStream does not throw
        }
    }
}
