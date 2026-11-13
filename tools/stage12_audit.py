from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
def read(path):
    return (ROOT / path).read_text(encoding='utf-8')
checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

# Re-run the prior-stage regression audit first.
prior = subprocess.run([sys.executable, str(ROOT / 'tools' / 'stage11_audit.py')], capture_output=True, text=True)
check('Stage 11 regression audit remains green', prior.returncode == 0 and '32/32 checks passed' in prior.stdout)
check('Room schema remains 15; no migration added', 'APP_DATABASE_SCHEMA_VERSION = 15' in read('app/src/main/java/com/example/bookapp/data/AppDatabase.kt'))
check('DOCX footnote extraction exists', 'word/footnotes.xml' in read('app/src/admin/java/com/example/bookapp/data/DocumentProcessingPipeline.kt'))
check('DOCX embedded media extraction exists', 'word/media/' in read('app/src/admin/java/com/example/bookapp/data/DocumentProcessingPipeline.kt'))
check('PDF page rendering dimension is capped', 'MAX_RENDER_DIMENSION = 2200' in read('app/src/admin/java/com/example/bookapp/data/DocumentProcessingPipeline.kt'))
check('Persian/Arabic OCR adapter validates fas/ara', 'setOf("fas", "ara")' in read('app/src/admin/java/com/example/bookapp/data/DocumentProcessingPipeline.kt'))
check('PDF text extraction provider contract exists', 'interface PdfTextExtractionProvider' in read('app/src/admin/java/com/example/bookapp/data/DocumentProcessingPipeline.kt'))
check('Human review artifacts are separate from canonical content', 'source_reviews' in read('app/src/admin/java/com/example/bookapp/data/SourceReviewWorkflow.kt'))
check('Independent version comparison service exists', 'class VersionComparisonService' in read('app/src/main/java/com/example/bookapp/data/ArchiveCompletionServices.kt'))
check('Word-level diff exists', 'object TaziehWordDiff' in read('app/src/main/java/com/example/bookapp/data/WordLevelDiff.kt'))
check('Encyclopedia graph excludes unverified records', 'reviewStatus == EncyclopediaRecordEntity.VERIFIED' in read('app/src/main/java/com/example/bookapp/data/ArchiveCompletionServices.kt'))
check('Viewer encyclopedia route is permission-gated', 'featureEnabled("encyclopedia.view")' in read('app/src/main/java/com/example/bookapp/ui/AppNavigation.kt'))
check('Viewer encyclopedia query is verified-only', 'allVerified()' in read('app/src/main/java/com/example/bookapp/data/EncyclopediaDao.kt'))
check('Glossary linking is connected to reader screen', 'GlossaryTextLinker.link(' in read('app/src/main/java/com/example/bookapp/ui/AppNavigation.kt'))
check('Performance snapshot includes version-bound text and hash', 'class PerformanceSnapshotService' in read('app/src/main/java/com/example/bookapp/data/ArchiveCompletionServices.kt') and 'sha256(json)' in read('app/src/main/java/com/example/bookapp/data/ArchiveCompletionServices.kt'))
check('Version picker replaces manual UID in archive intake', 'انتخاب نسخه تعزیه' in read('app/src/admin/java/com/example/bookapp/ui/screens/ArchiveSourceIntakeScreen.kt'))
check('Version compare and snapshot regression tests added', (ROOT / 'app/src/test/java/com/example/bookapp/data/CompletionStage12Test.kt').exists())

for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' | ' + name)
print(f'\nRESULT: {sum(ok for _, ok in checks)}/{len(checks)} checks passed')
sys.exit(0 if all(ok for _, ok in checks) else 1)
