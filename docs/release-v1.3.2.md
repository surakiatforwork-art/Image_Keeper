# Image Keeper v1.3.2

- Show the detected face boundary before automatic retouching and retain the existing multi-face selector.
- After AI retouching, select Frame to draw a rectangular limit around the area to keep edited, use the detected face frame, or remove that limit. This constrains compositing; it does not manually change the model's eye/mouth landmarks or recover failed face detection.
- Add adjustable soft brushes to restore original pixels locally and reapply the AI retouch layer. Tap for a spot or drag for a stroke. Reapplying operates only where the AI generated changes, not as an unrestricted generative/healing brush.
- Add undo/redo for the last 30 editing steps, including frame changes. Choose Zoom/Pan to use pinch zoom and double-tap reset.
- Replace the original-image text toggle with a Compare icon: hold to see the untouched original, release or cancel the gesture to return to the edited preview. Accessibility actions also support switching views.
- Save the exact composed preview as a separate edited copy. The original file remains untouched. Saving is disabled while a stroke or new composite is pending.

Validation: on-device compositing checks for restoring/reapplying pixels, strength and frame clipping without modifying inputs; tap/brush/undo/redo/frame/zoom gesture tests; hold/release/cancel tests; actual retouch sheet and existing gallery/import/filter regressions. Real-photo AI preview was also checked without saving changes to the user's images.
