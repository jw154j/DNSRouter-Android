# DNS Router for Android - v1.1

Native Kotlin Android app implementing a **DNS-only** `VpnService` that forwards intercepted UDP DNS packets to a configurable NextDNS DoH endpoint. Normal Internet traffic is not routed through the VPN.

## v1.1 changes
- IPv4 **and IPv6 UDP DNS** packet handling.
- Protected TLS/HTTP connection to NextDNS so the DoH socket cannot loop back into the VPN.
- PIN required for **ON/OFF** and configuration changes.
- Salted SHA-256 PIN storage instead of an unsalted hash.
- Wi-Fi exclusions keep the VPN service alive and switch DNS routing to bypass mode.
- Mobile data continues to use NextDNS when not excluded.
- Boot receiver starts the service when enabled.
- Battery-optimization exemption request and status.
- Protection setup screen for Android **Always-on VPN**.
- DNS counters: received queries, responses, errors, NXDOMAIN, SERVFAIL.
- Clear statement that NextDNS blocked-query counts require NextDNS logs API access rather than guessing from response codes.
- NextDNS profile/device are editable independently.

## Configuration
The app requires a NextDNS Profile ID to function. You can also optionally provide an API Key for cloud analytics and a Device Name for logs. All configuration is stored strictly on-device.

## Important Android behavior
Android does not permit an ordinary app to silently enable the system **Always-on VPN** setting. The user must authorize DNS Router in Android VPN settings once. Android then maintains the Always-on state.

The app can request the battery-optimization exemption, but Android controls the final authorization.

For Wi-Fi SSID exclusions, Android may require location permission for reliable SSID visibility. The app asks for that permission.

## Recommended Android setting
If you want strict fail-closed DNS protection, Android's **Block connections without VPN** option can be enabled after **Always-on VPN** is authorized. This is intentionally left as a user-controlled system setting because it changes behavior for the entire device.

## Build
Open this directory in Android Studio with JDK 17 and build the `app` module. Minimum Android 10 (API 29), target SDK 35.

## v1.1 limitations
- DNS over TCP/53 is not implemented as a transparent TCP stack. Most Android resolver traffic is UDP, and implementing a complete TCP/IP stack would substantially increase complexity and battery/maintenance cost.
- NextDNS blocked-query totals are not available locally without the NextDNS logs API and credentials.
- Android OEMs can impose their own background/VPN restrictions; the app exposes battery and protection status but cannot override system policy silently.
