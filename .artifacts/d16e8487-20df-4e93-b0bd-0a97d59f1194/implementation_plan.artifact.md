# Theme and Readability Improvements

This plan addresses the readability issues by implementing a theme selection (Light, Dark, System) and improving button contrast, while maintaining status indicators' colors.

## User Review Required

> [!IMPORTANT]
> The theme switcher will be added to the main settings screen. Changing the theme will recreate the Activity to apply the new style.

## Proposed Changes

### Core Logic
#### [MODIFY] [Prefs.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/Prefs.kt)
- Add `themeMode` property (0=System, 1=Light, 2=Dark).

### UI Improvements
#### [MODIFY] [MainActivity.kt](file:///home/jason/DNSRouter-Android-v1.1/app/src/main/java/com/jason/dnsrouter/MainActivity.kt)
- Apply `AppCompatDelegate.setDefaultNightMode` in `onCreate`.
- Update `showControlModeSelection` to use theme-aware colors for "User" and "Admin" buttons.
- Add a "App Theme" selection section in the UI.
- Refactor `setBtnStatus` to ensure status colors (Green/Yellow/Red) are consistent across themes.
- Ensure text visibility on buttons by setting explicit text colors based on background brightness.

## Verification Plan

### Manual Verification
- Deploy the app and test theme switching (Light -> Dark -> System).
- Verify "User" and "Admin" buttons have high contrast in both modes.
- Verify status buttons (DNS Protection, Auto-Start, etc.) maintain their Green/Yellow/Red colors in both modes.
- Check readability of all text labels.
