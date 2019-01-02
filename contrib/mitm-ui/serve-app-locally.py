# bazel build polygerrit-ui/app:gr-app
# mitmdump -s "serve-app-locally.py ~/gerrit/bazel-bin/polygerrit-ui/app"
from mitmproxy import http
import argparse
import os
import zipfile

class Server:
    def __init__(self, bundle):
        self.bundle = bundle
        self.bundlemtime = 0
        self.files = {
            'polygerrit_ui/elements/gr-app.js': '',
            'polygerrit_ui/elements/gr-app.html': '',
            'polygerrit_ui/styles/main.css': '',
        }
        self.read_files()

    def read_files(self):
        if not os.path.isfile(self.bundle):
            print("bundle not found!")
            return
        mtime = os.stat(self.bundle).st_mtime
        if mtime <= self.bundlemtime:
            return
        self.bundlemtime = mtime
        with zipfile.ZipFile(self.bundle) as z:
            for fname in self.files:
                print('Reading new content for ' + fname)
                with z.open(fname, 'r') as content_file:
                    self.files[fname] = content_file.read()

    def response(self, flow: http.HTTPFlow) -> None:
        self.read_files()
        for name in self.files:
            if name.rsplit('/', 1)[1] in flow.request.pretty_url:
                flow.response.content = self.files[name]

def expandpath(path):
    return os.path.expanduser(path)

def start():
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=str)
    args = parser.parse_args()
    return Server(expandpath(args.bundle))
