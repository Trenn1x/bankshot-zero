#!/usr/bin/env python3
"""Serve the browser build and save screenshots from the local developer QA page."""

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
from urllib.parse import parse_qs, urlsplit


PROJECT_ROOT = Path(__file__).resolve().parent.parent
WEB_ROOT = PROJECT_ROOT / "dist" / "web"
SCREENSHOT_ROOT = PROJECT_ROOT / "docs" / "screenshots"
MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024
SAFE_FILENAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}\.jpg\Z")


class QARequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_ROOT), **kwargs)

    def do_POST(self):
        request = urlsplit(self.path)
        if request.path != "/__qa/screenshot":
            self.send_error(404, "Unknown QA endpoint")
            return

        names = parse_qs(request.query, keep_blank_values=True).get("name", [])
        if len(names) != 1 or not SAFE_FILENAME.fullmatch(names[0]):
            self.send_error(400, "Use a safe .jpg basename, for example title.jpg")
            return

        if self.headers.get("Transfer-Encoding"):
            self.send_error(400, "Send the JPEG with a Content-Length header")
            return
        lengths = self.headers.get_all("Content-Length", [])
        if len(lengths) != 1:
            self.send_error(411, "Content-Length is required")
            return
        try:
            length = int(lengths[0])
        except ValueError:
            self.send_error(400, "Invalid Content-Length")
            return
        if length <= 0:
            self.send_error(400, "Screenshot must not be empty")
            return
        if length > MAX_SCREENSHOT_BYTES:
            self.send_error(413, "Screenshot exceeds 5 MB")
            return

        self.connection.settimeout(15)
        try:
            screenshot = self.rfile.read(length)
        except TimeoutError:
            self.send_error(408, "Screenshot upload timed out")
            return
        if len(screenshot) != length:
            self.send_error(400, "Incomplete screenshot upload")
            return
        if not screenshot.startswith(b"\xff\xd8") or not screenshot.endswith(b"\xff\xd9"):
            self.send_error(400, "Expected raw JPEG bytes")
            return

        SCREENSHOT_ROOT.mkdir(parents=True, exist_ok=True)
        destination = SCREENSHOT_ROOT / names[0]
        if destination.is_symlink():
            self.send_error(400, "Screenshot destination must not be a symbolic link")
            return
        destination.write_bytes(screenshot)
        response = f"Saved docs/screenshots/{names[0]}\n".encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)


def main():
    if not (WEB_ROOT / "index.html").is_file():
        raise SystemExit("Missing dist/web/index.html. Run ./scripts/build.sh first.")
    with ThreadingHTTPServer(("127.0.0.1", 8766), QARequestHandler) as server:
        print("QA game: http://127.0.0.1:8766", flush=True)
        print("Screenshot uploads: POST /__qa/screenshot?name=title.jpg", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
