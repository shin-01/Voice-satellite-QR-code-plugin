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
 * A small corner widget that shows the chosen QR code on tap. The QR content is
 * a URL, plain text or this kiosk's Wi-Fi credentials, handy for sharing any of
 * them with a phone. The same QR also opens from the Show QR code action, which
 * users can assign to a gesture, the kiosk drawer or a Home Assistant button.
 * QR rendering uses Nayuki's MIT licensed generator (io.nayuki.qrcodegen),
 * bundled with this plugin.
 */
public final class QrPlugin implements KioskPlugin {
    private static final String WIDGET = "qr_widget";
    private static final String CODE = "qr_code";

    private PluginHost host;
    private String content = "";
    private String contentType = "Wi-Fi";
    private String title = "Wi-Fi";
    private String corner = "Top right";
    private String icon = "mdi:qrcode";
    private boolean showWidget = true;
    private double qrSize = 320;
    private boolean codeOpen;

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        configure(settings);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        String previous = content;
        String previousType = contentType;
        contentType = string(settings.get("contentType"));
        if (contentType.isEmpty()) contentType = "Wi-Fi";
        title = string(settings.get("title"));
        corner = string(settings.get("corner"));
        icon = string(settings.get("icon"));
        showWidget = Boolean.TRUE.equals(settings.get("showWidget"));
        Object size = settings.get("qrSize");
        qrSize = size instanceof Number ? ((Number) size).doubleValue() : 320;
        content = buildContent(settings);
        if (host == null) return;
        if (showWidget) showWidget();
        else {
            try {
                host.hideOverlay(WIDGET);
            } catch (UnsupportedOperationException olderHost) {}
        }
        // A settings edit refreshes the open card so it never shows a stale code.
        if (codeOpen && (!content.equals(previous) || !contentType.equals(previousType))) showCode();
    }

    /**
     * What the QR encodes, from the settings of the selected content type:
     * a Wi-Fi join string, a URL or the plain text as typed.
     */
    static String buildContent(Map<String, Object> settings) {
        String type = string(settings.get("contentType"));
        if ("Text".equals(type)) return string(settings.get("textContent"));
        if ("URL".equals(type)) return string(settings.get("urlContent"));
        return wifiPayload(settings);
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

    /** The Show QR code action, reachable from a gesture, the drawer, Home Assistant and the widget's tap. */
    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        if ("showQr".equals(command)) showCode();
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("overlay.closed".equals(event) && payload != null && CODE.equals(payload.get("key"))) codeOpen = false;
    }

    @Override
    public synchronized void stop() {
        host = null;
    }

    private synchronized void showWidget() {
        if (host == null) return;
        if (title.isEmpty() || content.isEmpty()) {
            try {
                host.hideOverlay(WIDGET);
            } catch (UnsupportedOperationException olderHost) {}
            host.status("Set the widget title and the QR content in the plugin settings.", true);
            return;
        }
        OverlaySpec spec = OverlaySpec.at(anchor(corner))
            .inset(16)
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(WIDGET, spec, new Widget());
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    private synchronized void showCode() {
        if (host == null) return;
        if (content.isEmpty()) {
            host.status("Set the QR content in the plugin settings first.", true);
            return;
        }
        codeOpen = true;
        OverlaySpec spec = OverlaySpec.fullScreen()
            .closeOnOutsideTouch(true)
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(CODE, spec, new Code());
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
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

    /** The corner widget: a KS themed pill with the chosen icon and title. */
    private final class Widget implements OverlayFactory {
        @Override
        public View create(Context context, KsTheme theme) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER);
            TextView label = new TextView(context);
            label.setText(title);
            root.addView(label);
            root.setOnClickListener(view -> tap(QrPlugin.this::showCode));
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

    /** The full screen card: a white QR panel over a dimmed kiosk. */
    private final class Code implements OverlayFactory {
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
            heading.setText(title);
            heading.setGravity(Gravity.CENTER);
            card.addView(heading);
            ImageView image = new ImageView(context);
            image.setImageBitmap(bitmap(buildQr()));
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

    private synchronized QrCode buildQr() {
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
        codeOpen = false;
        if (host != null) host.hideOverlay(CODE);
    }
}
