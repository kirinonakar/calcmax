"""Development static server with portable JS/WASM MIME types (no calculation API)."""
import argparse
import functools
import http.server
import pathlib

WEB = pathlib.Path(__file__).resolve().parent


class StaticHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        # Development modules must all come from the current checkout. A cached
        # dependency can lack exports required by a newly edited importer.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def send_head(self):
        # File timestamps have second precision in HTTP. Two edits in the same
        # second must not produce a 304 with an older module body.
        if "If-Modified-Since" in self.headers:
            del self.headers["If-Modified-Since"]
        return super().send_head()

    # Windows registry MIME mappings can otherwise serve .mjs as text/plain,
    # which browsers reject when the Pyodide loader imports its WASM bootstrap.
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript",
        ".mjs": "text/javascript",
        ".wasm": "application/wasm",
        ".css": "text/css",
        ".html": "text/html",
        ".json": "application/json",
        ".whl": "application/octet-stream",
        ".zip": "application/zip",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--bind", default="127.0.0.1")
    options = parser.parse_args()
    handler = functools.partial(StaticHandler, directory=str(WEB))
    with http.server.ThreadingHTTPServer((options.bind, options.port), handler) as server:
        print(f"CalcMax: http://{options.bind}:{server.server_port} (static files only)", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
