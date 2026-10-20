// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.nayuki.qrcodegen.QrCode;
import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A small corner widget that shows the primary QR code on tap, plus up to four
 * QR codes that open through plugin actions users bind to gestures, the kiosk
 * drawer or Home Assistant buttons. Each QR encodes a URL, plain text or Wi-Fi
 * credentials. The screensaver's stock corner widgets are a Kiosk Satellite
 * surface the plugin SDK cannot add to, so these QR codes live in the plugin's
 * own overlays. QR rendering uses Nayuki's MIT licensed generator
 * (io.nayuki.qrcodegen), bundled with this plugin.
 */
public final class QrPlugin implements KioskPlugin {
    /** The slots, in manifest order. Slot 1 also backs the corner widget. */
    private static final int SLOTS = 4;
    private static final String WIDGET = "qr_widget";
    private static final String CODE = "qr_code";

    private PluginHost host;
    private final String[] content = new String[SLOTS];
    private final String[] title = new String[SLOTS];
    private boolean showWidget = true;
    private String corner = "Top right";
    private String placement = "Anchored";
    private int widgetX;
    private int widgetY;
    private String icon = "mdi:qrcode";
    private double qrSize = 320;
    private int openSlot = -1;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        configure(settings);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        String[] previous = content.clone();
        for (int slot = 0; slot < SLOTS; slot++) {
            String prefix = "qr" + (slot + 1);
            title[slot] = string(settings.get(prefix + "Title"));
            content[slot] = buildContent(settings, prefix);
        }
        showWidget = Boolean.TRUE.equals(settings.get("showWidget"));
        corner = string(settings.get("corner"));
        placement = string(settings.get("placement"));
        if (placement.isEmpty()) placement = "Anchored";
        widgetX = dpSetting(settings.get("widgetX"));
        widgetY = dpSetting(settings.get("widgetY"));
        icon = string(settings.get("icon"));
        Object size = settings.get("qrSize");
        qrSize = size instanceof Number ? ((Number) size).doubleValue() : 320;
        if (host == null) return;
        if (showWidget) showWidget();
        else {
            try {
                host.hideOverlay(WIDGET);
            } catch (UnsupportedOperationException olderHost) {}
        }
        // A settings edit refreshes the open card so it never shows a stale code.
        if (openSlot >= 0 && !content[openSlot].equals(previous[openSlot])) showCode(openSlot);
    }

    /**
     * What a slot's QR encodes, from its settings: a Wi-Fi join string, a URL
     * or the plain text as typed.
     */
    static String buildContent(Map<String, Object> settings, String prefix) {
        String type = string(settings.get(prefix + "Type"));
        if ("Text".equals(type)) return string(settings.get(prefix + "Text"));
        if ("URL".equals(type)) return string(settings.get(prefix + "Url"));
        Map<String, Object> wifi = new java.util.LinkedHashMap<>();
        wifi.put("wifiSsid", settings.get(prefix + "Ssid"));
        wifi.put("wifiSecurity", settings.get(prefix + "Security"));
        wifi.put("wifiPassword", settings.get(prefix + "Password"));
        return wifiPayload(wifi);
    }

    /** The standard Wi-Fi join string for the configured network. */
    static String wifiPayload(Map<String, Object> settings) {
        String ssid = string(settings.get("wifiSsid"));
        if (ssid.isEmpty()) return "";
        if ("Open (no password)".equals(string(settings.get("wifiSecurity"))))
            return "WIFI:T:nopass;S:" + escape(ssid) + ";;";
        return "WIFI:T:WPA;S:" + escape(ssid) + ";P:" + escape(string(settings.get("wifiPassword"))) + ";;";
    }

    private static String string(Object value) {
        return value instanceof String ? (String) value : "";
    }

    /** Escapes the Wi-Fi string's reserved characters with a backslash. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"");
    }

    /**
     * The four Show QR code actions, one per slot. Users bind them to gestures
     * in Gestures > Run a plugin action, and can also keep them in the kiosk
     * drawer or expose them as Home Assistant buttons from the plugin subpage.
     */
    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (("showQr" + (slot == 0 ? "" : slot + 1)).equals(command)) {
                showCode(slot);
                return;
            }
        }
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("overlay.closed".equals(event) && payload != null && CODE.equals(payload.get("key"))) openSlot = -1;
    }

    @Override
    public synchronized void stop() {
        host = null;
    }

    private synchronized void showWidget() {
        if (host == null) return;
        if (title[0].isEmpty() || content[0].isEmpty()) {
            try {
                host.hideOverlay(WIDGET);
            } catch (UnsupportedOperationException olderHost) {}
            host.status("Set the first QR code's title and content to show the corner widget.", true);
            return;
        }
        boolean free = "Free".equals(placement);
        // Anchored sits in the chosen corner; Free carries the pill's center as an
        // offset from the middle of the kiosk, wherever the last drag left it.
        OverlaySpec spec = OverlaySpec.at(free ? OverlaySpec.CENTER : anchor(corner));
        spec = spec.inset(free ? 0 : 16);
        if (free) spec = spec.offset(widgetX, widgetY);
        spec = spec
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(WIDGET, spec, new Widget());
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    private synchronized void showCode(int slot) {
        if (host == null) return;
        if (content[slot].isEmpty()) {
            host.status("QR code " + (slot + 1) + " has no content. Set it in the plugin settings.", true);
            return;
        }
        openSlot = slot;
        OverlaySpec spec = OverlaySpec.fullScreen()
            .closeOnOutsideTouch(true)
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(CODE, spec, new Code(slot));
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    /** A saved drag position in dp, 0 when never moved. */
    private static int dpSetting(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String anchor(String corner) {
        if ("Top left".equals(corner)) return OverlaySpec.TOP_LEFT;
        if ("Bottom left".equals(corner)) return OverlaySpec.BOTTOM_LEFT;
        if ("Bottom right".equals(corner)) return OverlaySpec.BOTTOM_RIGHT;
        return OverlaySpec.TOP_RIGHT;
    }

    /**
     * Click listeners run on the main thread, outside the callbacks KS guards.
     * A session can end between a tap and the host call, so an exception is dropped.
     */
    private static void tap(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException sessionEnded) {}
    }

    private static Drawable icon(KsTheme theme, String name, String role) {
        try {
            return theme.icon(name, role);
        } catch (LinkageError olderHost) {
            return null;
        }
    }

    /** The corner widget: a KS themed pill with the chosen icon and the first QR code's title. */
    private final class Widget implements OverlayFactory {
        private static final int MAX_DRAG_SLOPE = 12;
        private float downX;
        private float downY;
        private boolean dragging;

        @Override
        public View create(final Context context, KsTheme theme) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER);
            TextView label = new TextView(context);
            label.setText(title[0]);
            root.addView(label);
            // A drag moves the widget anywhere on the kiosk; a plain tap opens the
            // QR. The touch listener owns the whole stream, so the tap is measured
            // against the same slop that starts the drag.
            root.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        dragging = false;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float density = view.getResources().getDisplayMetrics().density;
                        if (!dragging) {
                            float moved = Math.max(
                                Math.abs(event.getRawX() - downX),
                                Math.abs(event.getRawY() - downY));
                            if (moved / density > MAX_DRAG_SLOPE) dragging = true;
                        }
                        if (dragging) {
                            // Slide the pill under the finger without rebuilding the
                            // overlay, which would interrupt the gesture.
                            view.setTranslationX(view.getTranslationX() + event.getRawX() - downX);
                            view.setTranslationY(view.getTranslationY() + event.getRawY() - downY);
                            downX = event.getRawX();
                            downY = event.getRawY();
                        }
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: {
                        boolean wasDragging = dragging;
                        dragging = false;
                        if (wasDragging) tap(() -> dropWidget(view));
                        else tap(() -> showCode(0));
                        return true;
                    }
                }
                return false;
            });
            onThemeChanged(root, theme);
            return root;
        }

        @Override
        public void onThemeChanged(View view, KsTheme theme) {
            LinearLayout root = (LinearLayout) view;
            GradientDrawable pill = theme.card();
            pill.setCornerRadius(theme.px(100));
            root.setBackground(pill);
            root.setPadding(theme.px(20), theme.px(12), theme.px(20), theme.px(12));
            TextView label = (TextView) root.getChildAt(0);
            theme.styleText(label, 15, 600, "onSurface");
            Drawable glyph = icon(theme, icon.isEmpty() ? "mdi:qrcode" : icon, "primary");
            if (glyph != null) glyph.setBounds(0, 0, theme.px(24), theme.px(24));
            label.setCompoundDrawablesRelative(glyph, null, null, null);
            label.setCompoundDrawablePadding(theme.px(10));
        }
    }

    /**
     * The finger left the pill somewhere new. Turn the translated view into a
     * saved free position, persist it and rebuild the overlay there, so the spot
     * survives settings edits, theme changes and app restarts.
     */
    private synchronized void dropWidget(View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        float density = view.getResources().getDisplayMetrics().density;
        int screenW = Math.round(view.getResources().getDisplayMetrics().widthPixels / density);
        int screenH = Math.round(view.getResources().getDisplayMetrics().heightPixels / density);
        // The pill's center, in dp, relative to the kiosk's center.
        int centerX = Math.round(at[0] / density + view.getWidth() / 2f / density);
        int centerY = Math.round(at[1] / density + view.getHeight() / 2f / density);
        widgetX = Math.max(-screenW / 2, Math.min(screenW / 2, centerX - screenW / 2));
        widgetY = Math.max(-screenH / 2, Math.min(screenH / 2, centerY - screenH / 2));
        placement = "Free";
        try {
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            values.put("placement", "Free");
            values.put("widgetX", widgetX);
            values.put("widgetY", widgetY);
            host.updateSettings(values);
        } catch (UnsupportedOperationException olderHost) {}
        showWidget();
    }

    /** The full screen card: a white QR panel over a dimmed kiosk. */
    private final class Code implements OverlayFactory {
        private final int slot;

        Code(int slot) {
            this.slot = slot;
        }

        @Override
        public View create(Context context, KsTheme theme) {
            FrameLayout root = new FrameLayout(context);
            root.setOnClickListener(view -> tap(QrPlugin.this::closeCode));
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            // Taps on the card stay on the card.
            card.setClickable(true);
            TextView heading = new TextView(context);
            heading.setText(title[slot]);
            heading.setGravity(Gravity.CENTER);
            card.addView(heading);
            ImageView image = new ImageView(context);
            image.setImageBitmap(bitmap(buildQr(content[slot])));
            int size = (int) Math.max(160, Math.min(qrSize, 480));
            LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(theme.px(size), theme.px(size));
            imageParams.topMargin = theme.px(16);
            card.addView(image, imageParams);
            Button close = new Button(context);
            close.setText("Close");
            LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            closeParams.topMargin = theme.px(24);
            closeParams.gravity = Gravity.CENTER_HORIZONTAL;
            close.setOnClickListener(view -> tap(QrPlugin.this::closeCode));
            card.addView(close, closeParams);
            int width = Math.min(theme.px(420),
                context.getResources().getDisplayMetrics().widthPixels - theme.px(48));
            FrameLayout.LayoutParams cardParams =
                new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            root.addView(card, cardParams);
            onThemeChanged(root, theme);
            return root;
        }

        @Override
        public void onThemeChanged(View view, KsTheme theme) {
            FrameLayout root = (FrameLayout) view;
            root.setBackgroundColor(Color.argb(160, 0, 0, 0));
            LinearLayout card = (LinearLayout) root.getChildAt(0);
            card.setBackground(theme.card());
            card.setPadding(theme.px(theme.inset()), theme.px(theme.inset()),
                theme.px(theme.inset()), theme.px(theme.inset()));
            theme.styleText((TextView) card.getChildAt(0), 22, 600, "onSurface");
            theme.stylePill((TextView) card.getChildAt(2), false);
            ImageView image = (ImageView) card.getChildAt(1);
            GradientDrawable frame = new GradientDrawable();
            frame.setCornerRadius(theme.px(theme.radiusControl()));
            frame.setColor(Color.WHITE);
            image.setBackground(frame);
            image.setPadding(theme.px(12), theme.px(12), theme.px(12), theme.px(12));
        }
    }

    private static QrCode buildQr(String content) {
        return QrCode.encodeText(content, QrCode.Ecc.MEDIUM);
    }

    /** The QR matrix as a black and white Android bitmap, with its quiet zone. */
    private static Bitmap bitmap(QrCode qr) {
        int border = 4;
        int side = qr.size + border * 2;
        // Around 1080 pixels on the long side keeps the modules crisp on every display.
        int scale = Math.max(4, 1080 / side);
        Bitmap bitmap = Bitmap.createBitmap(side * scale, side * scale, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        Canvas canvas = new Canvas(bitmap);
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (!qr.getModule(x, y)) continue;
                canvas.drawRect((x + border) * scale, (y + border) * scale,
                    (x + border + 1) * scale, (y + border + 1) * scale, paint);
            }
        }
        return bitmap;
    }

    private synchronized void closeCode() {
        openSlot = -1;
        if (host != null) host.hideOverlay(CODE);
    }
}
