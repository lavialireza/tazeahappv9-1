# TaziehApp V9 — Director Office / Stage 1

This patch adds the Room data foundation for the independent Director Office module.

## Included
- Nine Room entities for plans, versions, source-file metadata, turns, participants, text segments, stage cues, sessions, and per-turn progress.
- `DirectorDao` with CRUD/list operations for these records.
- Room database version increment from 17 to 18 and a non-destructive `MIGRATION_17_18`.
- Existing tables and existing feature source files are preserved.

## Important scope note
This is Stage 1 (database/domain foundation), not yet the visible menu or import/reader UI. The source document is represented by a private app file path and metadata; the original file is not modified. `sectionUid`, `roleUid`, `taziehUid`, and `performanceUid` are soft references to existing records to avoid changing existing library and rehearsal ownership.

## Next stage
Build the Admin workflow: create/select a plan, import PDF/text, parse turn numbers and collective turns, preview/edit, and save a draft version. Then add live director view and Viewer read-only published access.

## Build verification
The ZIP does not contain a Gradle wrapper and this environment does not have a configured Android SDK/Gradle installation, so an Android compile was not run here. Run the project's GitHub Actions build after uploading.
