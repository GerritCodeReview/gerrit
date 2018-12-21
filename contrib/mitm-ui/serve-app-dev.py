# 1. install and setup mitmproxy v2.0.2: https://mitmproxy.readthedocs.io/en/v2.0.2/install.html
#   (In case of python versions trouble, use https://www.anaconda.com/)
# 2. mitmdump -q -s -p 8888 \
#   "serve-app-dev.py --app /path/to/polygerrit-ui/app/"
# 3. start Chrome with --proxy-server="127.0.0.1:8888" --user-data-dir=/tmp/devchrome
# 4. open, say, gerrit-review.googlesource.com. Or chromium-review.googlesource.com. Any.
# 5. uncompiled source files are served and you can log in, too.
# 6. enjoy!
#
# P.S. For replacing plugins, use --plugins or --plugin_root
#
# --plugin takes comma-separated list of plugins to add or replace.
#
# Example: Adding a new plugin to the server response:
# --plugins ~/gerrit-testsite/plugins/myplugin.html
#
# Example: Replace all matching plugins with local versions:
# --plugins ~/gerrit-testsite/plugins/
# Following files will be served if they exist for /plugins/tricium/static/tricium.html:
#  ~/gerrit-testsite/plugins/tricium.html
#  ~/gerrit-testsite/plugins/tricium/static/tricium.html
#
# --assets takes assets bundle.html, expecting rest of the assets files to be in the same folder
#
# Example:
#  --assets ~/gerrit-testsite/assets/a3be19f.html
#

from mitmproxy import http
from mitmproxy.script import concurrent
import re
import argparse
import os.path
import json

class Server:
    def __init__(self, devpath, plugins, pluginroot, assets, strip_assets):
        if devpath:
            print("Serving app from " + devpath)
        if pluginroot:
            print("Serving plugins from " + pluginroot)
        if assets:
            self.assets_root, self.assets_file = os.path.split(assets)
            print("Assets: using " + self.assets_file + " from " + self.assets_root)
        else:
            self.assets_root = None
        if plugins:
            self.plugins = {path.split("/")[-1:][0]: path for path in map(expandpath, plugins.split(","))}
            for filename, path in self.plugins.items():
                print("Serving " + filename + " from " + path)
        else:
            self.plugins = {}
        self.devpath = devpath
        self.pluginroot = pluginroot
        self.strip_assets = strip_assets

    def readfile(self, path):
        with open(path, 'rb') as contentfile:
            return contentfile.read()

@concurrent
def response(flow: http.HTTPFlow) -> None:
    if server.strip_assets:
        assets_bundle = 'googlesource.com/polygerrit_assets'
        assets_pos = flow.response.text.find(assets_bundle)
        if assets_pos != -1:
            t = flow.response.text
            flow.response.text = t[:t.rfind('<', 0, assets_pos)] + t[t.find('>', assets_pos) + 1:]
            return

    if server.assets_root:
        marker = 'webcomponents-lite.js"></script>'
        pos = flow.response.text.find(marker)
        if pos != -1:
            pos += len(marker)
            flow.response.text = ''.join([
                flow.response.text[:pos],
                '<link rel="import" href="/gerrit_assets/123.0/' + server.assets_file + '">',
                flow.response.text[pos:]
            ])

        assets_prefix = "/gerrit_assets/123.0/"
        if flow.request.path.startswith(assets_prefix):
            assets_file = flow.request.path[len(assets_prefix):]
            flow.response.content = server.readfile(server.assets_root + '/' + assets_file)
            flow.response.status_code = 200
            if assets_file.endswith('.js'):
                flow.response.headers['Content-type'] = 'text/javascript'
            return
    m = re.match(".+polygerrit_ui/\d+\.\d+/(.+)", flow.request.path)
    pluginmatch = re.match("^/plugins/(.+)", flow.request.path)
    localfile = ""
    if flow.request.path == "/config/server/info":
        config = json.loads(flow.response.content[5:].decode('utf8'))
        for filename, path in server.plugins.items():
            pluginname = filename.split(".")[0]
            payload = config["plugin"]["js_resource_paths" if filename.endswith(".js") else "html_resource_paths"]
            if list(filter(lambda url: filename in url, payload)):
                continue
            payload.append("plugins/" + pluginname + "/static/" + filename)
        flow.response.content = str.encode(")]}'\n" + json.dumps(config))
    if m is not None:
        filepath = m.groups()[0]
        localfile = server.devpath + filepath
    elif pluginmatch is not None:
        pluginfile = flow.request.path_components[-1]
        if server.plugins and pluginfile in server.plugins:
            if os.path.isfile(server.plugins[pluginfile]):
                localfile = server.plugins[pluginfile]
            else:
                print("Can't find file " + server.plugins[pluginfile] + " for " + flow.request.path)
        elif server.pluginroot:
            pluginurl = pluginmatch.groups()[0]
            if os.path.isfile(server.pluginroot + pluginfile):
                localfile = server.pluginroot + pluginfile
            elif os.path.isfile(server.pluginroot + pluginurl):
                localfile = server.pluginroot + pluginurl
    if localfile and os.path.isfile(localfile):
        if pluginmatch is not None:
            print("Serving " + flow.request.path + " from " + localfile)
        flow.response.content = server.readfile(localfile)
        flow.response.status_code = 200
        if localfile.endswith('.js'):
            flow.response.headers['Content-type'] = 'text/javascript'

def expandpath(path):
    return os.path.realpath(os.path.expanduser(path))

parser = argparse.ArgumentParser()
parser.add_argument("--app", type=str, default="", help="Path to /polygerrit-ui/app/")
parser.add_argument("--plugins", type=str, default="", help="Comma-separated list of plugin files to add/replace")
parser.add_argument("--plugin_root", type=str, default="", help="Path containing individual plugin files to replace")
parser.add_argument("--assets", type=str, default="", help="Path containing assets file to import.")
parser.add_argument("--strip_assets", action="store_true", help="Strip plugin bundles from the response.")
args = parser.parse_args()
server = Server(expandpath(args.app) + '/',
                args.plugins, expandpath(args.plugin_root) + '/',
                args.assets and expandpath(args.assets),
                args.strip_assets)
