package com.usbthermal.b4j;

/**
 * Immutable data container for printer information discovered
 * via the Windows print spooler / javax.print API.
 */
public class PrinterInfo {

    private final String printerName;
    private final String location;       // e.g., "USB001", "LPT1:", "192.168.1.50"
    private final String driverName;
    private final boolean isDefault;
    private final boolean isLocal;       // True if connected via USB/parallel/serial
    private final boolean isNetworked;
    private final boolean supportsColor;
    private final String status;         // Best-effort status string

    public PrinterInfo(String printerName, String location, String driverName,
                       boolean isDefault, boolean isLocal, boolean isNetworked,
                       boolean supportsColor, String status) {
        this.printerName = printerName;
        this.location = location != null ? location : "";
        this.driverName = driverName != null ? driverName : "";
        this.isDefault = isDefault;
        this.isLocal = isLocal;
        this.isNetworked = isNetworked;
        this.supportsColor = supportsColor;
        this.status = status != null ? status : "Unknown";
    }

    // ─── Accessors ───────────────────────────────────────────────────────

    public String getPrinterName()  { return printerName; }
    public String getLocation()     { return location; }
    public String getDriverName()   { return driverName; }
    public boolean isDefault()      { return isDefault; }
    public boolean isLocal()        { return isLocal; }
    public boolean isNetworked()    { return isNetworked; }
    public boolean supportsColor()  { return supportsColor; }
    public String getStatus()       { return status; }

    /**
     * Returns true if the printer appears to be a USB-connected local printer.
     * Heuristic: local=true and location contains "USB" or "usb".
     */
    public boolean isUsbPrinter() {
        return isLocal && location.toUpperCase().contains("USB");
    }

    /**
     * Returns a human-readable summary.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(printerName);
        if (!location.isEmpty()) sb.append(" @ ").append(location);
        sb.append(" [").append(driverName).append("]");
        if (isDefault) sb.append(" (Default)");
        if (isUsbPrinter()) sb.append(" (USB)");
        return sb.toString();
    }

    /**
     * Convert to a flat map-like structure compatible with B4J Map.
     * Keys: name, location, driver, isDefault, isLocal, isNetworked, isUsb, status
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("name", printerName);
        map.put("location", location);
        map.put("driver", driverName);
        map.put("isDefault", isDefault);
        map.put("isLocal", isLocal);
        map.put("isNetworked", isNetworked);
        map.put("isUsb", isUsbPrinter());
        map.put("status", status);
        return map;
    }
}
