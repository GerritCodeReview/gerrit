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

from mitmproxy import ctx, http, net

import mimetypes
import os.path
import re
import time
import zipfile

def createDefaultResponse():
  headers = net.http.Headers()
  headers['Cache-Control'] = "private no-cache"
  headers['Access-Control-Allow-Origin'] = "*"
  response = http.HTTPResponse(
    http_version = b"HTTP/2.0",
    status_code = 200,
    reason = b'',
    headers = headers,
    content = b'',
    timestamp_start = time.time(),
    timestamp_end = time.time(),
  )
  return response

class ServeLocalFiles:

  def load(self, loader):
    loader.add_option(
      name = "devpath",
      typespec = str,
      default = "~/gerrit/polygerit_ui/app/",
      help = "Location of Polgerrit dev sources (e.g. ~/gerrit/polygerit_ui/app/)",
    )

  def readfile(self, path):
    with open(path, 'rb') as contentfile:
      self.read = contentfile.read()
      return self.read

  def request(self, flow):
    path = flow.request.path
    path = path.strip('/')
    path = path.replace("?dom=shadow", "")
    match = re.match("polygerrit_ui/\d+\.\d+/(.+)", path)
    if not match is None:
      path = match.groups()[0]

    content = self.contentFromBowerZip(path)
    if (content is None): content = self.contentFromLocalFileSystem(path)

    if (not content is None):
      self.serve(flow, path, content)

  def serve(self, flow, path, content):
    flow.response = createDefaultResponse();
    flow.response.content = content;

    local_type = mimetypes.guess_type(path)
    if local_type and local_type[0]:
      flow.response.headers['Content-type'] = local_type[0]

  def contentFromBowerZip(self, path):
    if not path.startswith("bower_components/"):
      return None
    with zipfile.ZipFile("/Users/brohlfs/gerrit/bazel-bin/polygerrit-ui/app/test_components.zip") as bower_components_zipfile:
      return bower_components_zipfile.read(path)

  def contentFromLocalFileSystem(self, path):
    local_file = ctx.options.devpath + path
    if (os.path.isfile(local_file)):
      return self.readfile(local_file)
    else:
      return None

  def response(self, flow: http.HTTPFlow):
    return

addons = [
  ServeLocalFiles()
]
