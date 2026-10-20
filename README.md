# QR Code (Kiosk Satellite plugin)

A pill per QR code, each with its own icon. Grouped pills share one pill; a
detached pill floats on its own. Every QR encodes a URL, plain text or Wi-Fi
credentials — whatever a phone needs to scan.

## What it does

- Every QR code gets a **pill with an icon of your choosing** — no title on the
  pill. The QR's title appears in its full screen card.
- **Grouped** pills share one pill that carries an icon per QR code. Tapping it
  with several QR codes inside first asks **which to show, in a modal** with a
  row per QR; with a single QR code it opens it directly.
- **Detached** pills float on their own, each in its own corner until you drag
  it anywhere. Tapping a detached pill opens its QR directly.
- Dragging a pill moves it (or the whole group) anywhere on the kiosk; the spot
  survives settings edits, theme changes and app restarts.
- Every QR code also has its own **Show QR code N** action: assign it in
  **Gestures > Run a plugin action**, keep it in the kiosk drawer or expose it
  as a Home Assistant button from the plugin subpage.

## Settings

**Pills** covers the group:

| Setting | Meaning |
| --- | --- |
| Show pills | Turn the pills on or off |
| Group placement | `Anchored` to the corner below, or `Free` where you last dragged the group |
| Group corner | The anchor corner while placement is Anchored |
| QR size | The size of the QR in the full screen card, 160 to 480 dp |

Each **QR code 1–4** group has the same settings:

| Setting | Meaning |
| --- | --- |
| Title | The heading of that QR's card and its row in the chooser |
| Pill icon | The icon this QR code's pill shows |
| Pill | `Grouped` or `Detached` |
| Detached position X, Y | Kept by the pill when you drag it |
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
(MIT), bundled under `src/io/nayuki/qrcodegen`.
