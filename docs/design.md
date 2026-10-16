# Design

A calm, paper-and-ink look for a utility app: light surfaces for browsing, a dark canvas wherever you look at or adjust a page.

## What was studied

Looked at on 2026-10-16 (Google Play listings, screenshots only; no app code):

- **Adobe Scan, crop screen.** Dark canvas, page with corner handles sitting clear of the screen edge, a row of labelled tool
  icons under the page, and two actions pinned to the bottom: outlined "Keep scanning" and filled "Save PDF".
- **Genius Scan, document view.** Black canvas, small top bar (back, title, share), a "1/4" page counter pill on the page,
  Edit / Filters / Format tabs and a labelled icon toolbar.
- **Dribbble "document scanner app" concepts.** A home with one obvious scan action, file cards with thumbnails, soft rounded
  corners, one accent colour.
- General pattern across scanners (Google Drive, CamScanner, Lens): full-bleed viewfinder, big round shutter button at the
  bottom centre, closing control at the top, hints as a pill over the preview, one primary action per screen.

## Principles

1. **One primary action per screen**, filled, at the bottom or as the single FAB. Secondary actions are outlined or text.
2. **Nothing floats over content.** Lists reserve room for the FAB; bars use `Scaffold` and window insets; no row of buttons
   sits on top of the preview or the page.
3. **Nothing touches the screen edge.** The page and its outline live inside a 24 dp inset, so corner handles and edge labels
   are never cut off; labels are clamped inside the canvas.
4. **Dark canvas for pages** (scan, review, page viewer); **light theme surfaces** elsewhere (the app is light only, whatever the system's dark mode says).
5. **Icons carry the toolbar, words carry the commitments** (Save, Export, Delete are always words).
6. 8 dp grid, 16 dp screen margins, 48 dp minimum touch targets, 16 dp card corners, fully round buttons and chips.

## Tokens

- Colour: the look of the system settings. A soft grey page (`#EFF1F4`), white cards and pills, grey section labels, one blue accent
  (`#2F6BEB`); each document type has its own tint and accent (`CategoryStyle.kt`), also on the filter chips. Confidence colours green / amber / red stay on the overlay. Chips and fields inside a white card use a light grey
  fill (`onCard` variants in `Styling.kt`).
- Type: Material 3 scale on Roboto; titles medium weight, labels uppercase never.
- Shape: 16 dp cards, 28 dp bottom sheets and dialogs, round buttons.

## Screens

| Screen | Layout |
|---|---|
| **Library** | Pinned header: title, settings icon, white search pill (a tap target until tapped, so it never grabs focus) and category chips; document cards (thumbnail, title, date and pages, category and total tags); swipe left to delete, long-press for edit mode (Cancel in the header, Share and Delete in the bottom bar); bottom bar with Import and Scan. On wide windows the selected card is highlighted and the document opens beside the list. |
| **Scan** | Black screen, viewfinder inset 24 dp with rounded corners, transparent top bar (close, title, paper-format menu), hint pill under it, bottom bar with a round shutter button and, when you moved a corner, "Reset corners". |
| **Review** | Dark canvas with the photo and an editable outline; bottom row "Retake" and "Save" only. Enhancement and filters live in the page editor. |
| **Document** | Top bar with back and a trash icon; page viewer on a dark card (tap to edit, edit and share buttons); Details card (type chips, comment), receipt fields for receipts, copyable recognised text; bottom bar "Add page" and "Share PDF". |
| **Export** | Centred card: determinate progress with page count and Cancel; on success a check, the file name, "Share" (filled) and "Done". |
| **Settings** | Grey page, white rounded cards with grey section labels; switches for Quick scan and Assistants, a select row for paper size, plain privacy rows. |

Components live in `core:designsystem`; screens keep their view models unchanged.
