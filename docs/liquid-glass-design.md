# Liquid Glass UI implementation

## Scope and safeguards

Presentation-only refresh with shared glass backdrop, toolbar, cards, buttons, inputs and dialogs. No repository, database schema, ViewModel, sharing contract or AI model changes. Preserve branch search/account filtering/sorting, metadata and route display, capture/import, multi-selection, save/delete/send confirmations, both GhostShift and Timestamp destinations, backup transfer and settings.

Light colors follow the supplied lavender/ice-blue reference with dark navy typography and purple primary controls. Dark appearance uses darker opaque surfaces and restrained white borders. Static translucent gradients simulate frosted glass instead of expensive live blur on scrolling image cards. Photos are not tinted or blurred. Controls retain at least 48dp touch targets; primary controls compress slightly on press.

The viewer fits images without cropping, provides 1x-6x focal-point zoom with bounded panning, resets on double-tap, and leaves paging gestures available at fit size. Small-height/large-font layouts scroll. The actual retouch sheet uses the same viewport for before/after layers and prevents accidental sheet dismissal during processing or zoom gestures.

## Assets

- Noto Sans Thai is bundled locally with its SIL Open Font License in app assets; no font download is required at runtime. Source: https://github.com/google/fonts/tree/main/ofl/notosansthai
- Mascot foreground was edited from the user's supplied mint ghost reference using image generation in transparent-background edit mode. The original reference and personal QA photos are not distributed. Adaptive icons use a separate lavender background and a monochrome ghost/folder silhouette.

### Icon edit prompt

Edit the supplied Image Keeper brand mascot into an Android adaptive launcher foreground asset. Preserve the same friendly white ghost, mint-teal keeper cap with folder badge, mint folder holding store/photo cards, lilac details, dark expressive eyes, soft premium 3D finish. Remove ALL text including Image Keeper, remove the entire rounded-square background, underline and decorative floating marks. Only the ghost plus folder and cards, on genuine transparent background. Recompose as one compact balanced square silhouette centered inside the central 64 percent of a square canvas, with at least 18 percent transparent safe padding on every side so Android circular masks never cut off the cap or folder. Keep recognizable original character proportions. No letters, no watermark, no enclosing tile, no extra characters. Crisp details readable at 48dp; soft lavender reflected edge highlights compatible with a lavender liquid-glass app.

## Verification limits

Automated gesture/model tests and non-destructive visual checks are performed on the connected Android test device. Destructive actions, external sync, and sending personal photos to other apps are not exercised during visual QA. Other OEM display sizes and accessibility configurations still benefit from field testing.
