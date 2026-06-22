# Notara_ — Agent Session Log

## Goal
Build and refine Notara app: Pastel/Solid styles removed (only Label remains), themes simplified to 2 (Preto/Branco), theme values remapped, unused backgrounds and color bars cleaned up, `contentContainer` fixed to `match_parent` in `EditActivity`, geometric background restored as permanent MainActivity bg only.

## Progress

### Done
- `contentContainer` in `activity_edit.xml` changed from `wrap_content` to `match_parent` to fill screen height.
- Pastel and Solid styles removed from `NoteAdapter`, `NoteWidgetProvider`, `EditActivity`, `ChecklistActivity`, `SettingsManager`, `SettingsActivity`, and strings.
- `colorBar` View removed from `item_note.xml`; `widget_color_bar` removed from all 4 widget layouts.
- `bgView` removed from `activity_main.xml` and `activity_settings.xml`; `applyBgTheme()` removed from `MainActivity` and `SettingsActivity`.
- `bg_mesh.xml` deleted; `bg_geometric.xml` restored from git as permanent background only in `MainActivity`.
- `LinedEditText.java` stripped of drawing code (empty override only); `setLineColor()` removed.
- Card style and bg theme radio groups removed from `activity_settings.xml`.
- Themes: Light (0) and Pantera (1) removed; only Preto(0) and Branco(1) remain.
- `SettingsManager.getTheme()` default changed to `0`; remap: old 2→0, 3→1.
- All `isDarkTheme` checks updated: now `currentTheme == 0` (Preto).
- All theme application blocks simplified: no more `Theme_Notara_Pantera`, all use `Theme_Notara`; status bar icons change with theme.
- `setupThemeSettings()` in `SettingsActivity.java` simplified to 2 radio buttons (rbPreto/rbBranco).
- Strings updated: `theme_preto`/`theme_branco` in both PT and EN locales.
- Widget and provider no longer switch styles; always Label mode.
- Dynamic margin system added for BottomAppBar (keyboard + scroll state).

### Key Decisions
- Old theme values migrated in getter: Light(0)→Branco(1), Pantera(1)→Preto(0), DynamicBlack(2)→Preto(0), DynamicLight(3)→Branco(1).
- Both themes use `R.style.Theme_Notara`; only status bar icon color changes (Preto = dark icons off, Branco = dark icons on).
- `LinedEditText` kept as empty class inheriting `AppCompatEditText` to avoid XML breakage.

### Limitations
- RemoteViews don't support MaterialCardView stroke; border via root padding trick.
- At very low transparency (slider near 0), `noteColor` slightly bleeds through content area — inherent RemoteViews limitation.
