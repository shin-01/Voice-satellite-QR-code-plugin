// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.LinkedHashMap;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Checks what the QR plugin asks KS for, for every pill and content type. */
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
        settings.put("qrSize", 320);
        for (int slot = 1; slot <= 4; slot++) {
            settings.put("qr" + slot + "Title", "QR " + slot);
            settings.put("qr" + slot + "Icon", "mdi:numeric-" + slot + "-circle");
            settings.put("qr" + slot + "Pill", "Grouped");
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
        assert "qr_group".matches("[a-z][a-z0-9_]{0,39}");
        assert "qr_code".matches("[a-z][a-z0-9_]{0,39}");
        assert "qr_drag".matches("[a-z][a-z0-9_]{0,39}");
        assert "qr_pill_2".matches("[a-z][a-z0-9_]{0,39}");

        // All four grouped: one group pill, anchored where the settings say.
        plugin.start(host, settings());
        OverlaySpec group = host.shown.get("qr_group");
        assert group != null;
        assert OverlaySpec.BOTTOM_LEFT.equals(group.anchor()) && group.inset() == 16;
        assert OverlaySpec.FADE.equals(group.enter()) && group.touchable();
        assert !host.shown.containsKey("qr_pill_1");

        // Detached pills get their own overlays, each in its own corner.
        Map<String, Object> detached = settings();
        detached.put("qr2Pill", "Detached");
        detached.put("qr3Pill", "Detached");
        plugin.configure(detached);
        assert host.shown.containsKey("qr_group");
        assert host.shown.containsKey("qr_pill_2") && host.shown.containsKey("qr_pill_3");
        assert !host.shown.containsKey("qr_pill_1") && !host.shown.containsKey("qr_pill_4");
        assert OverlaySpec.TOP_RIGHT.equals(host.shown.get("qr_pill_2").anchor());

        // A dragged detached pill keeps its saved position as a center offset.
        Map<String, Object> moved = settings();
        moved.put("qr2Pill", "Detached");
        moved.put("qr2X", -120);
        moved.put("qr2Y", 60);
        plugin.configure(moved);
        OverlaySpec floating = host.shown.get("qr_pill_2");
        assert floating != null && floating.offsetX() == -120 && floating.offsetY() == 60;
        assert OverlaySpec.CENTER.equals(floating.anchor()) && floating.inset() == 0;

        // A free group carries the same offset from the center.
        Map<String, Object> free = settings();
        free.put("placement", "Free");
        free.put("widgetX", -90);
        free.put("widgetY", 40);
        plugin.configure(free);
        OverlaySpec groupFree = host.shown.get("qr_group");
        assert groupFree != null && groupFree.offsetX() == -90 && groupFree.offsetY() == 40;
        assert OverlaySpec.CENTER.equals(groupFree.anchor());

        // Every slot's action opens the full screen card and hides the pills.
        plugin.configure(settings());
        plugin.execute("showQr", new LinkedHashMap<String, Object>());
        OverlaySpec code = host.shown.get("qr_code");
        assert code != null;
        assert code.width() == OverlaySpec.FILL && code.height() == OverlaySpec.FILL;
        assert code.closeOnOutsideTouch() && code.closeOnBack();
        assert !host.shown.containsKey("qr_group");
        // KS removes the card itself on overlay.closed; the pills come back.
        plugin.onEvent("overlay.closed", map("key", "qr_code"));
        assert host.shown.containsKey("qr_group");

        // Show pills off removes every pill; an open QR card is left alone.
        Map<String, Object> off = settings();
        off.put("showWidget", false);
        plugin.configure(off);
        assert !host.shown.containsKey("qr_group");
        for (int slot = 1; slot <= 4; slot++) assert !host.shown.containsKey("qr_pill_" + slot);

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
