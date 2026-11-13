#!/usr/bin/env python3
"""Read-only Stage 11 regression/security contract audit for Tazieh Studio."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, condition, detail=""):
    checks.append((name, bool(condition), detail))


def read(path):
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.exists() else ""

app_gradle = read("app/build.gradle.kts")
db = read("app/src/main/java/com/example/bookapp/data/AppDatabase.kt")
policy = read("app/src/main/java/com/example/bookapp/data/ViewerAccessPolicy.kt")
transfer = read("app/src/main/java/com/example/bookapp/data/ViewerAccessTransfer.kt")
backup = read("app/src/main/java/com/example/bookapp/data/BackupHelper.kt")
release = read(".github/workflows/release.yml")
qa = read(".github/workflows/main.yml")
gitignore = read(".gitignore")
admin_manifest = read("app/src/admin/AndroidManifest.xml")
viewer_manifest = read("app/src/viewer/AndroidManifest.xml")

check("Room schema is 15", "APP_DATABASE_SCHEMA_VERSION = 15" in db and "version = APP_DATABASE_SCHEMA_VERSION" in db)
for old, new in zip(range(5, 15), range(6, 16)):
    check(f"Room migration {old}->{new}", f"MIGRATION_{old}_{new}" in db)
check("All Room migrations registered", ".addMigrations(" in db and all(f"MIGRATION_{i}_{i+1}" in db for i in range(5, 15)))
check("Admin package ID preserved", 'applicationId = "com.example.bookapp"' in app_gradle)
check("Viewer package ID preserved", 'applicationId = "com.example.bookapp.viewer"' in app_gradle)
check("QA application suffix isolated", 'applicationIdSuffix = ".test"' in app_gradle)
check("QA release update URLs empty", 'buildConfigField("String", "UPDATE_SERVER_URLS", "\\"\\"")' in app_gradle)
check("Private policy signer source is Admin-only", (ROOT / "app/src/admin/java/com/example/bookapp/data/ViewerAccessSigner.kt").exists() and "POLICY_PRIVATE_KEY" in app_gradle)
check("Viewer validates signed policies", "SHA256withRSA" in transfer and "initVerify" in transfer)
check("Policy-change receiver protected by signature permission", 'android:permission="${policyChangedPermission}"' in viewer_manifest and 'android:protectionLevel="signature"' in admin_manifest)
check("QA policy-change permission is isolated", 'com.example.bookapp.test.permission.POLICY_CHANGED' in app_gradle)
check("Viewer public write restrictions retained", all(k in policy for k in ["gallery.add", "dictionary.edit", "taziehCorrections.apply"]))
check("Official release is manual", "workflow_dispatch:" in release and "push:" not in release)
check("QA workflow exists", bool(qa) and "workflow_dispatch:" in qa)
check("Signing files excluded from Git", "local.properties" in gitignore and "*.jks" in gitignore and "*.keystore" in gitignore)
check("Portable backup v4 checksum", 'put("backupVersion", 4)' in backup and 'integritySha256' in backup and "validateBackupRoot" in backup)
check("Backup schema follows current Room schema", "put(\"schemaVersion\", APP_DATABASE_SCHEMA_VERSION)" in backup)
check("Backup bounded read", "MAX_BACKUP_BYTES" in backup and "readBounded" in backup)
check("Database integrity + FK diagnostics", "PRAGMA integrity_check" in read("app/src/main/java/com/example/bookapp/data/DatabaseHealthCheck.kt") and "PRAGMA foreign_key_check" in read("app/src/main/java/com/example/bookapp/data/DatabaseHealthCheck.kt"))
check("Backup scope limitation visible in UI", "تصویر کامل پایگاه داده نیست" in read("app/src/main/java/com/example/bookapp/ui/screens/InfoScreens.kt"))
check("Backup integrity regression test exists", "rejects tampering before restore" in read("app/src/test/java/com/example/bookapp/data/DataLayerTest.kt"))
check("Health check regression test exists", "database health check confirms integrity" in read("app/src/test/java/com/example/bookapp/data/DataLayerTest.kt"))
check("AES-GCM backup crypto regression tests exist", "backup encryption round trips" in read("app/src/test/java/com/example/bookapp/data/BackupCryptoTest.kt"))

for name, ok, detail in checks:
    print(("PASS" if ok else "FAIL") + " | " + name + (f" | {detail}" if detail else ""))
print(f"\nRESULT: {sum(ok for _, ok, _ in checks)}/{len(checks)} checks passed")
sys.exit(0 if all(ok for _, ok, _ in checks) else 1)
