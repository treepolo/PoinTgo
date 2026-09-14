"""Use the preserved official APK decode (not the private clone decode)."""
from __future__ import annotations

import re
import shutil
from pathlib import Path

import build_original_inventory as inv


ROOT = Path(__file__).resolve().parents[1]
inv.DECODED = ROOT / "analysis" / "reverse_engineering" / "original_decoded" / "base"
GRAPH = ROOT / "analysis" / "aot_graph"
LEGACY = ROOT / "analysis" / "aot_unflutter"


def manifest_facts() -> dict:
    path = inv.DECODED / "AndroidManifest.xml"
    text = path.read_text(encoding="utf-8", errors="replace")
    app = re.search(r"<application\b[^>]*\bandroid:name=\"([^\"]+)\"", text)
    components = re.findall(r"android:name=\"([^\"]+)\"", text)
    permissions = re.findall(r"<uses-permission\b[^>]*android:name=\"([^\"]+)\"", text)
    return {
        "path": str(path.relative_to(ROOT)),
        "package": re.search(r'package="([^"]+)"', text).group(1),
        "application": app.group(1) if app else None,
        "component_count": len(components),
        "components": sorted(set(components)),
        "permissions": sorted(set(permissions)),
        "lines": len(text.splitlines()),
    }


def main() -> None:
    originals = [GRAPH / name for name in ("objects.jsonl", "edges.jsonl", "code_map.jsonl")]
    copies = [LEGACY / name for name in ("objects.jsonl", "edges.jsonl", "code_map.jsonl")]
    try:
        for source, target in zip(originals, copies):
            shutil.copyfile(source, target)
        inv.manifest_facts = manifest_facts
        inv.main()
    finally:
        for target in copies:
            target.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
