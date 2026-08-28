# DNS Router for Android - v1.9

Native Kotlin Android app implementing a **DNS-only** `VpnService` that forwards intercepted UDP DNS packets to a configurable NextDNS DoH endpoint. Normal Internet traffic is not routed through the VPN.

## v1.9 changes
- **Security Diagnostics & Leak Detection**: Integrated an active testing suite to verify DNS interception and detect bypasses.
- **Active Bypass Warnings**: The app now warns users if a DNS request expected to go through NextDNS did not, helping identify "Secure DNS" browser leaks.
- **Cloud Verification**: (API users only) Real-time verification that queries are appearing in NextDNS cloud logs.
- **Diagnostics History**: Tracks the timestamp of the last successful DNS security test.

## v1.8 changes
- **Reliability Status Indicators**: New UI section with color-coded circles (Green/Yellow/Red) for real-time tracking of Always-on VPN, Battery Optimization, and Auto-Start status.
- **Network Awareness**: Immediate feedback on the current Wi-Fi network and its exclusion status.

## v1.5 changes
- **Device Compatibility Report**: Added a detailed diagnostic tool that categorizes features into Core, Recommended, and Optional. It automatically runs after app updates to ensure ongoing compatibility.
- **Enhanced Wi-Fi Exclusions**: Improved SSID detection logic with explicit "Wi-Fi name unavailable" reporting. Added a real-time counter for excluded networks and a one-tap "Add Current" shortcut.
- **Improved Setup Flow**: Refined the sequential permission and setup process for better hardware alignment.

## v1.4 changes
- **Optimal Setup Grid**: New UI with color-coded buttons (Green/Yellow/Red) to guide the user toward the most reliable configuration.
- **Smart Setup Flow**: Sequential setup starting with Location access for SSIDs, followed by a detailed Battery Optimization request with a descriptive "hard stop" dialog.
- **Always-On VPN Focus**: Updated terminology to emphasize Always-On VPN as "Highly Recommended" and explained the risks of unfiltered DNS if disabled.
- **Smart Wi-Fi Exclusions**: Added "Add Current Network" button to the exclusion dialog for easier configuration.
- **Enhanced Status Monitoring**: Detailed status area confirming efficient operation (No Polling, Standard DoH).

## v1.3 changes

## v1.2 changes
- **Keystore-backed Encryption**: Configuration data (Profile ID, API Key, Device Name) is now encrypted using hardware-backed keys via `EncryptedSharedPreferences`.
- **Enhanced Privacy**: Removed all hardcoded developer defaults. Device Name and Profile ID are empty by default.
- **Improved Onboarding**: DNS Protection is OFF by default at installation to facilitate initial VPN authorization.
- Fixed DoH path formatting for empty device names.

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
