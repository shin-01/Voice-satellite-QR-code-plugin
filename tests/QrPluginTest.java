// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the QR plugin asks KS for, for every slot and content type. */
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
        settings.put("showWidget", true);
        settings.put("corner", "Bottom left");
        settings.put("icon", "mdi:wifi");
        settings.put("qrSize", 320);
        for (int slot = 1; slot <= 4; slot++) {
            settings.put("qr" + slot + "Title", "QR " + slot);
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
        assert "qr_widget".matches("[a-z][a-z0-9_]{0,39}");
        assert "qr_code".matches("[a-z][a-z0-9_]{0,39}");

        // The widget appears once started, anchored where the settings say.
        plugin.start(host, settings());
        OverlaySpec widget = host.shown.get("qr_widget");
        assert widget != null;
        assert OverlaySpec.BOTTOM_LEFT.equals(widget.anchor()) && widget.inset() == 16;
        assert OverlaySpec.FADE.equals(widget.enter()) && widget.touchable();

        // Every slot's action opens the same full screen card.
        for (int slot = 1; slot <= 4; slot++) {
            plugin.execute("showQr" + (slot == 1 ? "" : slot), new LinkedHashMap<String, Object>());
            OverlaySpec code = host.shown.get("qr_code");
            assert code != null;
            assert code.width() == OverlaySpec.FILL && code.height() == OverlaySpec.FILL;
            assert code.closeOnOutsideTouch() && code.closeOnBack();
            plugin.onEvent("overlay.closed", map("key", "qr_code"));
            assert host.shown.containsKey("qr_widget");
        }

        // Turning the widget off removes it, without touching the actions.
        Map<String, Object> off = settings();
        off.put("showWidget", false);
        plugin.configure(off);
        assert !host.shown.containsKey("qr_widget");
        plugin.execute("showQr", new LinkedHashMap<String, Object>());
        assert host.shown.containsKey("qr_code");
        plugin.onEvent("overlay.closed", map("key", "qr_code"));
        plugin.configure(settings());
        assert host.shown.containsKey("qr_widget");

        // An empty slot says why instead of opening.
        Map<String, Object> empty = settings();
        empty.put("qr2Type", "URL");
        empty.put("qr2Url", "");
        plugin.configure(empty);
        plugin.execute("showQr2", new LinkedHashMap<String, Object>());
        assert host.status != null;
        // KS never received a showOverlay for the empty slot; the fake host still
        // holds the earlier card, which the real host would have removed itself.

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
