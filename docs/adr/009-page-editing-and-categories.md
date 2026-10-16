# ADR 009: Page editing, categories and sharing

Status: accepted

## Context
Users need to fix a scan after saving it (crop, brightness, colour, pen marks), file documents by type, add a note, copy
the recognised text, and share one or many documents. The first version stored only the flattened page image, so the
crop could not be changed afterwards.

## Decision
- A page keeps its **original upright photo** (`originalPath`) next to the **final image** and a `PageEdit`
  (enhancement, brightness, tint, pen strokes). Editing re-renders from the original, so nothing degrades and the crop
  can always be adjusted. Cost: roughly two images per page on disk. Pages saved by earlier builds treat their flat
  image as the original (no crop to adjust).
- Rendering sits behind `PageRenderer` (domain interface, native/Canvas implementation in `vision`, fake in tests).
  The order is fixed: crop, enhance, brightness and tint, then strokes. Strokes are stored normalized to the cropped
  page, so a new crop clears them (Undo restores them).
- The text is read again after every edit, so anything covered with the pen is no longer in the recognised text.
- `DocumentCategory` (receipt, passport/ID, book page, A4 page, document, other) is **suggested** by
  `DocumentClassifier` from the page proportions and the text, and always editable. Receipt fields are shown only for
  receipts. A comment is stored per document and is searchable.
- Room schema v2 adds `category`, `comment`, `originalPath`, `editJson` with `MIGRATION_1_2` (no destructive fallback).
- Sharing always creates a file first (PDF for documents and single pages, JPEG for an image) in `cache/exports`,
  exposed only through the FileProvider, then opens the system share sheet. Several documents become one PDF.
- The editor is its own module (`feature-editor`); `app` wires navigation.

## Consequences
Disk use grows with the originals; deleting a page or document removes both files. The classifier is a heuristic and
will be wrong sometimes, which is why the user can change the category.
