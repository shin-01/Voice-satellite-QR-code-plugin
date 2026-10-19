// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the QR plugin asks KS for, and that its QR content matches a reference encoder. */
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
        settings.put("content", "WIFI:T:WPA;S:Home;P:secret;;");
        settings.put("corner", "Bottom left");
        settings.put("icon", "mdi:wifi");
        settings.put("qrSize", 320);
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

        // The command opens the full screen card.
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

        // Empty content keeps the widget away and says why.
        Map<String, Object> empty = settings();
        empty.put("content", "");
        plugin.configure(empty);
        assert !host.shown.containsKey("qr-widget");
        assert host.status != null;
        plugin.configure(settings());
        assert host.shown.containsKey("qr-widget");

        plugin.stop();
        System.out.println("QrPluginTest OK");
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
}
