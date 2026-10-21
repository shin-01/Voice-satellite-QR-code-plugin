// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.nayuki.qrcodegen.QrCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A hideable bar of QR code buttons, docked anywhere on the kiosk's frame.
 * Expanded it shows one icon per QR code, plus a chevron that collapses it to a
 * lone arrow handle; collapsed it shows just that arrow, and tapping it brings
 * the bar back. Dragging moves the whole bar to another spot on the frame, and
 * the spot persists. Each QR encodes a URL, plain text or Wi-Fi credentials.
 * QR rendering uses Nayuki's MIT licensed generator (io.nayuki.qrcodegen),
 * bundled with this plugin.
 */
public final class QrPlugin implements KioskPlugin {
    private static final int SLOTS = 4;
    private static final String BAR = "qr_bar";
    private static final String CODE = "qr_code";
    /** Full screen, visual only: carries the bar during a drag, so it is never clipped. */
    private static final String DRAG = "qr_drag";

    private PluginHost host;
    private final String[] content = new String[SLOTS];
    private final String[] title = new String[SLOTS];
    private final String[] barIcon = new String[SLOTS];
    private boolean showBar = true;
    private boolean expanded = true;
    private String corner = "Top right";
    private String placement = "Anchored";
    private int barX;
    private int barY;
    private double qrSize = 320;
    /** The open card's slot, or -1 when none is open. */
    private int openSlot = -1;
    /** The drag's ghost state. */
    private boolean dragging;
    private float dragX;
    private float dragY;
    private float dragDensity;
    private float dragScreenW;
    private float dragScreenH;
    private View dragLayerView;

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
            barIcon[slot] = string(settings.get(prefix + "Icon"));
            if (barIcon[slot].isEmpty()) barIcon[slot] = "mdi:qrcode";
            content[slot] = buildContent(settings, prefix);
        }
        showBar = Boolean.TRUE.equals(settings.get("showBar"));
        corner = string(settings.get("corner"));
        placement = string(settings.get("placement"));
        if (placement.isEmpty()) placement = "Anchored";
        barX = dpSetting(settings.get("barX"));
        barY = dpSetting(settings.get("barY"));
        Object size = settings.get("qrSize");
        qrSize = size instanceof Number ? ((Number) size).doubleValue() : 320;
        if (host == null) return;
        applyBar();
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

    /** A saved drag position in dp, 0 when never moved. */
    private static int dpSetting(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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
        if ("overlay.closed".equals(event) && payload != null && CODE.equals(payload.get("key"))) {
            openSlot = -1;
            if (showBar && !dragging) applyBar();
        }
    }

    @Override
    public synchronized void stop() {
        host = null;
    }

    /** The QR codes that have both a title and content, in slot order. */
    private int[] activeSlots() {
        List<Integer> active = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!title[slot].isEmpty() && !content[slot].isEmpty()) active.add(slot);
        }
        int[] out = new int[active.size()];
        for (int i = 0; i < out.length; i++) out[i] = active.get(i);
        return out;
    }

    /** Show, hide or rebuild the bar to match the settings. */
    private synchronized void applyBar() {
        if (host == null) return;
        int[] active = activeSlots();
        if (!showBar || active.length == 0) {
            hide(BAR);
            if (active.length == 0 && showBar)
                host.status("Give a QR code both a title and content to show the bar.", true);
            return;
        }
        boolean free = "Free".equals(placement);
        // Anchored sits on the chosen corner of the frame; Free carries the bar's
        // center as an offset from the middle of the kiosk, wherever the last drag left it.
        OverlaySpec spec = OverlaySpec.at(free ? OverlaySpec.CENTER : anchor(corner));
        spec = spec.inset(free ? 0 : 16);
        if (free) spec = spec.offset(barX, barY);
        spec = spec
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(BAR, spec, new Bar(active));
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    private void hide(String key) {
        try {
            host.hideOverlay(key);
        } catch (UnsupportedOperationException olderHost) {}
    }

    private synchronized void showCode(int slot) {
        if (host == null) return;
        if (content[slot].isEmpty()) {
            host.status("QR code " + (slot + 1) + " has no content. Set it in the plugin settings.", true);
            return;
        }
        hide(BAR);
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

    private static String anchor(String corner) {
        if ("Top left".equals(corner)) return OverlaySpec.TOP_LEFT;
        if ("Bottom left".equals(corner)) return OverlaySpec.BOTTOM_LEFT;
        if ("Bottom right".equals(corner)) return OverlaySpec.BOTTOM_RIGHT;
        return OverlaySpec.TOP_RIGHT;
    }

    /**
     * The arrow the collapsed handle and the collapse button wear, pointing
     * from the bar's edge into the kiosk.
     */
    private String chevron() {
        String edge = "Free".equals(placement) ? freeEdge() : anchoredEdge();
        if ("top".equals(edge)) return "mdi:chevron-down";
        if ("bottom".equals(edge)) return "mdi:chevron-up";
        if ("left".equals(edge)) return "mdi:chevron-right";
        return "mdi:chevron-left";
    }

    /** The frame edge an anchored corner sits on. */
    private String anchoredEdge() {
        return corner.startsWith("Top") ? "top" : "bottom";
    }

    /** The frame edge a freely placed bar is closest to. */
    private String freeEdge() {
        if (Math.abs(barY) >= Math.abs(barX)) return barY <= 0 ? "top" : "bottom";
        return barX <= 0 ? "left" : "right";
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

    /**
     * The bar's touch stream. A drag moves the bar anywhere on the kiosk; a
     * plain tap opens the icon under the finger, toggles the collapse on the
     * chevron, or expands when collapsed. The listener owns the whole stream:
     * returning false on DOWN would hand it to a child and the drag would
     * never start.
     */
    private View.OnTouchListener barTouch(final int[] slots) {
        return new View.OnTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float density = view.getResources().getDisplayMetrics().density;
                        if (!dragging) {
                            float moved = Math.max(
                                Math.abs(event.getRawX() - downX),
                                Math.abs(event.getRawY() - downY));
                            if (moved / density > 12) {
                                tap(() -> startDrag(view, event.getRawX(), event.getRawY()));
                            }
                        }
                        if (dragging) tap(() -> moveDrag(event.getRawX(), event.getRawY()));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        boolean wasDragging = dragging;
                        dragging = false;
                        if (wasDragging) {
                            tap(QrPlugin.this::dropBar);
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            tap(() -> handleTap(view, event, slots));
                        }
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /** What a tap on the bar does: an icon opens its QR, the chevron toggles. */
    private synchronized void handleTap(View view, MotionEvent event, int[] slots) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Rect hit = new Rect();
            child.getHitRect(hit);
            if (!hit.contains((int) event.getX(), (int) event.getY())) continue;
            Object tag = child.getTag();
            if (tag instanceof Integer) {
                showCode((Integer) tag);
                return;
            }
            // The chevron: collapse when expanded, expand when collapsed.
            expanded = !expanded;
            applyBar();
            return;
        }
    }

    /**
     * KS clips an overlay's view to the overlay's own rectangle, so a bar
     * translated out of its bounds would disappear. The drag instead hands it
     * to a full screen, visual-only layer that follows the finger everywhere,
     * while the bar's own view stays alive with the touch stream, transparent,
     * until the release rebuilds the bar at the new spot.
     */
    private synchronized void startDrag(View view, float rawX, float rawY) {
        if (host == null || dragging) return;
        dragging = true;
        dragX = rawX;
        dragY = rawY;
        dragDensity = view.getResources().getDisplayMetrics().density;
        dragScreenW = view.getResources().getDisplayMetrics().widthPixels;
        dragScreenH = view.getResources().getDisplayMetrics().heightPixels;
        try {
            host.showOverlay(DRAG, OverlaySpec.fullScreen().touchable(false), new DragLayer());
            view.setAlpha(0);
        } catch (UnsupportedOperationException olderHost) {
            dragging = false;
        }
    }

    /** The bar follows the finger on the drag layer. */
    private synchronized void moveDrag(float rawX, float rawY) {
        if (!dragging) return;
        dragX = rawX;
        dragY = rawY;
        if (dragLayerView != null) dragLayerView.invalidate();
    }

    /**
     * The finger left the bar somewhere new. Turn the drop point into a saved
     * free position, persist it and rebuild the bar, so the spot survives
     * settings edits, theme changes and app restarts.
     */
    private synchronized void dropBar() {
        if (!dragging) return;
        dragging = false;
        dragLayerView = null;
        hide(DRAG);
        if (dragDensity <= 0) return;
        int screenW = Math.round(dragScreenW / dragDensity);
        int screenH = Math.round(dragScreenH / dragDensity);
        // The bar's center at the drop, in dp, relative to the kiosk's center.
        int centerX = Math.round(dragX / dragDensity);
        int centerY = Math.round(dragY / dragDensity);
        barX = Math.max(-screenW / 2, Math.min(screenW / 2, centerX - screenW / 2));
        barY = Math.max(-screenH / 2, Math.min(screenH / 2, centerY - screenH / 2));
        placement = "Free";
        try {
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            values.put("placement", "Free");
            values.put("barX", barX);
            values.put("barY", barY);
            host.updateSettings(values);
        } catch (UnsupportedOperationException olderHost) {}
        applyBar();
    }

    /** A Material 3 role with a fallback for KS versions that do not provide it. */
    private static int color(KsTheme theme, String role, String fallback) {
        try {
            return theme.color(role);
        } catch (IllegalArgumentException missing) {
            return theme.color(fallback);
        }
    }

    /**
     * Pressed feedback without animation, the navbar's approach: an onSurface
     * wash that appears on touch down and goes on release. A ripple animates
     * for about 300 ms, and every one of its frames has to cross KS's overlay
     * texture, which arrives late on a low-power panel.
     */
    private static Drawable pressable(KsTheme theme, float radiusPx) {
        int ink = theme.color("onSurface");
        GradientDrawable wash = new GradientDrawable();
        wash.setCornerRadius(radiusPx);
        wash.setColor(Color.argb(36, Color.red(ink), Color.green(ink), Color.blue(ink)));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.setEnterFadeDuration(0);
        states.setExitFadeDuration(0);
        states.addState(new int[] {android.R.attr.state_pressed}, wash);
        states.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    /**
     * The hideable bar, styled after dimatx's kiosk-satellite-navbar. Expanded it
     * shows one icon per QR code in a rounded indicator, plus a chevron that
     * collapses it; collapsed it shows the lone chevron handle that brings it
     * back.
     */
    private final class Bar implements OverlayFactory {
        private final int[] slots;

        Bar(int[] slots) {
            this.slots = slots;
        }

        @Override
        public View create(Context context, KsTheme theme) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            if (expanded) {
                for (int slot : slots) {
                    LinearLayout cell = cell(context, barIcon[slot]);
                    cell.setTag(slot);
                    root.addView(cell);
                }
            }
            LinearLayout chevron = cell(context, chevron());
            chevron.setTag("chevron");
            root.addView(chevron);
            root.setOnTouchListener(barTouch(slots));
            onThemeChanged(root, theme);
            return root;
        }

        /** One tappable cell: a rounded indicator with the icon centered in it. */
        private LinearLayout cell(Context context, String name) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER);
            android.widget.FrameLayout indicator = new android.widget.FrameLayout(context);
            android.widget.ImageView icon = new android.widget.ImageView(context);
            icon.setTag(name);
            indicator.addView(icon, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            cell.addView(indicator);
            return cell;
        }

        @Override
        public void onThemeChanged(View view, KsTheme theme) {
            LinearLayout root = (LinearLayout) view;
            // The navbar's surface and padding: the KS card and a tight inset.
            root.setBackground(theme.card());
            root.setPadding(theme.px(8), theme.px(6), theme.px(8), theme.px(6));
            for (int i = 0; i < root.getChildCount(); i++) {
                LinearLayout cell = (LinearLayout) root.getChildAt(i);
                cell.setMinimumWidth(theme.px(48));
                cell.setMinimumHeight(theme.px(48));
                cell.setPadding(theme.px(4), theme.px(4), theme.px(4), theme.px(4));
                cell.setBackground(pressable(theme, theme.px(theme.radiusRow())));
                android.widget.FrameLayout indicator = (android.widget.FrameLayout) cell.getChildAt(0);
                GradientDrawable pill = new GradientDrawable();
                pill.setCornerRadius(theme.px(16));
                pill.setColor(color(theme, "secondaryContainer", "primaryContainer"));
                indicator.setBackground(pill);
                indicator.setLayoutParams(new LinearLayout.LayoutParams(theme.px(44), theme.px(32)));
                android.widget.ImageView icon = (android.widget.ImageView) indicator.getChildAt(0);
                LinearLayout.LayoutParams glyph = new LinearLayout.LayoutParams(theme.px(24), theme.px(24));
                icon.setLayoutParams(glyph);
                String name = (String) icon.getTag();
                Drawable glyph2 = icon(theme, name, "onSurfaceVariant");
                if (glyph2 != null)
                    glyph2.setTint(color(theme, "onSecondaryContainer", "onSurface"));
                icon.setImageDrawable(glyph2);
            }
        }
    }

    /**
     * The full screen, visual-only layer a drag draws on. It paints the bar's
     * handle at the finger, in the same KS style, so the drag is visible
     * everywhere without the bar's clipping. Touches pass through: the bar's
     * own view still owns the gesture.
     */
    private final class DragLayer implements OverlayFactory {
        @Override
        public View create(Context context, final KsTheme theme) {
            final View view = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    float density = getResources().getDisplayMetrics().density;
                    int pillW = (int) (56 * density);
                    int pillH = (int) (48 * density);
                    float left = dragX - pillW / 2f;
                    float top = dragY - pillH / 2f;
                    GradientDrawable pill = theme.card();
                    pill.setCornerRadius(theme.px(100));
                    pill.setBounds((int) left, (int) top, (int) (left + pillW), (int) (top + pillH));
                    pill.draw(canvas);
                    Drawable glyph = icon(theme, "mdi:drag", "primary");
                    if (glyph != null) {
                        int g = (int) (24 * density);
                        glyph.setBounds((int) (left + pillW / 2f - g / 2f), (int) (top + pillH / 2f - g / 2f),
                            (int) (left + pillW / 2f + g / 2f), (int) (top + pillH / 2f + g / 2f));
                        glyph.draw(canvas);
                    }
                }
            };
            dragLayerView = view;
            return view;
        }

        @Override
        public void onThemeChanged(View view, KsTheme theme) {
            view.invalidate();
        }
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
            root.addView(card, new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
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
