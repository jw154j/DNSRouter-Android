# Notifications, Connection Diagnostics, Transport Protocols, and IP Versioning

This plan implements high-priority notifications for protection status, a connection diagnostic sequence to detect network blocking, support for multiple DNS transport protocols, and granular control over IP versioning.

## User Review Required

> [!IMPORTANT]
> - **DoH3** and **DoQ** require QUIC/HTTP3 support. I will integrate **Google Cronet** to provide this.
> - The app will request **Post Notifications** permission during onboarding.
> - I will standardize the height of all buttons in the "Setup & Protection" grid to ensure they are identical in size.
> - **IPv6 only** warning: Using this mode on networks without IPv6 support will cause encrypted DNS to fail.

## Proposed Changes

### UI Polishing & Layout
#### [MODIFY] [MainActivity.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/MainActivity.kt)
- Standardize `createGridButton` to use a fixed height (160px).
- Refactor locking logic: Entering PIN unlocks a section; saving re-locks it without a second PIN prompt.

### Core Preferences
#### [MODIFY] [Prefs.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/Prefs.kt)
- Add `dnsTransport` property (0=DoH, 1=DoH3, 2=DoT, 3=DoQ).
- Add `ipVersion` property (0=Automatic, 1=IPv4 & IPv6, 2=IPv4 only, 3=IPv6 only).

### Onboarding Flow
#### [MODIFY] [MainActivity.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/MainActivity.kt)
- Add `POST_NOTIFICATIONS` permission request to the onboarding sequence.

### Main UI
#### [MODIFY] [MainActivity.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/MainActivity.kt)
- Add "DNS Transport Protocol" spinner to **NextDNS Configuration**.
- Add "IP Version" spinner to **NextDNS Configuration** with the requested descriptions and the IPv6-only caution warning.
- Ensure all new settings are correctly locked/unlocked in Admin mode.

### VPN Service & Protocols
#### [MODIFY] [DnsVpnService.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/DnsVpnService.kt)
- Implement DoH, DoT, DoH3, and DoQ transport handling.
- Respect `ipVersion` setting:
    - Filter DNS server addresses and DoH/DoT connection attempts based on the selected version.
    - "Automatic" will probe and select the best path.
- Implement **Diagnostic Sequence**:
    1. If encrypted DNS fails, test general connectivity (HTTP probe).
    2. If internet is UP but DNS fails, try fallback transports (switch protocols).
    3. If all fail but internet works, notify: "The current Wi-Fi network may be blocking encrypted DNS".

### Notifications
#### [MODIFY] [DnsVpnService.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/DnsVpnService.kt)
- Create "Security Alerts" high-priority channel.
- Implement notifications for:
    - Protection Stopped.
    - Traffic Blocked (Fail-safe).
    - Network Blocking Encrypted DNS.

## Verification Plan

### Manual Verification
- **Button Size**: Confirm grid symmetry.
- **IP Versioning**: Test "IPv4 only" on a dual-stack network and confirm only IPv4 is used.
- **Onboarding**: Verify notification permission step.
- **Diagnostics**: Simulate network blocking and verify the "Network blocking" alert.
- **Notifications**: Verify high-priority alerts for status changes.
