# Notara_ — Agent Session Log

## Goal
Build and refine the Notara Android app.

## Progress

### Done
- Added `extractTitle()` method in `Note` to derive a title from content.
- Modified save logic to use extracted titles instead of "Sem título".
- Added Toast warnings when saving empty notes/lists.
- Changed Label card style from color bar to full 3dp colored stroke border.
- Applied full-border design to Edit/Checklist activities via GradientDrawable.
- Card transparency controlled by settings slider (0–100).
- Fixed widget 1x1 bug (RemoteViews accessing non-existent IDs).
- Fixed widget inflation crash (replaced `<View>` with `<FrameLayout>`).
- Created separate widget previews (1x2, 2x2).
- Unified widget designer with card designer (dynamic colors per theme).
- Fixed widget theme issue for Pantera (uses `0xFF000000`).
- Removed border simulation from widget Label style (current code uses color bar).
- **Re-implemented widget border frame**: Label style now uses `widget_root` bg = `noteColor` (opaque, creates border via 3dp padding) + `content_container` bg = `surfaceColor` with slider alpha + `widget_color_bar` GONE. Pastel/Solid styles unchanged.
- Added `getThemeColor()` helper method back to `NoteWidgetProvider`.

### Key Decisions
- Widget border uses padding trick (3dp on `widget_root` with `noteColor` background).
- Content area uses `surfaceColor` with slider alpha to show wallpaper through content while minimizing border color bleed.
- `widget_color_bar` hidden for Label style (border frame replaces it).
- `getThemeColor()` behaves identically to `NoteAdapter.getThemeColor()`.

### Limitations
- RemoteViews don't support `MaterialCardView` stroke; border is a visual illusion via root padding.
- At very low transparency (slider near 0), `noteColor` slightly bleeds through content area — inherent RemoteViews limitation.
