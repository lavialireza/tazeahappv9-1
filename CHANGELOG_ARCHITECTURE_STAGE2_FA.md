Tazieh Studio – Architecture Stage 2

- Domain archive contracts now return domain models, not Room entities.
- Added explicit archive version, source document and review-state enums.
- Added source-review and version-state use cases.
- Added a guarded version lifecycle; draft cannot skip review and published versions are immutable.
- Repository operations preserve coroutine cancellation.
- Added domain validation and lifecycle tests.
- No Room schema migration was required; database remains version 11.
- No package IDs, flavors, signing, update channels, release workflows, Viewer access policy or content package format were changed.
- Android Gradle/Robolectric tests were not executed because the project archive has no Gradle wrapper and Gradle is not installed in the build environment.
