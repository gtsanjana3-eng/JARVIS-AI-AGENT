package com.jarvis.system;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;

/**
 * A JARVIS icon in the Windows system tray (next to the clock) that shows real
 * Windows notifications — reminders appear even when the JARVIS web page is
 * closed. Right-click the icon to open JARVIS or exit. Pure Java (AWT).
 */
public class DesktopNotifier {

    private TrayIcon icon;

    public DesktopNotifier(String url) {
        try {
            if (!SystemTray.isSupported()) {
                return;
            }
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open JARVIS");
            open.addActionListener(e -> openBrowser(url));
            MenuItem exit = new MenuItem("Exit JARVIS");
            exit.addActionListener(e -> System.exit(0));
            menu.add(open);
            menu.addSeparator();
            menu.add(exit);
            icon = new TrayIcon(drawIcon(), "J.A.R.V.I.S. is running", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> openBrowser(url));
            SystemTray.getSystemTray().add(icon);
        } catch (AWTException | RuntimeException e) {
            icon = null; // no tray (e.g. a server without a desktop): reminders still show in the browser
        }
    }

    public boolean available() {
        return icon != null;
    }

    /** Shows a Windows notification with a sound. */
    public void notify(String title, String message) {
        if (icon != null) {
            icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
        Toolkit.getDefaultToolkit().beep();
    }

    private static void openBrowser(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
            // nothing else to do
        }
    }

    /** The JARVIS "arc reactor" icon, drawn in code. */
    private static BufferedImage drawIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(3, 7, 13));
        g.fillOval(2, 2, 60, 60);
        g.setColor(new Color(0, 229, 255));
        g.setStroke(new BasicStroke(5));
        g.drawOval(8, 8, 48, 48);
        g.setColor(new Color(22, 139, 255));
        g.setStroke(new BasicStroke(3));
        g.drawOval(18, 18, 28, 28);
        g.setColor(new Color(190, 250, 255));
        g.fillOval(25, 25, 14, 14);
        g.dispose();
        return img;
    }
}
