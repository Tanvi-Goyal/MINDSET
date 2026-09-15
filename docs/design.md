---
name: Obsidian Performance
colors:
  - surface: '#131313'
  - surface-dim: '#131313'
  - surface-bright: '#3a3939'
  - surface-container-lowest: '#0e0e0e'
  - surface-container-low: '#1c1b1b'
  - surface-container: '#201f1f'
  - surface-container-high: '#2a2a2a'
  - surface-container-highest: '#353534'
  - on-surface: '#e5e2e1'
  - on-surface-variant: '#e7bdb7'
  - inverse-surface: '#e5e2e1'
  - inverse-on-surface: '#313030'
  - outline: '#ad8883'
  - outline-variant: '#5d3f3b'
  - surface-tint: '#ffb4aa'
  - primary: '#ffb4aa'
  - on-primary: '#690003'
  - primary-container: '#ff5545'
  - on-primary-container: '#5c0002'
  - inverse-primary: '#c0000a'
  - secondary: '#c6c6c7'
  - on-secondary: '#2f3131'
  - secondary-container: '#454747'
  - on-secondary-container: '#b4b5b5'
  - tertiary: '#c8c6c8'
  - on-tertiary: '#303032'
  - tertiary-container: '#929092'
  - on-tertiary-container: '#2a292c'
  - error: '#ffb4ab'
  - on-error: '#690005'
  - error-container: '#93000a'
  - on-error-container: '#ffdad6'
  - primary-fixed: '#ffdad5'
  - primary-fixed-dim: '#ffb4aa'
  - on-primary-fixed: '#410001'
  - on-primary-fixed-variant: '#930005'
  - secondary-fixed: '#e2e2e2'
  - secondary-fixed-dim: '#c6c6c7'
  - on-secondary-fixed: '#1a1c1c'
  - on-secondary-fixed-variant: '#454747'
  - tertiary-fixed: '#e4e2e4'
  - tertiary-fixed-dim: '#c8c6c8'
  - on-tertiary-fixed: '#1b1b1d'
  - on-tertiary-fixed-variant: '#474649'
  - background: '#131313'
  - on-background: '#e5e2e1'
  - surface-variant: '#353534'
typography:
  - display-lg:
    - fontFamily: Inter
    - fontSize: 48px
    - fontWeight: '800'
    - lineHeight: 56px
    - letterSpacing: -0.02em
  - headline-lg:
    - fontFamily: Inter
    - fontSize: 32px
    - fontWeight: '700'
    - lineHeight: 40px
    - letterSpacing: -0.01em
  - headline-lg-mobile:
    - fontFamily: Inter
    - fontSize: 28px
    - fontWeight: '700'
    - lineHeight: 34px
  - title-md:
    - fontFamily: Inter
    - fontSize: 20px
    - fontWeight: '600'
    - lineHeight: 28px
  - body-lg:
    - fontFamily: Inter
    - fontSize: 16px
    - fontWeight: '400'
    - lineHeight: 24px
  - body-sm:
    - fontFamily: Inter
    - fontSize: 14px
    - fontWeight: '400'
    - lineHeight: 20px
  - label-caps:
    - fontFamily: JetBrains Mono
    - fontSize: 12px
    - fontWeight: '600'
    - lineHeight: 16px
    - letterSpacing: 0.1em
rounded:
  - sm: 0.25rem
  - DEFAULT: 0.5rem
  - md: 0.75rem
  - lg: 1rem
  - xl: 1.5rem
  - full: 9999px
spacing:
  - unit: 4px
  - xs: 4px
  - sm: 8px
  - md: 16px
  - lg: 24px
  - xl: 40px
  - gutter: 16px
  - margin-mobile: 20px
  - margin-desktop: 64px
---

## Brand & Style

This design system is engineered for peak athletic performance and mental discipline. It utilizes a **Glassmorphic** aesthetic to convey a sense of high-tech precision and depth, while maintaining a strict **Minimalist** foundation to reduce cognitive load during intense training.

The brand personality is authoritative, competitive, and refined. It targets high-performance individuals who value professional-grade tools. By merging deep obsidian surfaces with vibrant, electric accents, the UI evokes a "dark mode" laboratory environment—clean, focused, and powerful.

## Colors

The palette is anchored by **Deep Obsidian (#0A0A0A)** for the primary background to ensure maximum contrast for data visualization.

- **Primary (Electric Red):** Reserved for critical actions, active states, and performance metrics. It represents energy and urgency.
- **Secondary (High-Contrast White):** Used for primary typography and iconography to ensure peak legibility.
- **Surface (Glass):** Semi-transparent layers use a white tint at 5-8% opacity to create the "frosted" effect over the dark background.
- **Accents:** Subtle greys are used for secondary text to maintain a clear visual hierarchy without cluttering the dark interface.

## Typography

The typography system relies on **Inter** for its neutral, highly legible, and athletic character. It is paired with **JetBrains Mono** for technical labels and data readouts to reinforce the "performance tracking" nature of the product.

Headlines should use tight tracking and heavy weights to appear bold and commanding. Body text maintains generous line height for readability against dark backgrounds. Use the `label-caps` role for metadata, timestamps, and small categorical tags to provide a technical, instrument-like feel.

## Layout & Spacing

This design system employs a **Fluid Grid** model with a base unit of 4px. Layouts should feel airy but structured, using high-density information clusters separated by significant whitespace.

- **Mobile:** 4-column grid with 20px side margins. Containers should span the full width to maximize the glass effect area.
- **Desktop:** 12-column grid with a maximum content width of 1440px.
- **Rhythm:** Use `lg` (24px) for vertical spacing between distinct sections and `md` (16px) for internal component padding.

## Elevation & Depth

Elevation is achieved through **translucency and blurs** rather than traditional drop shadows.

1. **Base Layer:** Solid #0A0A0A.
2. **Glass Layer (Level 1):** 5% white fill, 20px backdrop-blur. 0.5px border in 15% white.
3. **Floating Layer (Level 2):** 10% white fill, 40px backdrop-blur. 1px border in 20% white. This is used for modals and tooltips.

The "Electric Red" accent should never be semi-transparent; it must always sit on top of the glass layers as a solid, vibrant element to draw the eye immediately.

## Shapes

The shape language is "Squircle" inspired—soft but intentional.
- **Default (0.5rem):** Standard buttons, input fields, and small cards.
- **Large (1rem):** Main content containers and glass sections.
- **Pill:** Used exclusively for tags, status indicators (e.g., "Live", "Completed"), and secondary "Ghost" buttons.

Borders must remain extremely thin (0.5px to 1px) to maintain the premium, high-precision aesthetic.

## Components

### Buttons
- **Primary:** Solid Electric Red (#FF3B30) with white centered text. Heavy font weight.
- **Glass/Secondary:** Semi-transparent white (10%) with a subtle white border.
- **Interaction:** On press, primary buttons should scale down slightly (98%) to provide tactile feedback.

### Input Fields
- Backgrounds use the Level 1 Glass style. Placeholder text in 40% white. The active state is indicated by a 1px Electric Red bottom border or glow.

### Cards & Containers
- All cards must use `backdrop-filter: blur(20px)`.
- Content within cards should have a standard 16px padding.

### Progress & Metrics
- Data visualizations should use the primary Electric Red for "current" or "active" data, and a muted 20% white for background tracks or historical data.
- Metrics should be displayed in JetBrains Mono to look like high-precision instruments.

### Selection Controls
- Checkboxes and Radios: When active, they fill with Electric Red. When inactive, they are a simple 1px white circle/square outline at 30% opacity.
