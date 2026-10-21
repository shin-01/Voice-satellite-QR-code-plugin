# QR Code (Kiosk Satellite plugin)

A hideable bar of QR code buttons, docked anywhere on the kiosk's frame and
dragged wherever you want. Every QR encodes a URL, plain text or Wi-Fi
credentials — whatever a phone needs to scan.

> **Style credit:** the bar's look — the KS card surface, the rounded indicator
> pills behind the icons, the animation-free pressed feedback and the cell
> sizing — follows [Navbar for Kiosk Satellite](https://github.com/dimatx/kiosk-satellite-navbar)
> by **Dima Tokar**, Apache-2.0. Many thanks to Dima for the design; this plugin
> reimplements that look for the QR bar. See [NOTICE](NOTICE) for the full
> attribution.

## What it does

- **The bar** shows one icon per configured QR code, with a chevron at its end.
- **Tap an icon** to open that QR code full screen; tap the chevron to collapse
  the bar. Collapsed, only the **arrow handle** stays, and tapping it brings the
  bar back.
- **Drag the bar** — expanded or collapsed — to move it anywhere on the frame.
  The dropped spot persists across settings edits, theme changes and app
  restarts. Placement switches to Free by itself on the first drag; Anchored
  keeps it in the corner you pick.
- The chevron points **from the bar's edge into the kiosk**, wherever the bar
  sits: down from a top corner, up from a bottom corner, and sideways for a
  freely placed bar near a side.
- Every QR code also has its own **Show QR code N** action: assign it in
  **Gestures > Run a plugin action**, keep it in the kiosk drawer or expose it
  as a Home Assistant button from the plugin subpage.

## Settings

**Bar**:

| Setting | Meaning |
| --- | --- |
| Show bar | Turn the bar on or off |
| Placement | `Anchored` to the corner below, or `Free` where you last dragged it |
| Corner | The anchor corner while placement is Anchored |
| Free position X, Y | Kept by the bar when you drag it |
| QR size | The size of the QR in the full screen card, 160 to 480 dp |

Each **QR code 1–4** group has the same settings:

| Setting | Meaning |
| --- | --- |
| Title | The heading of that QR's card |
| Bar icon | The icon this QR code shows on the bar |
| Content type | `Wi-Fi`, `URL` or `Text` |
| Network name (SSID), Security, Password | Shown for `Wi-Fi`. The password is masked |
| URL / Text | Shown for `URL` / `Text` |

For Wi-Fi the plugin builds the standard join string that Android and iOS
cameras read, escaping special characters for you:

```
WIFI:T:WPA;S:MyNetwork;P:MyPassword;;
```

Open networks use `T:nopass` and carry no password.

## Actions

| Action | Opens |
| --- | --- |
| Show QR code 1 | The first QR code |
| Show QR code 2 | The second QR code |
| Show QR code 3 | The third QR code |
| Show QR code 4 | The fourth QR code |

## Where data goes

Nothing leaves the device. The plugin only draws overlays with the content of
its own settings.

## License

Apache-2.0. The QR encoder is [Nayuki's QR-Code-generator](https://www.nayuki.io/page/qr-code-generator-library)
(MIT), bundled under `src/io/nayuki/qrcodegen`. The bar's visual style follows
[Navbar for Kiosk Satellite](https://github.com/dimatx/kiosk-satellite-navbar)
by Dima Tokar (Apache-2.0), with thanks — see [NOTICE](NOTICE).
