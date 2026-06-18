package com.usbthermal.b4j;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;

/**
 * USBThermalPrinter - High-level facade for printing to USB thermal receipt
 * printers (80mm) from B4J applications.
 *
 * Handles printer selection, ESC/POS command generation, and raw byte
 * submission to the Windows print spooler.
 *
 * Usage (B4J):
 * <pre>
 *   Dim printer As USBThermalPrinter
 *   printer.Initialize("EPSON TM-T88V")
 *   printer.PrintTestPage
 *   printer.CutPaper
 * </pre>
 */
public class USBThermalPrinter {

    // ─── Dependencies ────────────────────────────────────────────────────
    private PrinterDiscovery discovery;
    private ESCPOSCommands escpos;
    private PrintService printService;
    private String currentPrinterName;
    private boolean isInitialized;

    // ─── Print job tracking ──────────────────────────────────────────────
    private boolean asyncPrinting = false;
    private boolean lastJobSuccess = false;
    private String lastJobError = "";

    // =====================================================================
    //  INITIALIZATION
    // =====================================================================

    /**
     * Default constructor. Must call Initialize() before use.
     */
    public USBThermalPrinter() {
        this.discovery = PrinterDiscovery.getInstance();
        this.escpos = new ESCPOSCommands();
        this.isInitialized = false;
        this.currentPrinterName = "";
    }

    /**
     * Initialize with a specific printer name.
     * Use GetAllPrinterNames() first to discover available printers.
     *
     * @param printerName Exact printer name from the Windows printer list
     * @return true if the printer was found and initialized successfully
     */
    public boolean initialize(String printerName) {
        if (printerName == null || printerName.trim().isEmpty()) {
            lastJobError = "Printer name is empty";
            return false;
        }

        this.currentPrinterName = printerName.trim();
        this.printService = discovery.getPrintServiceByName(this.currentPrinterName);

        if (this.printService == null) {
            lastJobError = "Printer not found: " + this.currentPrinterName;
            this.isInitialized = false;
            return false;
        }

        this.isInitialized = true;
        this.lastJobError = "";
        return true;
    }

    /**
     * Initialize with the Windows default printer.
     * @return true if a default printer exists and was initialized
     */
    public boolean initializeDefault() {
        PrinterInfo defaultPrinter = discovery.getDefaultPrinter();
        if (defaultPrinter == null) {
            lastJobError = "No default printer found";
            return false;
        }
        return initialize(defaultPrinter.getPrinterName());
    }

    /**
     * Returns the error message from the last failed operation.
     */
    public String getLastError() {
        return lastJobError;
    }

    /**
     * Returns whether the printer is initialized and ready.
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Returns the name of the currently configured printer.
     */
    public String getCurrentPrinterName() {
        return currentPrinterName;
    }

    // =====================================================================
    //  PRINTER DISCOVERY (static convenience methods)
    // =====================================================================

    /**
     * Get all installed printer names.
     */
    public static List<String> getAllPrinterNames() {
        return PrinterDiscovery.getInstance().getAllPrinterNames();
    }

    /**
     * Get default printer name.
     */
    public static String getDefaultPrinterName() {
        return PrinterDiscovery.getInstance().getDefaultPrinterName();
    }

    /**
     * Get detailed info for all printers (returns list of maps).
     */
    public static List<java.util.Map<String, Object>> getAllPrinterDetails() {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (PrinterInfo info : PrinterDiscovery.getInstance().getAllPrinters()) {
            result.add(info.toMap());
        }
        return result;
    }

    // =====================================================================
    //  PRINTING - SIMPLE TEXT
    // =====================================================================

    /**
     * Print a single text line.
     */
    public boolean printLine(String text) {
        checkInit();
        escpos.startNewJob();
        escpos.appendTextLine(text);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Print multiple lines of text.
     */
    public boolean printLines(String[] lines) {
        checkInit();
        escpos.startNewJob();
        for (String line : lines) {
            escpos.appendTextLine(line);
        }
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Print raw text (no formatting applied, caller provides line breaks).
     */
    public boolean printText(String text) {
        checkInit();
        escpos.startNewJob();
        escpos.appendText(text);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Print a centered line on 80mm paper.
     */
    public boolean printCenteredLine(String text) {
        checkInit();
        escpos.startNewJob();
        escpos.appendCenteredLine(text);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Print a horizontal separator/dividing line.
     */
    public boolean printSeparator() {
        return printSeparatorChar('-', 48);
    }

    /**
     * Print a custom separator.
     */
    public boolean printSeparatorChar(char ch, int count) {
        checkInit();
        escpos.startNewJob();
        escpos.appendSeparator(ch, count);
        return sendToPrinter(escpos.getCommandBytes());
    }

    // =====================================================================
    //  PRINTING - FORMATTED
    // =====================================================================

    /**
     * Begin a new print job in the buffer. Call this, then formatting methods,
     * then ExecuteJob() to send to the printer.
     *
     * Example:
     * <pre>
     *   printer.BeginJob()
     *   printer.SetBold(True)
     *   printer.AppendLine("BOLD TEXT")
     *   printer.SetBold(False)
     *   printer.AppendLine("normal text")
     *   printer.ExecuteJob()
     * </pre>
     */
    public void beginJob() {
        checkInit();
        escpos.startNewJob();
    }

    /**
     * Execute the currently built job - sends buffer to printer.
     */
    public boolean executeJob() {
        checkInit();
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Append text to the current job buffer (no newline).
     */
    public void appendText(String text) {
        checkInit();
        escpos.appendText(text);
    }

    /**
     * Append a line of text to the current job buffer (adds newline).
     */
    public void appendLine(String text) {
        checkInit();
        escpos.appendTextLine(text);
    }

    /**
     * Append a centered line to the current job buffer.
     */
    public void appendCenteredLine(String text) {
        checkInit();
        escpos.appendCenteredLine(text);
    }

    // =====================================================================
    //  FORMATTING COMMANDS
    // =====================================================================

    public void setBold(boolean enabled)     { checkInit(); escpos.setBold(enabled); }
    public void setUnderline(boolean enabled){ checkInit(); escpos.setUnderline(enabled ? 1 : 0); }
    public void setDoubleStrike(boolean enabled) { checkInit(); escpos.setDoubleStrike(enabled); }
    public void setReverseMode(boolean enabled)  { checkInit(); escpos.setReverseMode(enabled); }
    public void setUpsideDown(boolean enabled)   { checkInit(); escpos.setUpsideDown(enabled); }

    public void setAlignmentLeft()   { checkInit(); escpos.setAlignment(ESCPOSCommands.ALIGN_LEFT); }
    public void setAlignmentCenter() { checkInit(); escpos.setAlignment(ESCPOSCommands.ALIGN_CENTER); }
    public void setAlignmentRight()  { checkInit(); escpos.setAlignment(ESCPOSCommands.ALIGN_RIGHT); }

    /**
     * Set character size multipliers.
     * @param width  1-8 (1=normal width)
     * @param height 1-8 (1=normal height)
     */
    public void setCharSize(int width, int height) { checkInit(); escpos.setCharSize(width, height); }

    /**
     * Set font: 0 = Font A (12x24, default), 1 = Font B (9x17, smaller).
     */
    public void setFont(int fontIndex) { checkInit(); escpos.setFont(fontIndex); }

    public void setLineSpacing(int dots) { checkInit(); escpos.setLineSpacing(dots); }

    // =====================================================================
    //  PAPER CONTROL
    // =====================================================================

    /**
     * Feed paper by N lines (using current line spacing).
     */
    public boolean feedLines(int count) {
        checkInit();
        escpos.startNewJob();
        escpos.feedLines(count);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Feed paper by N dots (precise control, ignores line spacing).
     */
    public boolean feedDots(int dotCount) {
        checkInit();
        escpos.startNewJob();
        escpos.feedDots(dotCount);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Cut paper - full cut with 100-dot feed.
     */
    public boolean cutPaperFull() {
        checkInit();
        escpos.startNewJob();
        escpos.cutPaperFull();
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Cut paper - partial cut with 100-dot feed (leaves one point attached).
     */
    public boolean cutPaperPartial() {
        checkInit();
        escpos.startNewJob();
        escpos.cutPaperPartial();
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Cut paper with custom options.
     * @param fullCut true=full cut, false=partial cut
     * @param feedDotsBeforeCut dots to feed before cutting (100 = ~17mm, typical)
     */
    public boolean cutPaper(boolean fullCut, int feedDotsBeforeCut) {
        checkInit();
        int cutType = fullCut ? ESCPOSCommands.CUT_FULL : ESCPOSCommands.CUT_PARTIAL;
        escpos.startNewJob();
        escpos.cutPaper(cutType, feedDotsBeforeCut);
        return sendToPrinter(escpos.getCommandBytes());
    }

    // =====================================================================
    //  CASH DRAWER
    // =====================================================================

    // ─── Drawer type constants ──────────────────────────────────
    public static final int DRAWER_STANDARD = ESCPOSCommands.DRAWER_STANDARD;
    public static final int DRAWER_CITIZEN  = ESCPOSCommands.DRAWER_CITIZEN;
    public static final int DRAWER_STAR     = ESCPOSCommands.DRAWER_STAR;
    public static final int DRAWER_SUNMI    = ESCPOSCommands.DRAWER_SUNMI;

    /**
     * Open the cash drawer with standard ESC/POS protocol (pin 2, 200ms).
     */
    public boolean openCashDrawer() {
        checkInit();
        escpos.startNewJob();
        escpos.openCashDrawer(ESCPOSCommands.DRAWER_STANDARD);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Open cash drawer with specified manufacturer protocol.
     * @param drawerType DRAWER_STANDARD(0), DRAWER_CITIZEN(1), DRAWER_STAR(2), DRAWER_SUNMI(3)
     */
    public boolean openCashDrawer(int drawerType) {
        checkInit();
        escpos.startNewJob();
        escpos.openCashDrawer(drawerType);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Open cash drawer by manufacturer kick code value.
     * Maps: 1=Standard, 2=Citizen, 3=Star, 6=Sunmi/ProvaTech
     */
    public boolean openCashDrawerByKickCode(int drawerKickCode) {
        int type;
        switch (drawerKickCode) {
            case 2:  type = ESCPOSCommands.DRAWER_CITIZEN; break;
            case 3:  type = ESCPOSCommands.DRAWER_STAR;    break;
            case 6:  type = ESCPOSCommands.DRAWER_SUNMI;   break;
            default: type = ESCPOSCommands.DRAWER_STANDARD; break;
        }
        return openCashDrawer(type);
    }

    /**
     * Pulse cash drawer with custom ESC/POS standard timing.
     * @param pin 0=pin2, 1=pin5
     * @param onTimeMs ON time (1-8, units of 100ms)
     * @param offTimeMs OFF time (1-8, units of 100ms)
     */
    public boolean pulseCashDrawer(int pin, int onTimeMs, int offTimeMs) {
        checkInit();
        escpos.startNewJob();
        escpos.pulseCashDrawer(pin, onTimeMs, offTimeMs);
        return sendToPrinter(escpos.getCommandBytes());
    }

    // =====================================================================
    //  PAPER WIDTH
    // =====================================================================

    /**
     * Set paper width. Affects separator length, centering calculations.
     * @param paperWidthDots ESCPOSCommands.PAPER_WIDTH_80MM/76MM/72MM/56MM
     */
    public void setPaperWidth(int paperWidthDots) {
        escpos.setPaperWidth(paperWidthDots);
    }

    /**
     * Set paper width by name string.
     * @param widthName "_80mm", "_76mm", "_72mm", or "_56mm"
     */
    public void setPaperWidthByName(String widthName) {
        escpos.setPaperWidthByName(widthName);
    }

    /**
     * Get characters per line for current paper width (Font A, normal size).
     */
    public int getCharsPerLine() {
        return escpos.getCharsPerLine();
    }

    // =====================================================================
    //  RAW / COMPATIBILITY — Direct byte-level access
    // =====================================================================

    /**
     * Send raw bytes to the printer unchanged (no ESC/POS wrapping).
     * The caller is responsible for building ESC/POS commands and encoding.
     *
     * @param rawBytes The complete byte array to send to the printer
     * @return true if sent successfully
     */
    public boolean sendRawBytes(byte[] rawBytes) {
        checkInit();
        return sendToPrinter(rawBytes);
    }

    /**
     * Send a pre-built print string using UTF-8 encoding.
     * The string should contain all ESC/POS formatting commands, text, and cut commands.
     *
     * @param printString Complete ESC/POS string ready for the printer
     * @return true if sent successfully
     */
    public boolean sendPrintString(String printString) {
        checkInit();
        if (printString == null || printString.isEmpty()) return false;
        try {
            return sendToPrinter(printString.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            return sendToPrinter(printString.getBytes());
        }
    }

    /**
     * Send a print string with specified character encoding.
     * Common encodings: "UTF8" for network printers, "IBM437" for serial/COM,
     * "ASCII" for Bluetooth or USB raw printing.
     *
     * @param printString The ESC/POS command string
     * @param encoding Character encoding name (e.g. "UTF8", "IBM437", "ASCII")
     * @return true if sent successfully
     */
    public boolean sendPrintStringEncoded(String printString, String encoding) {
        checkInit();
        if (printString == null || printString.isEmpty()) return false;
        try {
            return sendToPrinter(printString.getBytes(encoding));
        } catch (java.io.UnsupportedEncodingException e) {
            lastJobError = "Unsupported encoding: " + encoding;
            return false;
        }
    }

    /**
     * Get the Windows PrintService for this printer (for direct javax.print access).
     */
    public javax.print.PrintService getPrintService() {
        return printService;
    }

    // =====================================================================
    //  BARCODE PRINTING
    // =====================================================================

    /**
     * Print a barcode. The job is sent immediately.
     * @param data Barcode data string
     * @param barcodeType Use BARCODE_* constants from ESCPOSCommands
     * @param height Dots height (default ~162)
     * @param width Width multiplier 2-6
     * @param hriPosition HRI text position (0=none, 1=above, 2=below, 3=both)
     */
    public boolean printBarcode(String data, int barcodeType, int height, int width, int hriPosition) {
        checkInit();
        escpos.startNewJob();
        escpos.setBarcodeHeight(height);
        escpos.setBarcodeWidth(width);
        escpos.setBarcodeHriPosition(hriPosition);
        escpos.printBarcode(data, barcodeType);
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Print a CODE128 barcode with default settings.
     */
    public boolean printBarcodeCode128(String data) {
        return printBarcode(data, ESCPOSCommands.BARCODE_CODE128, 162, 3, 2);
    }

    /**
     * Print an EAN13 barcode with default settings.
     */
    public boolean printBarcodeEan13(String data) {
        return printBarcode(data, ESCPOSCommands.BARCODE_EAN13, 162, 3, 2);
    }

    // =====================================================================
    //  QR CODE PRINTING
    // =====================================================================

    /**
     * Print a QR code with custom settings.
     * @param data Data to encode
     * @param moduleSize QR module size 1-16 (typical: 4-6)
     * @param errorCorrectionLevel 0=L(7%), 1=M(15%), 2=Q(25%), 3=H(30%)
     */
    public boolean printQRCode(String data, int moduleSize, int errorCorrectionLevel) {
        checkInit();
        int ec;
        switch (errorCorrectionLevel) {
            case 0: ec = 48; break;
            case 2: ec = 50; break;
            case 3: ec = 51; break;
            default: ec = 49; break; // M(15%) default
        }
        escpos.startNewJob();
        escpos.printQRCode(data, moduleSize, ec);
        escpos.newLine();
        return sendToPrinter(escpos.getCommandBytes());
    }

    /**
     * Print a QR code with default settings (size=4, M-level correction).
     */
    public boolean printQRCodeSimple(String data) {
        return printQRCode(data, 4, 1);
    }

    // =====================================================================
    //  RECEIPT TEMPLATES
    // =====================================================================

    /**
     * Print a standard receipt header: centered shop name, divider, date.
     * @param shopName Shop/business name
     * @param receiptTitle e.g., "SALE RECEIPT"
     */
    public void appendReceiptHeader(String shopName, String receiptTitle) {
        checkInit();
        escpos.setAlignment(ESCPOSCommands.ALIGN_CENTER);
        escpos.setBold(true);
        escpos.setCharSize(1, 2); // double height
        escpos.appendTextLine(shopName);
        escpos.setCharSize(1, 1);
        escpos.setBold(false);
        escpos.appendTextLine(receiptTitle);
        escpos.appendSeparator();
        escpos.setAlignment(ESCPOSCommands.ALIGN_LEFT);

        // Date/time
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        escpos.appendTextLine("Date: " + sdf.format(new java.util.Date()));
        escpos.appendSeparator();
    }

    /**
     * Append a receipt line item: item name left-aligned, price right-aligned.
     * Uses 48-char line width typical for 80mm receipt printers (Font A).
     */
    public void appendReceiptLine(String itemName, String price) {
        checkInit();
        int nameLen = Math.min(itemName.length(), 32);
        String truncatedName = itemName.substring(0, nameLen);
        StringBuilder line = new StringBuilder();
        line.append(truncatedName);
        // Pad to align price to right (48 chars total)
        int padLen = 48 - truncatedName.length() - price.length();
        if (padLen < 1) padLen = 1;
        for (int i = 0; i < padLen; i++) {
            line.append(' ');
        }
        line.append(price);
        escpos.appendTextLine(line.toString());
    }

    /**
     * Append receipt footer: total, thankyou message, cut.
     */
    public void appendReceiptFooter(String totalAmount, String thankYouMessage) {
        checkInit();
        escpos.appendSeparator();
        escpos.setBold(true);
        escpos.appendTextLine("TOTAL: " + totalAmount);
        escpos.setBold(false);
        escpos.appendSeparator();
        escpos.setAlignment(ESCPOSCommands.ALIGN_CENTER);
        escpos.appendTextLine(thankYouMessage != null ? thankYouMessage : "Thank You!");
        escpos.feedLines(2);
        escpos.cutPaper(ESCPOSCommands.CUT_PARTIAL, 100);
    }

    /**
     * Convenience: print a complete receipt in one call.
     */
    public boolean printFullReceipt(String shopName, String receiptTitle,
                                    String[][] items, // [name, price] pairs
                                    String totalAmount, String thankYouMessage) {
        beginJob();
        appendReceiptHeader(shopName, receiptTitle);
        if (items != null) {
            for (String[] item : items) {
                if (item.length >= 2) {
                    appendReceiptLine(item[0], item[1]);
                }
            }
        }
        appendReceiptFooter(totalAmount, thankYouMessage);
        return executeJob();
    }

    // =====================================================================
    //  TEST & DIAGNOSTIC
    // =====================================================================

    /**
     * Print a test page showing printer capabilities and ESC/POS feature demo.
     */
    public boolean printTestPage() {
        checkInit();
        beginJob();
        escpos.setAlignment(ESCPOSCommands.ALIGN_CENTER);
        escpos.setCharSize(2, 2);
        escpos.appendTextLine("PRINTER TEST");
        escpos.setCharSize(1, 1);
        escpos.appendSeparator('=', 48);
        escpos.setAlignment(ESCPOSCommands.ALIGN_LEFT);

        escpos.appendTextLine("Printer: " + currentPrinterName);
        escpos.appendTextLine("Date:    " + new java.util.Date().toString());
        escpos.appendSeparator();

        escpos.appendTextLine("Font A (default): ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        escpos.setFont(1);
        escpos.appendTextLine("Font B (small):   ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        escpos.setFont(0);

        escpos.setBold(true);
        escpos.appendTextLine("Bold text test");
        escpos.setBold(false);

        escpos.setUnderline(1);
        escpos.appendTextLine("Underline text test");
        escpos.setUnderline(0);

        escpos.setAlignment(ESCPOSCommands.ALIGN_CENTER);
        escpos.appendTextLine("CENTERED TEXT");
        escpos.setAlignment(ESCPOSCommands.ALIGN_RIGHT);
        escpos.appendTextLine("RIGHT ALIGNED");
        escpos.setAlignment(ESCPOSCommands.ALIGN_LEFT);

        escpos.setCharSize(2, 1);
        escpos.appendTextLine("Double width");
        escpos.setCharSize(1, 2);
        escpos.appendTextLine("Double height");
        escpos.setCharSize(1, 1);

        escpos.appendSeparator();

        // QR Code demo
        escpos.setAlignment(ESCPOSCommands.ALIGN_CENTER);
        escpos.appendTextLine("QR Code Test:");
        escpos.printQRCode("https://github.com", 4, 49);
        escpos.newLine();

        // Barcode demo
        escpos.appendTextLine("CODE128 Test:");
        escpos.setBarcodeHeight(80);
        escpos.setBarcodeWidth(2);
        escpos.setBarcodeHriPosition(2);
        escpos.printBarcode("TEST12345", ESCPOSCommands.BARCODE_CODE128);
        escpos.newLine();

        escpos.feedLines(2);
        escpos.cutPaper(ESCPOSCommands.CUT_PARTIAL, 100);

        return executeJob();
    }

    // =====================================================================
    //  DIRECT COMMAND ACCESS (for power users)
    // =====================================================================

    /**
     * Get direct access to the ESCPOSCommands builder for advanced use.
     * Call beginJob() first, then modify via getEscpos(), then executeJob().
     */
    public ESCPOSCommands getEscpos() {
        return escpos;
    }

    // =====================================================================
    //  B4X XML METHOD BRIDGES
    // =====================================================================

    public boolean Initialize(String printerName) { return initialize(printerName); }
    public boolean InitializeDefault() { return initializeDefault(); }
    public boolean IsInitialized() { return isInitialized(); }

    public java.util.List<String> GetAllPrinterNames() { return getAllPrinterNames(); }
    /** @deprecated Always returns all printer names (filtering removed). Use GetAllPrinterNames(). */
    @Deprecated
    public java.util.List<String> GetLocalPrinterNames() { return getAllPrinterNames(); }
    /** @deprecated Always returns all printer names (filtering removed). Use GetAllPrinterNames(). */
    @Deprecated
    public java.util.List<String> GetUsbPrinterNames() { return getAllPrinterNames(); }
    public String GetDefaultPrinterName() { return getDefaultPrinterName(); }
    public java.util.List<java.util.Map<String, Object>> GetAllPrinterDetails() { return getAllPrinterDetails(); }

    public boolean PrintLine(String text) { return printLine(text); }
    public boolean PrintText(String text) { return printText(text); }
    public boolean PrintCenteredLine(String text) { return printCenteredLine(text); }
    public boolean PrintSeparator() { return printSeparator(); }
    public boolean PrintSeparatorChar(char ch, int count) { return printSeparatorChar(ch, count); }

    public void BeginJob() { beginJob(); }
    public boolean ExecuteJob() { return executeJob(); }
    public void AppendText(String text) { appendText(text); }
    public void AppendLine(String text) { appendLine(text); }
    public void AppendCenteredLine(String text) { appendCenteredLine(text); }

    public void SetBold(boolean enabled) { setBold(enabled); }
    public void SetUnderline(boolean enabled) { setUnderline(enabled); }
    public void SetDoubleStrike(boolean enabled) { setDoubleStrike(enabled); }
    public void SetReverseMode(boolean enabled) { setReverseMode(enabled); }
    public void SetAlignmentLeft() { setAlignmentLeft(); }
    public void SetAlignmentCenter() { setAlignmentCenter(); }
    public void SetAlignmentRight() { setAlignmentRight(); }
    public void SetCharSize(int width, int height) { setCharSize(width, height); }
    public void SetFont(int fontIndex) { setFont(fontIndex); }
    public void SetLineSpacing(int dots) { setLineSpacing(dots); }

    public boolean FeedLines(int count) { return feedLines(count); }
    public boolean FeedDots(int dotCount) { return feedDots(dotCount); }
    public boolean CutPaperFull() { return cutPaperFull(); }
    public boolean CutPaperPartial() { return cutPaperPartial(); }
    public boolean CutPaper(boolean fullCut, int feedDotsBeforeCut) { return cutPaper(fullCut, feedDotsBeforeCut); }

    public boolean OpenCashDrawer() { return openCashDrawer(); }
    public boolean OpenCashDrawerByKickCode(int drawerKickCode) { return openCashDrawerByKickCode(drawerKickCode); }
    public boolean PulseCashDrawer(int pin, int onTimeMs, int offTimeMs) { return pulseCashDrawer(pin, onTimeMs, offTimeMs); }

    public void SetPaperWidth(int paperWidthDots) { setPaperWidth(paperWidthDots); }
    public void SetPaperWidthByName(String widthName) { setPaperWidthByName(widthName); }
    public int GetCharsPerLine() { return getCharsPerLine(); }

    public boolean SendRawBytes(byte[] rawBytes) { return sendRawBytes(rawBytes); }
    public boolean SendPrintString(String printString) { return sendPrintString(printString); }
    public boolean SendPrintStringEncoded(String printString, String encoding) { return sendPrintStringEncoded(printString, encoding); }
    public byte[] ConvertEscposRasterToPng(byte[] imageParams, byte[] imageData) { return convertEscposRasterToPng(imageParams, imageData); }
    public boolean SendPngToPrinter(byte[] pngBytes) { return sendPngToPrinter(pngBytes); }

    /**
     * Convert ESC/POS raster data to PNG and send directly to the printer.
     * Single-call convenience that avoids the B4J byte[]-param codegen issue.
     */
    public boolean PrintEscposRasterAsPng(byte[] imageParams, byte[] imageData) {
        byte[] pngBytes = convertEscposRasterToPng(imageParams, imageData);
        if (pngBytes == null) return false;
        return sendPngToPrinter(pngBytes);
    }

    public boolean PrintBarcode(String data, int barcodeType, int height, int width, int hriPosition) { return printBarcode(data, barcodeType, height, width, hriPosition); }
    public boolean PrintBarcodeCode128(String data) { return printBarcodeCode128(data); }
    public boolean PrintBarcodeEan13(String data) { return printBarcodeEan13(data); }
    public boolean PrintQRCode(String data, int moduleSize, int errorCorrectionLevel) { return printQRCode(data, moduleSize, errorCorrectionLevel); }
    public boolean PrintQRCodeSimple(String data) { return printQRCodeSimple(data); }

    public void AppendReceiptHeader(String shopName, String receiptTitle) { appendReceiptHeader(shopName, receiptTitle); }
    public void AppendReceiptLine(String itemName, String price) { appendReceiptLine(itemName, price); }
    public void AppendReceiptFooter(String totalAmount, String thankYouMessage) { appendReceiptFooter(totalAmount, thankYouMessage); }
    public boolean PrintFullReceipt(String shopName, String receiptTitle, String[][] items, String totalAmount, String thankYouMessage) {
        return printFullReceipt(shopName, receiptTitle, items, totalAmount, thankYouMessage);
    }
    public boolean PrintTestPage() { return printTestPage(); }
    public void SetAsyncPrinting(boolean async) { setAsyncPrinting(async); }

    // =====================================================================
    //  RASTER-TO-PNG CONVERSION
    // =====================================================================

    /**
     * Convert ESC/POS GS v 0 raster bitmap data to PNG bytes for
     * Windows spooler printing.  This lets the Windows printer driver
     * handle halftoning at the printer's native resolution instead of
     * mangling raw ESC/POS bytes.
     *
     * @param imageParams 5-byte param array from B4X CreatePrintImageData:
     *                    [0]=mode, [1]=xL, [2]=xH, [3]=yL, [4]=yH
     * @param imageData   Packed 1-bit raster (8 pixels per byte, MSB first)
     * @return PNG-encoded byte array, or null on failure
     */
    public byte[] convertEscposRasterToPng(byte[] imageParams, byte[] imageData) {
        try {
            int bytesPerRow = (imageParams[1] & 0xFF) | ((imageParams[2] & 0xFF) << 8);
            int widthPixels = bytesPerRow * 8;
            int heightPixels = (imageParams[3] & 0xFF) | ((imageParams[4] & 0xFF) << 8);

            BufferedImage img = new BufferedImage(widthPixels, heightPixels, BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < heightPixels; y++) {
                for (int x = 0; x < widthPixels; x++) {
                    int byteIdx = y * bytesPerRow + (x / 8);
                    if (byteIdx >= imageData.length) break;
                    int bitIdx = 7 - (x % 8);               // MSB = leftmost pixel
                    int bit = (imageData[byteIdx] >> bitIdx) & 1;
                    int pixel = (bit == 0) ? 255 : 0;       // 1=black (ESC/POS), 0=white → 0=black in gray
                    int rgb = (pixel << 16) | (pixel << 8) | pixel;
                    img.setRGB(x, y, rgb);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            lastJobError = "Raster-to-PNG conversion error: " + e.getMessage();
            return null;
        }
    }

    /**
     * Send PNG bytes directly to the Windows print spooler.  The driver
     * handles resolution, scaling, and halftoning in its native pipeline.
     */
    public boolean sendPngToPrinter(byte[] pngBytes) {
        checkInit();
        if (pngBytes == null || pngBytes.length == 0) {
            lastJobError = "No PNG data to print";
            return false;
        }
        try {
            DocFlavor flavor = DocFlavor.INPUT_STREAM.PNG;
            Doc doc = new SimpleDoc(new ByteArrayInputStream(pngBytes), flavor, null);
            DocPrintJob job = printService.createPrintJob();
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            job.print(doc, attrs);
            lastJobSuccess = true;
            lastJobError = "";
            return true;
        } catch (PrintException e) {
            lastJobSuccess = false;
            lastJobError = "PNG print error: " + e.getMessage();
            return false;
        }
    }

    // =====================================================================
    //  PRIVATE HELPERS
    // =====================================================================

    private void checkInit() {
        if (!isInitialized) {
            throw new IllegalStateException("USBThermalPrinter not initialized. Call Initialize(printerName) first.");
        }
    }

    /**
     * Send raw byte data to the configured printer via javax.print.
     */
    private boolean sendToPrinter(byte[] data) {
        if (data == null || data.length == 0) {
            lastJobError = "No data to print";
            return false;
        }

        try {
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(data, flavor, null);
            DocPrintJob job = printService.createPrintJob();

            if (!asyncPrinting) {
                // Synchronous print
                PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
                job.print(doc, attrs);
                lastJobSuccess = true;
                lastJobError = "";
                return true;
            } else {
                // Asynchronous with listener
                job.addPrintJobListener(new PrintJobAdapter() {
                    @Override
                    public void printJobCompleted(PrintJobEvent pje) {
                        lastJobSuccess = true;
                        lastJobError = "";
                    }

                    @Override
                    public void printJobFailed(PrintJobEvent pje) {
                        lastJobSuccess = false;
                        lastJobError = "Print job failed";
                    }
                });
                job.print(doc, null);
                return true; // Optimistic
            }
        } catch (PrintException e) {
            lastJobSuccess = false;
            lastJobError = "Print error: " + e.getMessage();
            return false;
        }
    }

    /**
     * Get last job success status.
     */
    public boolean getLastJobSuccess() {
        return lastJobSuccess;
    }

    /**
     * Enable or disable asynchronous printing.
     */
    public void setAsyncPrinting(boolean async) {
        this.asyncPrinting = async;
    }
}
