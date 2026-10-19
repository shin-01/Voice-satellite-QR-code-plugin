// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the QR plugin asks KS for, for every content type. */
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
        settings.put("title", "Wi-Fi");
        settings.put("corner", "Bottom left");
        settings.put("icon", "mdi:wifi");
        settings.put("qrSize", 320);
        settings.put("contentType", "Wi-Fi");
        settings.put("wifiSsid", "Home");
        settings.put("wifiSecurity", "WPA/WPA2/WPA3");
        settings.put("wifiPassword", "secret");
        settings.put("urlContent", "https://www.kiosksatellite.com/");
        settings.put("textContent", "Hello world");
        return settings;
    }

    public static void main(String[] args) {
        Host host = new Host();
        QrPlugin plugin = new QrPlugin();

        // The widget appears once started, anchored where the settings say.
        plugin.start(host, settings());
        OverlaySpec widget = host.shown.get("qr-widget");
        assert widget != null;
        assert OverlaySpec.BOTTOM_LEFT.equals(widget.anchor()) && widget.inset() == 16;
        assert OverlaySpec.FADE.equals(widget.enter()) && widget.touchable();

        // The gesture, drawer or widget action opens the full screen card.
        plugin.execute("showQr", new LinkedHashMap<String, Object>());
        OverlaySpec code = host.shown.get("qr-code");
        assert code != null;
        assert code.width() == OverlaySpec.FILL && code.height() == OverlaySpec.FILL;
        assert code.closeOnOutsideTouch() && code.closeOnBack();

        // KS removes the card itself when it sends overlay.closed; the widget keeps its key.
        plugin.onEvent("overlay.closed", map("key", "qr-code"));
        assert host.shown.containsKey("qr-widget");
        plugin.execute("showQr", map());
        plugin.onEvent("overlay.closed", map("key", "qr-widget"));
        assert host.shown.containsKey("qr-code");

        // Turning the widget off removes it.
        Map<String, Object> off = settings();
        off.put("showWidget", false);
        plugin.configure(off);
        assert !host.shown.containsKey("qr-widget");
        Map<String, Object> on = settings();
        plugin.configure(on);
        assert host.shown.containsKey("qr-widget");

        // Switching the content type to URL keeps the widget up with the URL.
        Map<String, Object> url = settings();
        url.put("contentType", "URL");
        plugin.configure(url);
        assert host.shown.containsKey("qr-widget");

        // Switching to plain text works the same.
        Map<String, Object> text = settings();
        text.put("contentType", "Text");
        plugin.configure(text);
        assert host.shown.containsKey("qr-widget");

        // Empty content keeps the widget away and says why.
        Map<String, Object> empty = settings();
        empty.put("contentType", "URL");
        empty.put("urlContent", "");
        plugin.configure(empty);
        assert !host.shown.containsKey("qr-widget");
        assert host.status != null;
        plugin.configure(settings());
        assert host.shown.containsKey("qr-widget");

        // The Wi-Fi string escapes its reserved characters.
        Map<String, Object> tricky = settings();
        tricky.put("wifiSsid", "Ho:me;Net");
        tricky.put("wifiPassword", "p;a\\s\"s");
        plugin.configure(tricky);
        assert QrPlugin.wifiPayload(tricky).equals("WIFI:T:WPA;S:Ho\\:me\\;Net;P:p\\;a\\\\s\\\"s;;");

        // An open network omits the password.
        Map<String, Object> open = settings();
        open.put("wifiSecurity", "Open (no password)");
        plugin.configure(open);
        assert QrPlugin.wifiPayload(open).equals("WIFI:T:nopass;S:Home;;");

        plugin.stop();
        System.out.println("QrPluginTest OK");
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
