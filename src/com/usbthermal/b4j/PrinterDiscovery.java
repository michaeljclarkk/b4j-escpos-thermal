package com.usbthermal.b4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.PrinterLocation;
import javax.print.attribute.standard.PrinterMakeAndModel;
import javax.print.attribute.standard.PrinterName;
import javax.print.attribute.standard.PrinterState;
import javax.print.attribute.standard.QueuedJobCount;
import javax.print.attribute.standard.ColorSupported;

/**
 * Discovers installed Windows printers via javax.print (Win32 print spooler).
 * Filters for local/USB printers suitable for thermal receipt printing.
 */
public class PrinterDiscovery {

    // ─── Singleton pattern ───────────────────────────────────────────────
    private static PrinterDiscovery instance;
    private List<PrinterInfo> cachedPrinterList;
    private long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 5000; // 5-second cache

    private PrinterDiscovery() {
        // private constructor
    }

    public static synchronized PrinterDiscovery getInstance() {
        if (instance == null) {
            instance = new PrinterDiscovery();
        }
        return instance;
    }

    // =====================================================================
    //  PUBLIC DISCOVERY API
    // =====================================================================

    /**
     * Get all installed printers. Results are cached for 5 seconds.
     * @return List of PrinterInfo objects for all available printers
     */
    public List<PrinterInfo> getAllPrinters() {
        if (isCacheValid()) {
            return new ArrayList<>(cachedPrinterList);
        }
        cachedPrinterList = discoverAllPrinters();
        cacheTimestamp = System.currentTimeMillis();
        return new ArrayList<>(cachedPrinterList);
    }

    /**
     * Get only local (USB/parallel/serial) printers. Sorted: default printer first.
     */
    public List<PrinterInfo> getLocalPrinters() {
        List<PrinterInfo> all = getAllPrinters();
        List<PrinterInfo> local = new ArrayList<>();
        for (PrinterInfo info : all) {
            if (info.isLocal()) {
                local.add(info);
            }
        }
        sortDefaultFirst(local);
        return local;
    }

    /**
     * Get only USB-connected printers (heuristic: local + location contains "USB").
     */
    public List<PrinterInfo> getUsbPrinters() {
        List<PrinterInfo> all = getAllPrinters();
        List<PrinterInfo> usb = new ArrayList<>();
        for (PrinterInfo info : all) {
            if (info.isUsbPrinter()) {
                usb.add(info);
            }
        }
        sortDefaultFirst(usb);
        return usb;
    }

    /**
     * Get a list of printer names only (simple string list for B4J).
     */
    public List<String> getAllPrinterNames() {
        List<String> names = new ArrayList<>();
        for (PrinterInfo info : getAllPrinters()) {
            names.add(info.getPrinterName());
        }
        return names;
    }

    /**
     * Get a list of local printer names only.
     */
    public List<String> getLocalPrinterNames() {
        List<String> names = new ArrayList<>();
        for (PrinterInfo info : getLocalPrinters()) {
            names.add(info.getPrinterName());
        }
        return names;
    }

    /**
     * Get a list of USB printer names only.
     */
    public List<String> getUsbPrinterNames() {
        List<String> names = new ArrayList<>();
        for (PrinterInfo info : getUsbPrinters()) {
            names.add(info.getPrinterName());
        }
        return names;
    }

    /**
     * Look up a specific printer by exact name.
     * @return PrinterInfo or null if not found
     */
    public PrinterInfo getPrinterByName(String printerName) {
        if (printerName == null || printerName.isEmpty()) return null;
        for (PrinterInfo info : getAllPrinters()) {
            if (info.getPrinterName().equalsIgnoreCase(printerName)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Get the default printer (Windows default).
     * @return PrinterInfo or null if no default
     */
    public PrinterInfo getDefaultPrinter() {
        PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultService == null) return null;
        return buildPrinterInfo(defaultService);
    }

    /**
     * Get the default printer name only.
     * @return Printer name or empty string
     */
    public String getDefaultPrinterName() {
        PrinterInfo info = getDefaultPrinter();
        return info != null ? info.getPrinterName() : "";
    }

    /**
     * Refresh the printer list, bypassing the cache.
     */
    public void refreshCache() {
        cacheTimestamp = 0;
    }

    /**
     * Get the javax.print.PrintService object for a given printer name.
     * Useful for direct Java print API access.
     * @return PrintService or null
     */
    public PrintService getPrintServiceByName(String printerName) {
        if (printerName == null || printerName.isEmpty()) return null;
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName)) {
                return service;
            }
        }
        return null;
    }

    // =====================================================================
    //  PRIVATE HELPERS
    // =====================================================================

    private boolean isCacheValid() {
        return cachedPrinterList != null
            && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS;
    }

    private List<PrinterInfo> discoverAllPrinters() {
        List<PrinterInfo> printers = new ArrayList<>();
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            printers.add(buildPrinterInfo(service));
        }
        sortDefaultFirst(printers);
        return printers;
    }

    private PrinterInfo buildPrinterInfo(PrintService service) {
        AttributeSet attrs = service.getAttributes();

        String name = getAttrString(attrs, PrinterName.class, service.getName());
        String location = getAttrString(attrs, PrinterLocation.class, "");
        String driverModel = getAttrString(attrs, PrinterMakeAndModel.class, "");
        boolean isDefault = isServiceDefault(service);

        // Determine connection type from location string
        String locUpper = location.toUpperCase();
        boolean isNetworked = locUpper.startsWith("\\\\") || locUpper.contains("IP_")
                           || locUpper.startsWith("HTTP") || locUpper.contains("NETWORK");
        boolean isLocal = !isNetworked
                       && (locUpper.startsWith("USB") || locUpper.startsWith("LPT")
                        || locUpper.startsWith("COM") || locUpper.isEmpty()
                        || locUpper.startsWith("TP"));

        boolean supportsColor = false;
        ColorSupported cs = (ColorSupported) attrs.get(ColorSupported.class);
        if (cs != null) {
            supportsColor = cs.equals(ColorSupported.SUPPORTED);
        }

        String status = deriveStatus(attrs);

        return new PrinterInfo(name, location, driverModel, isDefault, isLocal, isNetworked, supportsColor, status);
    }

    private boolean isServiceDefault(PrintService service) {
        PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
        return defaultService != null && defaultService.getName().equals(service.getName());
    }

    @SuppressWarnings("unchecked")
    private <T extends Attribute> String getAttrString(AttributeSet attrs, Class<T> category, String defaultValue) {
        T attr = (T) attrs.get(category);
        return (attr != null) ? attr.toString() : defaultValue;
    }

    private String deriveStatus(AttributeSet attrs) {
        PrinterState state = (PrinterState) attrs.get(PrinterState.class);
        PrinterIsAcceptingJobs accepting = (PrinterIsAcceptingJobs) attrs.get(PrinterIsAcceptingJobs.class);

        if (state == null) return "Unknown";

        if (state == PrinterState.IDLE && (accepting == null || accepting == PrinterIsAcceptingJobs.ACCEPTING_JOBS)) {
            return "Ready";
        } else if (state == PrinterState.PROCESSING) {
            return "Printing";
        } else if (state == PrinterState.STOPPED) {
            return "Stopped";
        } else {
            return state.toString();
        }
    }

    private void sortDefaultFirst(List<PrinterInfo> list) {
        Collections.sort(list, new Comparator<PrinterInfo>() {
            @Override
            public int compare(PrinterInfo a, PrinterInfo b) {
                if (a.isDefault() && !b.isDefault()) return -1;
                if (!a.isDefault() && b.isDefault()) return 1;
                return a.getPrinterName().compareToIgnoreCase(b.getPrinterName());
            }
        });
    }
}
