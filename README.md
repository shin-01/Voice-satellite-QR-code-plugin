# QR Code (Kiosk Satellite plugin)

A small corner widget that opens the first QR code full screen when tapped,
plus up to four QR codes that open through actions you can bind to gestures.

## What it does

- A themed pill widget with an icon and title of your picking. Tapping it opens
  **QR code 1**; dragging it moves it anywhere on the kiosk.
- **Anchored** keeps the widget in the corner you pick. **Free** leaves it where
  you last dragged it, and a drag switches the placement to Free by itself. The
  dropped spot survives settings edits, theme changes and app restarts.
- Each of the four QR codes encodes a URL, plain text or Wi-Fi credentials —
  whatever a phone needs to scan.
- Every QR code has its own **Show QR code N** action: assign it in
  **Gestures > Run a plugin action**, keep it in the kiosk drawer or expose it
  as a Home Assistant button from the plugin subpage.
- The card closes on a tap outside, on the **Close** button or with Back.

> The screensaver's stock corner widgets are a Kiosk Satellite surface with no
> plugin API, so a QR cannot become a screensaver widget. The gestures binding
> is the supported path: any gesture can open any of the four QR codes.

## Settings

**QR code 1** also carries the corner widget options:

| Setting | Meaning |
| --- | --- |
| Show corner widget | Turn the widget on or off. It opens QR code 1 |
| Placement | `Anchored` or `Free`. Dragging the pill switches it to Free |
| Corner | The anchor corner, shown while Placement is Anchored |
| Free position X, Free position Y | Kept by the widget when you drag it |
| Widget icon | The icon on the pill |
| QR size | The size of the QR in the full screen card, 160 to 480 dp |

Each **QR code 1–4** group has the same content settings:

| Setting | Meaning |
| --- | --- |
| Title | The heading of that QR's card |
| Content type | `Wi-Fi`, `URL` or `Text` |
| Network name (SSID), Security, Password | Shown for `Wi-Fi`. The password is masked |
| URL | Shown for `URL` |
| Text | Shown for `Text` |

For Wi-Fi the plugin builds the standard join string that Android and iOS
cameras read, escaping special characters for you:

```
WIFI:T:WPA;S:MyNetwork;P:MyPassword;;
```

Open networks use `T:nopass` and carry no password.

## Actions

| Action | Opens |
| --- | --- |
| Show QR code 1 | The first QR code, the same one the widget opens |
| Show QR code 2 | The second QR code |
| Show QR code 3 | The third QR code |
| Show QR code 4 | The fourth QR code |

## Where data goes

Nothing leaves the device. The plugin only draws overlays with the content of
its own settings.

## License

Apache-2.0. The QR encoder is [Nayuki's QR-Code-generator](https://www.nayuki.io/page/qr-code-generator-library)
(MIT), bundled under `src/io/nayuki/qrcodegen`.
