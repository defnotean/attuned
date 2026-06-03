from __future__ import annotations

import argparse
import os
import socket
import webbrowser
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_PATH = "/tools/asset_customizer/"


class QuietHandler(SimpleHTTPRequestHandler):
	def log_message(self, format: str, *args: object) -> None:
		return


def main() -> None:
	parser = argparse.ArgumentParser(description="Serve the Attuned asset customizer.")
	parser.add_argument("--port", type=int, default=8765, help="Preferred localhost port.")
	parser.add_argument("--no-open", action="store_true", help="Do not open the browser automatically.")
	args = parser.parse_args()

	port = first_open_port(args.port)
	os.chdir(ROOT)
	server = ThreadingHTTPServer(("127.0.0.1", port), QuietHandler)
	url = f"http://127.0.0.1:{port}{TOOL_PATH}"
	print(f"Attuned Asset Customizer: {url}")
	print("Press Ctrl+C to stop.")
	if not args.no_open:
		webbrowser.open(url)
	try:
		server.serve_forever()
	except KeyboardInterrupt:
		print("\nStopped.")
	finally:
		server.server_close()


def first_open_port(preferred: int) -> int:
	for port in range(preferred, preferred + 40):
		with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
			probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
			try:
				probe.bind(("127.0.0.1", port))
			except OSError:
				continue
			return port
	raise RuntimeError(f"No open localhost port found from {preferred} to {preferred + 39}")


if __name__ == "__main__":
	main()
