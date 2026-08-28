# DNS Router for Android - v1.97

Native Kotlin Android app implementing a **DNS-only** `VpnService` that forwards intercepted UDP DNS packets to a configurable NextDNS DoH endpoint. Normal Internet traffic is not routed through the VPN.

## v1.97 changes
- **App Exclusions**: Administrators can now whitelist specific applications from VPN routing via a PIN-protected management screen.
- **Structured Exclusion Requests**: Users can select installed apps and send detailed exclusion requests to their administrator, including application metadata and diagnostic results.
- **VPN Bypass Integration**: The service now dynamically excludes whitelisted apps using the `addDisallowedApplication` system API.

## v1.96 changes
- **Intelligent Multi-Channel Support**: Support requests now use structured data "traunches." Selecting an email app prepopulates Email/Subject/Body, while selecting a messaging app prepopulates Phone/Message. Standard sharing serves as a universal fallback.

## v1.95 changes
- **Unified Support Share Sheet**: Administrator contact now uses the native Android Share sheet, allowing users to choose between Email, SMS, or other messaging apps.
- **Multi-Channel Prepopulation**: Automatically fills Admin Email/Subject (for email) or Phone Number (for SMS) along with a full diagnostic report in the message body.
- **v2.0 Infrastructure**: Established the foundation for upcoming domain allowlist and app exclusion request workflows.

## v1.94 changes
- **Network Admin Mode**: Automatically restricts user capabilities when the app is managed via MDM/Managed Configurations.
- **Admin Read-Only UI**: Core settings become read-only in Admin Mode to prevent unauthorized modifications.
- **Managed Support Integration**: Added a "Support" feature that allows users to email their administrator with prepopulated diagnostic data.
- **Contextual Support Requests**: "Forgot PIN" and support buttons automatically include device and app status in help requests.

## v1.93 changes
- **Forgot PIN Workflows**: Added a new workflow for forgotten PINs. Personal users can perform a full app reset, while managed users (Enterprise/MDM) are directed to contact their administrator.
- **Factory Reset**: Introduced a secure way to wipe all app data and settings to recover access.

## v1.92 changes
- **Optional App PIN**: The application PIN is no longer mandatory on first launch.
- **Secure PIN Lifecycle**: Setting or changing a PIN now requires a "Verify PIN" step.
- **PIN Authorization**: Changing or removing an existing PIN now requires the current PIN for authorization.
- **Layout Refinement**: Fixed bottom button accessibility for system navigation bars.

## v1.91 changes
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
