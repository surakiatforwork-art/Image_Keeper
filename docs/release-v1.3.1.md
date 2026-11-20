# Image Keeper v1.3.1

- Import multiple images from the gallery in one selection. Each image is processed in sequence; a failed image does not prevent the remaining images from being imported. The completion message reports successes and failures.
- Remove the branch-wide Save All to Gallery button; individual and selected-image saving remain available.
- Remove Route sorting and Route controls/badges from active screens. Existing Route metadata remains intact in storage and transferable backups.
- Add oldest-to-newest and newest-to-oldest branch sorting, using each branch's latest photo timestamp. Empty branches appear last in both directions.
- Add a compact Only branches with photos checkbox, compatible with account and text filters and automatically updated after images change.
- Gallery saves now use one album: `Pictures/Image Keeper/`. Filenames start with account and branch code, followed by the actual save date/time (including milliseconds) and a unique identifier. Repeated saves cannot overwrite earlier files.
- Remove the obsolete per-branch export folder setting from the screen. Existing gallery albums and original photos are not moved or deleted.

Validation: naming tests (including Thai filenames and duplicate prevention), in-memory database sorting/filter tests, multi-image import with a failed source, on-device MediaStore path and original-file checks, and existing zoom/retouch-sheet gesture regressions. Legacy Android 8/9 storage permission handling is included but was not tested on a physical legacy device.
