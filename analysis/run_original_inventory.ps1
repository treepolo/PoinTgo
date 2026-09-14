# Run the static inventory without a phone.  The AOT graph exporter writes
# three files in a separate folder; copy them to the Python reader's legacy
# location only for the duration of this deterministic run.
$pairs = @(
    @('analysis/aot_graph/objects.jsonl', 'analysis/aot_unflutter/objects.jsonl'),
    @('analysis/aot_graph/edges.jsonl', 'analysis/aot_unflutter/edges.jsonl'),
    @('analysis/aot_graph/code_map.jsonl', 'analysis/aot_unflutter/code_map.jsonl')
)
try {
    foreach ($pair in $pairs) { Copy-Item -LiteralPath $pair[0] -Destination $pair[1] -Force }
    python analysis/build_original_inventory.py
}
finally {
    foreach ($pair in $pairs) {
        if (Test-Path -LiteralPath $pair[1]) { Remove-Item -LiteralPath $pair[1] -Force }
    }
}
