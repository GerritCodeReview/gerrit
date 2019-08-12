import os
import argparse
import mitmproxy
import re
import traceback
import typing
import zipfile
import http.server
import socketserver
import datetime
import math
import mitmproxy.connections
import time

from collections import namedtuple
from mitmproxy import ctx
from pathlib import Path
from mitmproxy.script import concurrent



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

    return True


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
      flow.response = mitmproxy.http.HTTPResponse.make(
          status_code = 404
      )
    return True

  def handle_response(self, flow: mitmproxy.http.HTTPFlow):
    return self._request_matcher.match(flow) is not None


