import os
import mitmproxy
import re
import traceback
import typing
import zipfile
import datetime
import math
import mitmproxy.connections
import time

from collections import namedtuple
from mitmproxy import ctx
from pathlib import Path


class OptionsAddon:
  def __init__(self):
    self._handlers = []
    self._cdn_mock_address = "cdn.googlesource.com"

  def load(self, loader: mitmproxy.addonmanager.Loader):
    #All shared options must be added here
    loader.add_option(
        name = "host",
        typespec = str,
        default = "gerrit-review.googlesource.com",
        help = "Host name"
    )

    loader.add_option(
        name = "cdn_pattern",
        typespec = str,
        default = "https://cdn.googlesource.com/polygerrit_ui/[0-9.]*",
        help = "Cdn pattern"
    )

    loader.add_option(
        name = "polyui_dir",
        typespec = str,
        default = "",
        help = "Path to polygerrit-ui directory"
    )

    loader.add_option(
        name="resource_base_dir",
        typespec = str,
        default = "",
        help = "Path to resource directory"
    )

  def configure(self, updated: typing.Set[str]):
    try:
      ctx.log.info("Configure!!!")
      ctx.log.info(str(ctx.options.listen_port))
      archive_base_path = Path(ctx.options.resource_base_dir).joinpath("gerrit", "polygerrit-ui")
      fs_source = FileSystemSource(Path(ctx.options.polyui_dir).joinpath("app"))
      bower_components_source = ZipFileSource(archive_base_path.joinpath("app", "test_components.zip"), "bower_components")
      fonts_source = ZipFileSource(archive_base_path.joinpath("fonts.zip"), "fonts")
      ctx.log.info(str(archive_base_path))
      cdnPathReplacer = RegexTextReplacer(ctx.options.cdn_pattern, "https://" + self._cdn_mock_address)
      self._handlers = [
          CustomSourceHandler(HostPathMatcher("csp.withgoogle.com", "/csp/"), EmptyContentSource()),
          ProxyHandler(NonGetMethodMatcher()),
          CustomSourceHandler(HostPathMatcher(self._cdn_mock_address, "/bower_components/"), bower_components_source, allow_cache = True),
          CustomSourceHandler(HostPathMatcher(self._cdn_mock_address, "/fonts/"), fonts_source, allow_cache = True),
          CustomSourceHandler(HostPathMatcher(self._cdn_mock_address), fs_source),
          ProxyHandler(HostPathExactMatcher(ctx.options.host, "/"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathExactMatcher(ctx.options.host, "/index.html"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathExactMatcher(ctx.options.host, "/logout"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/accounts/")),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/changes/")),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/config/")),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/projects/")),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/static/")),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/groups/")),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/login/"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/loginz"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/q/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/c/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/p/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/x/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/dashboard/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostPathMatcher(ctx.options.host, "/admin/"), new_path = "/", response_content_updater = cdnPathReplacer),
          CustomSourceHandler(HostPathMatcher(ctx.options.host, "/bower_components/"), bower_components_source),

          CustomSourceHandler(HostPathMatcher(ctx.options.host), fs_source),
          ProxyHandler(HostPathMatcher("accounts.google.com")),
          ProxyHandler(HostPathMatcher("www.goooglesource.com")),
          ProxyHandler(RegexUrlMatcher(".*")),
          BlockRequestHandler(RegexUrlMatcher(".*"))
      ]
      ctx.log.info(str(len(self._handlers)))
    except Exception as e:
      error = traceback.format_exc()
      ctx.log.error("Can't configure handler: " + str(e) + "\n" + error)
    ctx.log.info("Number of installed handlers: " + str(len(self._handlers)))

  def request(self, flow: mitmproxy.http.HTTPFlow):
    for handler in self._handlers:
      if handler.handle_request(flow):
        return

    #ctx.log.warn("The request was not handled: " + flow.request.url)

  def response(self, flow: mitmproxy.http.HTTPFlow):
    try:
      for handler in self._handlers:
        if handler.handle_response(flow):
          return
      ctx.log.warn("The response URL was not handled: " + flow.request.url)
    except Exception as e:
      error = traceback.format_exc()
      ctx.log.warn("Error in response handler for URL: " + flow.request.url)
      ctx.log.error("Can't configure handler: " + str(e) + "\n" + error)
      flow.response = mitmproxy.http.HTTPResponse.make(
          status_code = 500
      )



UrlMatchResult = namedtuple("UrlMatchResult", ["relative_path"])


class CustomSourceHandler:
  def __init__(self, request_matcher, custom_source, allow_cache = False):
    self._request_matcher = request_matcher
    self._custom_source = custom_source
    self._allow_cache = allow_cache

  def handle_request(self, flow: mitmproxy.http.HTTPFlow):
    match = self._request_matcher.match(flow)
    if not match:
      return False
    try:
      if_modified_since_str = flow.request.headers.get("If-Modified-Since", None)
      date_format = "%a, %d %b %Y %H:%M:%S GMT"
      if_modified_since = datetime.datetime.strptime(if_modified_since_str, date_format) if if_modified_since_str else None


      new_content, last_modified = self._custom_source.get_content_if_modified(match.relative_path, if_modified_since)
      if new_content is not None:
        headers = {
            "Access-Control-Allow-Origin": "*",
            "X-Proxy-Listen-Port": str(ctx.options.listen_port),
        }
        if not self._allow_cache:
          headers["Cache-Control"] = "no-cache,must-revalidate"
        else:
          headers["Cache-Control"] = "max-age=3600"

        if last_modified:
          headers["Last-Modified"] = last_modified.strftime("%a, %d %b %Y %H:%M:%S GMT")
        flow.response = mitmproxy.http.HTTPResponse.make(
            status_code = 200,
            content = new_content,
            headers = headers
        )
      else:
        flow.response = mitmproxy.http.HTTPResponse.make(
            status_code = 304
        )
    except FileNotFoundError:
      #ctx.log.warn("File not found: " + match.relative_path)
      flow.response = mitmproxy.http.HTTPResponse.make(
          status_code = 404
      )
    return True

    #return True if self._request_matcher.match(flow) else False

  def handle_response(self, flow: mitmproxy.http.HTTPFlow):
    match = self._request_matcher.match(flow)
    if not match:
      return False
    #flow.response.headers["Connection"] = "close"
    return True
    try:
      new_content = self._custom_source.get_content_if_modified(match.relative_path)
      #if flow.response.status_code == 404:
      #  flow.response.status_code = 200
      #flow.response.status_code = 200
      #flow.response.content = new_content
      #flow.response.headers["Access-Control-Allow-Origin"] = "*"
      #del flow.response.headers["X-Content-Type-Options"]
      flow.response = mitmproxy.http.HTTPResponse.make(
          status_code = 200,
          content = new_content,
          headers = {
              "Access-Control-Allow-Origin": "*"
          }
      )
    except FileNotFoundError:
      #ctx.log.warn("File not found: " + match.relative_path)
      flow.response = mitmproxy.http.HTTPResponse.make(
          status_code = 404
      )
    return True

class ProxyHandler:
  def __init__(self, request_matcher, new_path = None, response_content_updater = None):
    self._request_matcher = request_matcher
    self._new_path = new_path
    self._response_content_updater = response_content_updater

  def handle_request(self, flow: mitmproxy.http.HTTPFlow):
    if not self._request_matcher.match(flow):
      return False
    if self._new_path:
      flow.request.path = self._new_path
    return True

  def handle_response(self, flow: mitmproxy.http.HTTPFlow):
    if not self._request_matcher.match(flow):
      return False
    response = flow.response
    response.headers["Access-Control-Allow-Origin"] = "*"
    if self._response_content_updater:
      response.text = self._response_content_updater.update_text(response.text)
    # if "Access-Control-Allow-Origin" in flow.response.headers:

    return True if self._request_matcher.match(flow) else False

class BlockRequestHandler:
  def __init__(self, request_matcher):
    self._request_matcher = request_matcher

  def handle_request(self, flow: mitmproxy.http.HTTPFlow):
    if not self._request_matcher.match(flow):
      return False
    flow.kill()
    ctx.log.info("Request blocked: " + flow.request.url)
    return True

class FileSystemSource:
  def __init__(self, base_path):
    if isinstance(base_path, str):
      base_path = Path(base_path)
    self._base_path = base_path

  def get_content_if_modified(self, relative_path: str, if_modified_since = None):
    if relative_path.startswith('/'):
      relative_path = relative_path[1:]
    full_path = self._base_path.joinpath(relative_path).expanduser()
    last_modified = datetime.datetime.fromtimestamp(math.ceil(os.path.getmtime(full_path)))
    if if_modified_since and last_modified <= if_modified_since:
      return (None, last_modified)
    with open(full_path, mode='rb') as f:
      return (f.read(), last_modified)

class EmptyContentSource:
  def get_content_if_modified(self, relative_path: str, if_modified_since = None):
    return ("", None)

class ZipFileSource:
  def __init__(self, zip_file_path, base_path=None):
    try:
      str_path = str(zip_file_path.resolve())
      self._zip_file = zipfile.ZipFile(str_path, 'r')
      if base_path and isinstance(base_path, str):
        base_path = Path(base_path)
      self._base_path = base_path
    except:
      ctx.log.error("Can't open zip file: " + zip_file_path)
      self._zip_file = None

  def get_content_if_modified(self, relative_path: str, if_modified_since = None):
    ctx.log.info("Get zip")
    if not self._zip_file:
      return ("", None)
    full_path = self._base_path.joinpath(relative_path) if self._base_path else relative_path
    ctx.log.info(str(full_path))
    return (self._zip_file.read(str(full_path)), None)

class RegexUrlMatcher:
  def __init__(self, pattern):
    self._pattern = re.compile(pattern)

  def match(self, flow: mitmproxy.http.HTTPFlow):
    url = flow.request.url
    match = self._pattern.match(url)
    if not match:
      return None

    return UrlMatchResult(relative_path = url[match.end():])

class NonGetMethodMatcher:
  def match(self, flow: mitmproxy.http.HTTPFlow):
    return flow.request.method != "GET"

class HostPathMatcher:
  def __init__(self, host, path = None):
    self._host = host
    self._path = path

  def match(self, flow: mitmproxy.http.HTTPFlow):
    if flow.request.host != self._host:
      return None

    if not self._path:
      return UrlMatchResult(relative_path = flow.request.path)

    if not flow.request.path.startswith(self._path):
      return None

    return UrlMatchResult(relative_path = flow.request.path[len(self._path):])

class HostPathExactMatcher:
  def __init__(self, host, path):
    self._host = host
    self._path = path

  def match(self, flow: mitmproxy.http.HTTPFlow):
    if flow.request.host != self._host or flow.request.path != self._path:
      return None

    return UrlMatchResult(relative_path = flow.request.path[len(self._path):])

class RegexTextReplacer:
  def __init__(self, pattern, replacement):
    self._pattern = re.compile(pattern)
    self._replacement = replacement

  def update_text(self, text):
    return self._pattern.sub(self._replacement, text)

addons = [OptionsAddon()]

