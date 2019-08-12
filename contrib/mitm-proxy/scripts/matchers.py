import re
import mitmproxy.connections
from collections import namedtuple
from mitmproxy import ctx

UrlMatchResult = namedtuple("UrlMatchResult", ["relative_path"])

class NonGetMethodMatcher:
  def match(self, flow: mitmproxy.http.HTTPFlow):
    return flow.request.method != "GET"

class RegexUrlMatcher:
  def __init__(self, pattern):
    self._pattern = re.compile(pattern)

  def match(self, flow: mitmproxy.http.HTTPFlow):
    url = flow.request.url
    match = self._pattern.match(url)
    if not match:
      return None

    return UrlMatchResult(relative_path = url[match.end():])

class HostAndPathPrefixMatcher:
  def __init__(self, host, path = None):
    self._host = host
    self._path = path

  def match(self, flow: mitmproxy.http.HTTPFlow):
    if flow.request.host != self._host:
      ctx.log.info(flow.request.host)
      ctx.log.info(self._host)
      return None

    if not self._path:
      return UrlMatchResult(relative_path = flow.request.path)

    if not flow.request.path.startswith(self._path):
      return None

    return UrlMatchResult(relative_path = flow.request.path[len(self._path):])

class HostAndExactPathMatcher:
  def __init__(self, host, path):
    self._host = host
    self._path = path

  def match(self, flow: mitmproxy.http.HTTPFlow):
    if flow.request.host != self._host or flow.request.path != self._path:
      return None

    return UrlMatchResult(relative_path = flow.request.path[len(self._path):])
