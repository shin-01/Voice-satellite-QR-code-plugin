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
 * A pill per QR code, each with its own icon. Grouped pills share one overlay;
 * a detached pill floats on its own, anchored to its corner or dragged
 * anywhere. Tapping a group of several QR codes first asks which to show, in a
 * modal; tapping a single pill or a detached pill opens its QR code directly.
 * QR rendering uses Nayuki's MIT licensed generator (io.nayuki.qrcodegen),
 * bundled with this plugin.
 */
public final class QrPlugin implements KioskPlugin {
    private static final int SLOTS = 4;
    /** The one pill that holds every grouped QR code. */
    private static final String GROUP = "qr_group";
    private static final String CODE = "qr_code";
    /** Full screen, visual only: carries the pill during a drag, so it is never clipped. */
    private static final String DRAG = "qr_drag";
    /** One overlay per detached pill: qr_pill_1 to qr_pill_4. */
    private static final String PILL = "qr_pill_";

    private PluginHost host;
    private final String[] content = new String[SLOTS];
    private final String[] title = new String[SLOTS];
    private final String[] pillIcon = new String[SLOTS];
    private final boolean[] detached = new boolean[SLOTS];
    /** A detached pill's saved drag position, in dp from the kiosk's center. */
    private final int[] pillX = new int[SLOTS];
    private final int[] pillY = new int[SLOTS];
    private final boolean[] pillFree = new boolean[SLOTS];
    private boolean showWidget = true;
    private String corner = "Top right";
    private String placement = "Anchored";
    private int widgetX;
    private int widgetY;
    private double qrSize = 320;
    /** The open card's slot, -1 for none and -2 for the chooser. */
    private int openSlot = -1;
    /** The drag's target: -1 for the group, otherwise a slot index. */
    private boolean dragging;
    private int dragTarget = -1;
    private String dragIcon = "mdi:qrcode";
    private float dragX;
    private float dragY;
    private float dragDensity;
    private float dragScreenW;
    private float dragScreenH;
    /** The live drag layer view, invalidated as the finger moves. */
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
            pillIcon[slot] = string(settings.get(prefix + "Icon"));
            if (pillIcon[slot].isEmpty()) pillIcon[slot] = "mdi:qrcode";
            detached[slot] = "Detached".equals(string(settings.get(prefix + "Pill")));
            pillX[slot] = dpSetting(settings.get(prefix + "X"));
            pillY[slot] = dpSetting(settings.get(prefix + "Y"));
            // A pill with a saved position is free; a drag back to the exact center re-anchors it.
            pillFree[slot] = pillX[slot] != 0 || pillY[slot] != 0;
            content[slot] = buildContent(settings, prefix);
        }
        showWidget = Boolean.TRUE.equals(settings.get("showWidget"));
        corner = string(settings.get("corner"));
        placement = string(settings.get("placement"));
        if (placement.isEmpty()) placement = "Anchored";
        widgetX = dpSetting(settings.get("widgetX"));
        widgetY = dpSetting(settings.get("widgetY"));
        Object size = settings.get("qrSize");
        qrSize = size instanceof Number ? ((Number) size).doubleValue() : 320;
        if (host == null) return;
        if (showWidget) applyPills();
        else hidePills();
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
            if (showWidget && !dragging) applyPills();
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

    /** Show or hide every pill to match the settings. */
    private synchronized void applyPills() {
        if (host == null) return;
        int[] active = activeSlots();
        List<Integer> grouped = new ArrayList<>();
        for (int slot : active) {
            if (!detached[slot]) grouped.add(slot);
        }
        if (!grouped.isEmpty()) {
            int[] slots = new int[grouped.size()];
            for (int i = 0; i < slots.length; i++) slots[i] = grouped.get(i);
            showGroup(slots);
        } else {
            hide(GROUP);
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (showWidget && detached[slot] && !title[slot].isEmpty() && !content[slot].isEmpty()) showPill(slot);
            else hide(PILL + (slot + 1));
        }
        if (active.length == 0 && !grouped.isEmpty()) hide(GROUP);
        if (active.length == 0) {
            // Nothing to show yet; the group factory handles an empty list by hiding.
        }
    }

    private synchronized void hidePills() {
        hide(GROUP);
        for (int slot = 1; slot <= SLOTS; slot++) hide(PILL + slot);
    }

    private void hide(String key) {
        try {
            host.hideOverlay(key);
        } catch (UnsupportedOperationException olderHost) {}
    }

    /** The one group pill: a card with one icon per grouped QR code. */
    private synchronized void showGroup(int[] grouped) {
        boolean free = "Free".equals(placement);
        OverlaySpec spec = OverlaySpec.at(free ? OverlaySpec.CENTER : anchor(corner));
        spec = spec.inset(free ? 0 : 16);
        if (free) spec = spec.offset(widgetX, widgetY);
        spec = spec
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(GROUP, spec, new GroupPill(grouped));
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    /** A detached pill: one icon, on its own overlay. */
    private synchronized void showPill(int slot) {
        boolean free = pillFree[slot];
        OverlaySpec spec = OverlaySpec.at(free ? OverlaySpec.CENTER : pillAnchor(slot));
        spec = spec.inset(free ? 0 : 16);
        if (free) spec = spec.offset(pillX[slot], pillY[slot]);
        spec = spec
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(PILL + (slot + 1), spec, new IconPill(slot));
        } catch (UnsupportedOperationException olderHost) {
            host.status("Native overlays need a newer Kiosk Satellite.", true);
        }
    }

    /** Each detached pill keeps its own corner until it is dragged. */
    private static String pillAnchor(int slot) {
        switch (slot) {
            case 0: return OverlaySpec.TOP_LEFT;
            case 1: return OverlaySpec.TOP_RIGHT;
            case 2: return OverlaySpec.BOTTOM_LEFT;
            default: return OverlaySpec.BOTTOM_RIGHT;
        }
    }

    private synchronized void showCode(int slot) {
        if (host == null) return;
        if (content[slot].isEmpty()) {
            host.status("QR code " + (slot + 1) + " has no content. Set it in the plugin settings.", true);
            return;
        }
        // The card takes the pills' overlay slots; closing it brings them back.
        hidePills();
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

    /** The modal that asks which grouped QR code to show. */
    private synchronized void showChooser(int[] grouped) {
        hidePills();
        openSlot = -2;
        OverlaySpec spec = OverlaySpec.fullScreen()
            .closeOnOutsideTouch(true)
            .enter(OverlaySpec.FADE)
            .exit(OverlaySpec.FADE)
            .transitionDuration(200);
        try {
            host.showOverlay(CODE, spec, new Chooser(grouped));
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

    /**
     * The touch stream of a pill. A drag moves it anywhere on the kiosk; a
     * plain tap opens its QR code, or the chooser when the group holds more
     * than one. The listener owns the whole stream: returning false on DOWN
     * would hand it to a child and the drag would never start.
     */
    private View.OnTouchListener pillTouch(final int target, final int[] tapSlots) {
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
                                tap(() -> startDrag(view, target, event.getRawX(), event.getRawY()));
                            }
                        }
                        if (dragging) tap(() -> moveDrag(event.getRawX(), event.getRawY()));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        boolean wasDragging = dragging;
                        dragging = false;
                        if (wasDragging) tap(QrPlugin.this::dropWidget);
                        else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            if (tapSlots.length == 1) tap(() -> showCode(tapSlots[0]));
                            else if (tapSlots.length > 1) tap(() -> showChooser(tapSlots));
                        }
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * KS clips an overlay's view to the overlay's own rectangle, so a pill
     * translated out of its bounds would disappear. The drag instead hides
     * the other pills to stay within the overlay limit, hands this one to a
     * full screen, visual-only layer that follows the finger everywhere, and
     * keeps the pill's own view alive with the touch stream, its content
     * transparent, until the release rebuilds it at the new spot.
     */
    private synchronized void startDrag(View view, int target, float rawX, float rawY) {
        if (host == null || dragging) return;
        dragging = true;
        dragTarget = target;
        dragIcon = target >= 0 ? pillIcon[target] : pillIcon[0];
        dragX = rawX;
        dragY = rawY;
        dragDensity = view.getResources().getDisplayMetrics().density;
        dragScreenW = view.getResources().getDisplayMetrics().widthPixels;
        dragScreenH = view.getResources().getDisplayMetrics().heightPixels;
        // The layer needs an overlay slot of its own, so the other pills step aside.
        if (target >= 0) {
            hide(GROUP);
            for (int slot = 0; slot < SLOTS; slot++) {
                if (slot != target) hide(PILL + (slot + 1));
            }
        } else {
            for (int slot = 1; slot <= SLOTS; slot++) hide(PILL + slot);
        }
        try {
            host.showOverlay(DRAG, OverlaySpec.fullScreen().touchable(false), new DragLayer());
            view.setAlpha(0);
        } catch (UnsupportedOperationException olderHost) {
            dragging = false;
        }
    }

    /** The pill follows the finger on the drag layer. */
    private synchronized void moveDrag(float rawX, float rawY) {
        if (!dragging) return;
        dragX = rawX;
        dragY = rawY;
        if (dragLayerView != null) dragLayerView.invalidate();
    }

    /**
     * The finger left the pill somewhere new. Turn the drop point into a saved
     * free position, persist it and rebuild the pills, so the spot survives
     * settings edits, theme changes and app restarts.
     */
    private synchronized void dropWidget() {
        if (!dragging) return;
        dragging = false;
        dragLayerView = null;
        hide(DRAG);
        int target = dragTarget;
        dragTarget = -1;
        if (dragDensity <= 0) return;
        int screenW = Math.round(dragScreenW / dragDensity);
        int screenH = Math.round(dragScreenH / dragDensity);
        // The pill's center at the drop, in dp, relative to the kiosk's center.
        int centerX = Math.round(dragX / dragDensity);
        int centerY = Math.round(dragY / dragDensity);
        int x = Math.max(-screenW / 2, Math.min(screenW / 2, centerX - screenW / 2));
        int y = Math.max(-screenH / 2, Math.min(screenH / 2, centerY - screenH / 2));
        try {
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            if (target >= 0) {
                pillFree[target] = true;
                pillX[target] = x;
                pillY[target] = y;
                values.put("qr" + (target + 1) + "X", x);
                values.put("qr" + (target + 1) + "Y", y);
            } else {
                placement = "Free";
                widgetX = x;
                widgetY = y;
                values.put("placement", "Free");
                values.put("widgetX", x);
                values.put("widgetY", y);
            }
            host.updateSettings(values);
        } catch (UnsupportedOperationException olderHost) {}
        if (showWidget) applyPills();
    }

    /** An icon on the pill grid: a TextView that carries only its drawable. */
    private TextView iconView(Context context, KsTheme theme, String name) {
        TextView icon = new TextView(context);
        Drawable glyph = icon(theme, name, "primary");
        if (glyph != null) glyph.setBounds(0, 0, theme.px(24), theme.px(24));
        icon.setCompoundDrawablesRelative(glyph, null, null, null);
        icon.setGravity(Gravity.CENTER);
        return icon;
    }

    /** The group pill: one card carrying an icon per grouped QR code. */
    private final class GroupPill implements OverlayFactory {
        private final int[] slots;

        GroupPill(int[] slots) {
            this.slots = slots;
        }

        @Override
        public View create(Context context, KsTheme theme) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER);
            for (int slot : slots) root.addView(iconView(context, theme, pillIcon[slot]));
            root.setOnTouchListener(pillTouch(-1, slots));
            onThemeChanged(root, theme);
            return root;
        }

        @Override
        public void onThemeChanged(View view, KsTheme theme) {
            LinearLayout root = (LinearLayout) view;
            GradientDrawable pill = theme.card();
            pill.setCornerRadius(theme.px(100));
            root.setBackground(pill);
            root.setPadding(theme.px(16), theme.px(12), theme.px(16), theme.px(12));
            for (int i = 0; i < root.getChildCount(); i++) {
                TextView icon = (TextView) root.getChildAt(i);
                icon.setMinWidth(theme.px(32));
                icon.setMinHeight(theme.px(24));
            }
        }
    }

    /** A detached pill: one icon on its own card. */
    private final class IconPill implements OverlayFactory {
        private final int slot;

        IconPill(int slot) {
            this.slot = slot;
        }

        @Override
        public View create(Context context, KsTheme theme) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER);
            root.addView(iconView(context, theme, pillIcon[slot]));
            root.setOnTouchListener(pillTouch(slot, new int[] {slot}));
            onThemeChanged(root, theme);
            return root;
        }

        @Override
        public void onThemeChanged(View view, KsTheme theme) {
            LinearLayout root = (LinearLayout) view;
            GradientDrawable pill = theme.card();
            pill.setCornerRadius(theme.px(100));
            root.setBackground(pill);
            root.setPadding(theme.px(16), theme.px(12), theme.px(16), theme.px(12));
            TextView icon = (TextView) root.getChildAt(0);
            icon.setMinWidth(theme.px(32));
            icon.setMinHeight(theme.px(24));
        }
    }

    /**
     * The full screen, visual-only layer a drag draws on. It paints the pill at
     * the finger, in the same KS style, so the drag is visible everywhere
     * without the pill's clipping. Touches pass through: the pill's own view
     * still owns the gesture.
     */
    private final class DragLayer implements OverlayFactory {
        @Override
        public View create(Context context, final KsTheme theme) {
            final View view = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    float density = getResources().getDisplayMetrics().density;
                    // A pill the size of the icon's, centered on the finger.
                    int pillW = (int) (56 * density);
                    int pillH = (int) (48 * density);
                    float left = dragX - pillW / 2f;
                    float top = dragY - pillH / 2f;
                    GradientDrawable pill = theme.card();
                    pill.setCornerRadius(theme.px(100));
                    pill.setBounds((int) left, (int) top, (int) (left + pillW), (int) (top + pillH));
                    pill.draw(canvas);
                    Drawable glyph = icon(theme, dragIcon, "primary");
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

    /** The modal that asks which grouped QR code to show. */
    private final class Chooser implements OverlayFactory {
        private final int[] slots;

        Chooser(int[] slots) {
            this.slots = slots;
        }

        @Override
        public View create(Context context, KsTheme theme) {
            FrameLayout root = new FrameLayout(context);
            root.setOnClickListener(view -> tap(() -> {
                if (host != null) host.hideOverlay(CODE);
            }));
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClickable(true);
            TextView heading = new TextView(context);
            heading.setText("Show QR code");
            heading.setGravity(Gravity.CENTER);
            card.addView(heading);
            for (int slot : slots) {
                TextView row = new TextView(context);
                row.setText(title[slot]);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(theme.px(48));
                Drawable glyph = icon(theme, pillIcon[slot], "primary");
                if (glyph != null) glyph.setBounds(0, 0, theme.px(24), theme.px(24));
                row.setCompoundDrawablesRelative(glyph, null, null, null);
                row.setCompoundDrawablePadding(theme.px(14));
                final int picked = slot;
                row.setOnClickListener(view -> tap(() -> showCode(picked)));
                card.addView(row);
            }
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
            theme.styleText((TextView) card.getChildAt(0), 20, 600, "onSurface");
            for (int i = 1; i < card.getChildCount(); i++) {
                theme.styleText((TextView) card.getChildAt(i), 16, 500, "onSurface");
            }
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
