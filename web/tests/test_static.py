"""Verify the generated deployment using a real static HTTP server, without a browser."""
import functools
import hashlib
import http.server
import json
import pathlib
import re
import threading
import unittest
import urllib.request
import zipfile
import sys
import tempfile

WEB = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(WEB))
from serve import StaticHandler
from build import publish_directory


class StaticDeploymentTests(unittest.TestCase):
    def test_engine_archive_matches_android_sources(self):
        sources = WEB.parent / "app/src/main/python"
        with zipfile.ZipFile(WEB / "engine.zip") as archive:
            self.assertEqual({p.name for p in sources.glob("*.py")}, set(archive.namelist()))
            for name in archive.namelist():
                self.assertEqual((sources / name).read_bytes(), archive.read(name), name)

    def test_complete_manifest_serves_under_a_nested_path_with_wasm_mime(self):
        source = (WEB / "assets.js").read_text(encoding="utf-8")
        assets = json.loads(re.search(r"self.CALCMAX_ASSETS = (\[[\s\S]+\]);", source).group(1))

        class QuietHandler(StaticHandler):
            def log_message(self, *_):
                pass

        handler = functools.partial(QuietHandler, directory=str(WEB.parent))
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            for asset in assets:
                url = f"http://127.0.0.1:{server.server_port}/web/{asset.removeprefix('./')}"
                with urllib.request.urlopen(url, timeout=10) as response:
                    self.assertEqual(response.status, 200, asset)
                    body = response.read()
                    self.assertTrue(body, asset)
                    if asset.endswith(".wasm"):
                        self.assertEqual(response.headers.get_content_type(), "application/wasm")
                        self.assertEqual(body[:4], b"\x00asm")
                    if asset.endswith((".js", ".mjs")):
                        self.assertIn(response.headers.get_content_type(), ("text/javascript", "application/javascript"))
            for essential in ("./worker.js", "./engine.zip", "./i18n.js", "./index.html", "./README.md", "./THIRD_PARTY.md"):
                self.assertIn(essential, assets)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_wheels_match_pinned_distribution_checksums(self):
        lock = json.loads((WEB / "vendor/pyodide-lock.json").read_text(encoding="utf-8"))
        for name in ("sympy", "mpmath"):
            item = lock["packages"][name]
            digest = hashlib.sha256((WEB / "vendor" / item["file_name"]).read_bytes()).hexdigest()
            self.assertEqual(digest, item["sha256"], name)

    def test_pages_artifact_is_complete_and_needs_no_development_server(self):
        with tempfile.TemporaryDirectory() as directory:
            publish_directory(directory)
            site = pathlib.Path(directory)
            self.assertTrue((site / "index.html").is_file())
            self.assertTrue((site / "vendor/pyodide.mjs").is_file())
            self.assertTrue((site / "vendor/pyodide.asm.wasm").is_file())
            self.assertTrue((site / "engine.zip").is_file())
            self.assertTrue((site / ".nojekyll").is_file())
            self.assertFalse((site / "serve.py").exists())
            self.assertFalse((site / "node_modules").exists())
            self.assertFalse((site / "tests").exists())
            self.assertEqual((site / "worker.js").read_bytes(), (WEB / "worker.js").read_bytes())


if __name__ == "__main__":
    unittest.main()
