// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the QR plugin asks KS for, for the bar and every content type. */
public final class QrPluginTest {
    static final class Host implements PluginHost {
        final Map<String, OverlaySpec> shown = new LinkedHashMap<>();
        String status;
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) {
            assert factory != null;
            shown.put(key, spec);
        }
        public void hideOverlay(String key) { shown.remove(key); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) { status = message; assert error; }
    }

    static Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("showBar", true);
        settings.put("corner", "Bottom left");
        settings.put("qrSize", 320);
        for (int slot = 1; slot <= 4; slot++) {
            settings.put("qr" + slot + "Title", "QR " + slot);
            settings.put("qr" + slot + "Icon", "mdi:numeric-" + slot + "-circle");
            settings.put("qr" + slot + "Type", slot == 1 ? "Wi-Fi" : "URL");
            settings.put("qr" + slot + "Ssid", slot == 1 ? "Home" : "");
            settings.put("qr" + slot + "Security", "WPA/WPA2/WPA3");
            settings.put("qr" + slot + "Password", slot == 1 ? "secret" : "");
            settings.put("qr" + slot + "Url", "https://www.kiosksatellite.com/" + slot);
            settings.put("qr" + slot + "Text", "text " + slot);
        }
        return settings;
    }

    public static void main(String[] args) {
        Host host = new Host();
        QrPlugin plugin = new QrPlugin();

        // Overlay keys follow [a-z][a-z0-9_]{0,39}; KS rejects anything else.
        assert "qr_bar".matches("[a-z][a-z0-9_]{0,39}");
        assert "qr_code".matches("[a-z][a-z0-9_]{0,39}");
        assert "qr_drag".matches("[a-z][a-z0-9_]{0,39}");

        // The bar appears once started, anchored where the settings say.
        plugin.start(host, settings());
        OverlaySpec bar = host.shown.get("qr_bar");
        assert bar != null;
        assert OverlaySpec.BOTTOM_LEFT.equals(bar.anchor()) && bar.inset() == 16;
        assert OverlaySpec.FADE.equals(bar.enter()) && bar.touchable();
        assert bar.offsetX() == 0 && bar.offsetY() == 0;

        // Free placement positions the bar by its saved offset from the center.
        Map<String, Object> free = settings();
        free.put("placement", "Free");
        free.put("barX", -120);
        free.put("barY", 60);
        plugin.configure(free);
        OverlaySpec floating = host.shown.get("qr_bar");
        assert floating != null && floating.offsetX() == -120 && floating.offsetY() == 60;
        assert OverlaySpec.CENTER.equals(floating.anchor()) && floating.inset() == 0;

        // Every slot's action opens the full screen card and hides the bar.
        plugin.configure(settings());
        plugin.execute("showQr", new LinkedHashMap<String, Object>());
        OverlaySpec code = host.shown.get("qr_code");
        assert code != null;
        assert code.width() == OverlaySpec.FILL && code.height() == OverlaySpec.FILL;
        assert code.closeOnOutsideTouch() && code.closeOnBack();
        assert !host.shown.containsKey("qr_bar");
        // KS removes the card itself on overlay.closed; the bar comes back.
        plugin.onEvent("overlay.closed", map("key", "qr_code"));
        assert host.shown.containsKey("qr_bar");

        // Show bar off removes the bar; an open QR card is left alone.
        Map<String, Object> off = settings();
        off.put("showBar", false);
        plugin.configure(off);
        assert !host.shown.containsKey("qr_bar");

        // The Wi-Fi string escapes its reserved characters.
        Map<String, Object> tricky = settings();
        tricky.put("qr1Ssid", "Ho:me;Net");
        tricky.put("qr1Password", "p;a\\s\"s");
        plugin.configure(tricky);
        assert QrPlugin.buildContent(tricky, "qr1").equals("WIFI:T:WPA;S:Ho\\:me\\;Net;P:p\\;a\\\\s\\\"s;;");

        // An open network omits the password.
        Map<String, Object> open = settings();
        open.put("qr1Security", "Open (no password)");
        plugin.configure(open);
        assert QrPlugin.buildContent(open, "qr1").equals("WIFI:T:nopass;S:Home;;");

        // A URL slot builds the URL, a Text slot the text.
        assert QrPlugin.buildContent(settings(), "qr2").equals("https://www.kiosksatellite.com/2");
        Map<String, Object> text = settings();
        text.put("qr3Type", "Text");
        assert QrPlugin.buildContent(text, "qr3").equals("text 3");

        plugin.stop();
        System.out.println("QrPluginTest OK");
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
