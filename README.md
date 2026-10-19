# QR Code (Kiosk Satellite example plugin)

A small corner widget that opens the chosen QR code full screen when tapped.
Handy for sharing this kiosk's Wi-Fi credentials with a phone: point the
camera at the code and join the network.

## What it does

- A themed pill widget sits in the display corner you choose, with an icon and
  title of your picking.
- Tapping the widget (or running the **Show QR code** action from a gesture,
  the kiosk drawer or a Home Assistant button) opens a full screen card with
  the QR code.
- The card closes on a tap outside, on the **Close** button or with Back.
- The QR content is free text: a `WIFI:` string, a URL or anything else.

## Settings

| Setting | Meaning |
| --- | --- |
| Show corner widget | Turn the corner widget on or off |
| Widget title | The label on the widget and the heading of the QR card |
| QR content | What the QR encodes, for example `WIFI:T:WPA;S:MyNetwork;P:MyPassword;;` |
| Corner | Where the widget sits |
| Widget icon | A Material Design Icon for the widget |
| QR size | The size of the QR code in the full screen card, 160 to 480 dp |

## Wi-Fi format

Android and iOS both read the standard Wi-Fi QR format:

```
WIFI:T:WPA;S:MyNetwork;P:MyPassword;;
```

Use `T:nopass` for an open network and omit `P:`. Escape special characters
in the SSID or password with a backslash.

## Where data goes

Nothing leaves the device. The plugin only draws overlays with the content of
its own settings.

## License

Apache-2.0. The QR encoder is [Nayuki's QR-Code-generator](https://www.nayuki.io/page/qr-code-generator-library)
(MIT), bundled under `src/io/nayuki/qrcodegen`.
