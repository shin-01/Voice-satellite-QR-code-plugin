# QR Code (Kiosk Satellite plugin)

A small corner widget that opens the chosen QR code full screen when tapped.
The QR encodes a URL, plain text or this kiosk's Wi-Fi credentials — whatever
a phone needs to scan.

## What it does

- A themed pill widget sits in the display corner you choose, with an icon and
  title of your picking.
- Tapping the widget opens a full screen card with the QR code.
- The **Show QR code** action does the same from anywhere: assign it in
  **Gestures > Run a plugin action**, keep it in the kiosk drawer or expose it
  as a Home Assistant button from the plugin subpage.
- The card closes on a tap outside, on the **Close** button or with Back.

## Settings

### QR code

| Setting | Meaning |
| --- | --- |
| Show corner widget | Turn the corner widget on or off |
| Widget title | The label on the widget and the heading of the QR card |
| Corner | Where the widget sits |
| Widget icon | A Material Design Icon for the widget |
| QR size | The size of the QR code in the full screen card, 160 to 480 dp |

### QR content

| Setting | Meaning |
| --- | --- |
| QR content type | `Wi-Fi`, `URL` or `Text` |
| Network name (SSID), Security, Password | Shown for `Wi-Fi`. The password is masked |
| URL | Shown for `URL`, for example `https://www.kiosksatellite.com/` |
| Text | Shown for `Text`, any text |

For Wi-Fi the plugin builds the standard join string that Android and iOS
cameras read, escaping special characters for you:

```
WIFI:T:WPA;S:MyNetwork;P:MyPassword;;
```

Open networks use `T:nopass` and carry no password.

## Where data goes

Nothing leaves the device. The plugin only draws overlays with the content of
its own settings.

## License

Apache-2.0. The QR encoder is [Nayuki's QR-Code-generator](https://www.nayuki.io/page/qr-code-generator-library)
(MIT), bundled under `src/io/nayuki/qrcodegen`.
