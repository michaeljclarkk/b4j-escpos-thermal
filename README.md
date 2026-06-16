# B4J ESCPOS Thermal Printer

B4J library for printing to USB thermal receipt printers (80mm) via the Windows print spooler. Zero dependencies — pure `javax.print`.

## Build

Requires JDK 11+ on PATH.

```powershell
.\build.ps1
```

Outputs `output\escposthermal.jar` and `output\escposthermal.b4xlib`.

## Install in B4J

1. Copy `escposthermal.jar` and `escposthermal.xml` to your **B4J Additional Libraries** folder
2. In the B4J IDE: right-click the **Libraries** tab → **Refresh**
3. Check **USBThermalPrinter** in the Libraries Manager

## Usage

```b4j
Sub Process_Globals
    Private printer As USBThermalPrinter
End Sub

Sub AppStart
    ' List installed Windows printers
    Dim allNames As List = USBThermalPrinter.GetAllPrinterNames
    For Each name As String In allNames
        Log(name)
    Next

    ' Or just USB printers
    Dim usbNames As List = USBThermalPrinter.GetUsbPrinterNames
    If usbNames.Size > 0 Then
        ' Connect to first USB printer found
        printer.Initialize
        If printer.Initialize(usbNames.Get(0)) Then
            ' Quick one-line print
            printer.PrintLine("Hello, thermal world!")
            printer.CutPaperPartial
        End If
    End If
End Sub
```

For full API reference see [DOCUMENTATION.md](DOCUMENTATION.md).
