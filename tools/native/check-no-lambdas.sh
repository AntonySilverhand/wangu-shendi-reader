#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
NATIVE_DIR="$REPO_ROOT/android-native"

python3 -c '
import os, sys, re

repo = "'"$NATIVE_DIR"'"
java_files = []
for root, dirs, files in os.walk(repo):
    if "build" in root or ".gradle" in root:
        continue
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

issues = 0
for f in java_files:
    content = open(f, "r", encoding="utf-8").read()
    lines = content.splitlines()
    for idx, line in enumerate(lines, 1):
        clean_line = re.sub(r"\"(\\.|[^\"])*\"", "", line)
        clean_line = re.sub(r"//.*", "", clean_line)
        if "->" in clean_line:
            print(f"ERROR: Lambda detected in {f}:{idx}:\n  {line}")
            issues += 1

    decls = list(re.finditer(r"\b(class|interface|enum|@interface)\s+([A-Za-z0-9_]+)", content))
    if len(decls) > 1:
        print(f"ERROR: Multiple top-level declarations in {f}: {[d.group(0) for d in decls]}")
        issues += 1

    anon = re.findall(r"new\s+[A-Za-z0-9_<>, ]+\s*\([^)]*\)\s*\{", content)
    if anon:
        print(f"ERROR: Anonymous class detected in {f}: {anon}")
        issues += 1

if issues > 0:
    print(f"\nFAILED: Found {issues} violations of zero lambdas / zero inner classes constraint.", file=sys.stderr)
    sys.exit(1)

print(f"PASSED: Checked {len(java_files)} Java files. Zero lambdas, zero inner classes, zero anonymous classes.")
'
