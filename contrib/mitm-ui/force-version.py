# mitmdump -q -p 8888 -s "force-version.py --version $1"
# Request URL is not changed, only the response context
from mitmproxy import http
import argparse
import re

class Server:
    def __init__(self, version):
        self.version = version

    def request(self, flow: http.HTTPFlow) -> None:
        if "gr-app." in flow.request.pretty_url:
            flow.request.url = re.sub(
                r"polygerrit_ui/([\d.]+)/elements",
                "polygerrit_ui/" + self.version + "/elements",
                flow.request.url)

def start():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", type=str, help="Rapid release version, e.g. 432.0")
    args = parser.parse_args()
    return Server(args.version)
