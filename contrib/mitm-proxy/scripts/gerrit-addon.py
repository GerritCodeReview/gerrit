import mitmproxy
import traceback
import typing
import mitmproxy.connections

from mitmproxy import ctx
from pathlib import Path

from handlers import *
from matchers import *
from replacers import *
from sources import *

class GerritAddon:
  def __init__(self):
    self._handlers = []
    self._cdn_mock_address = "cdn.googlesource.com"

  def load(self, loader: mitmproxy.addonmanager.Loader):
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
      name = "polygerrit_ui_dir",
      typespec = str,
      default = "",
      help = "Path to polygerrit-ui directory"
    )

    loader.add_option(
      name="test_components_path",
      typespec = str,
      default = "",
      help = "Path to file with components"
    )

    loader.add_option(
        name="fonts_path",
        typespec = str,
        default = "",
        help = "Path to zip archive with fonts"
    )

  def configure(self, updated: typing.Set[str]):
    try:
      ctx.log.info("Loading configuration and setup handlers...")
      fs_source = FileSystemSource(Path(ctx.options.polygerrit_ui_dir).joinpath("app"))
      bower_components_source = ZipFileSource(ctx.options.test_components_path, "bower_components")
      fonts_source = ZipFileSource(ctx.options.fonts_path, "fonts")

      cdnPathReplacer = RegexTextReplacer(ctx.options.cdn_pattern, "https://" + self._cdn_mock_address)

      #For each request, the first matched handler will be used.
      self._handlers = [
          #Ignore all requests/reports to csp.withgoogle.com/csp/
          CustomSourceHandler(HostAndPathPrefixMatcher("csp.withgoogle.com", "/csp/"), EmptyContentSource()),
          #All requests where method != "GET" are processed without changes
          ProxyHandler(NonGetMethodMatcher()),
          #Requests to cdn are served from local folders
          CustomSourceHandler(HostAndPathPrefixMatcher(self._cdn_mock_address, "/bower_components/"), bower_components_source, allow_cache = True),
          CustomSourceHandler(HostAndPathPrefixMatcher(self._cdn_mock_address, "/fonts/"), fonts_source, allow_cache = True),
          CustomSourceHandler(HostAndPathPrefixMatcher(self._cdn_mock_address), fs_source),

          #Requests return original answers with changed Cdn path
          ProxyHandler(HostAndExactPathMatcher(ctx.options.host, "/"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndExactPathMatcher(ctx.options.host, "/index.html"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndExactPathMatcher(ctx.options.host, "/logout"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/login/"), response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/loginz"), response_content_updater = cdnPathReplacer),

          #Requests are redirected to '/' on original host and Cdn is changed in answers
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/q/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/c/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/p/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/x/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/dashboard/"), new_path = "/", response_content_updater = cdnPathReplacer),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/admin/"), new_path = "/", response_content_updater = cdnPathReplacer),

          #Requests to API return original answers without changes
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/accounts/")),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/changes/")),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/config/")),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/projects/")),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/static/")),
          ProxyHandler(HostAndPathPrefixMatcher(ctx.options.host, "/groups/")),

          #All other requests to host are served from local disk
          CustomSourceHandler(HostAndPathPrefixMatcher(ctx.options.host), fs_source),

          #All other requests to other hosts/domains return non-modifed answers
          ProxyHandler(RegexUrlMatcher(".*")),

      ]

    except Exception as e:
      error = traceback.format_exc()
      ctx.log.error("Can't setup handlers. The following Exception was raised: " + str(e) + "\n" + error)

    ctx.log.info("Configuration complete. Number of installed handlers: " + str(len(self._handlers)))

  def request(self, flow: mitmproxy.http.HTTPFlow):
    try:
      for handler in self._handlers:
        if handler.handle_request(flow):
          return
      ctx.log.error("No request handler was found for the following URL: " + flow.request.url)

    except Exception as e:
      error = traceback.format_exc()
      ctx.log.error("Exception was raised during processing the following request: " + flow.request.url + "\nException: " + str(e) + "\n" + error)
    flow.response = mitmproxy.http.HTTPResponse.make(
        status_code = 500
    )

  def response(self, flow: mitmproxy.http.HTTPFlow):
    try:
      for handler in self._handlers:
        if handler.handle_response(flow):
          return
      ctx.log.error("No request handler was found for the following URL: " + flow.request.url)
    except Exception as e:
      error = traceback.format_exc()
      ctx.log.error("Exception was raised during processing the following response: " + flow.request.url + "\nException: " + str(e) + "\n" + error)
    flow.response = mitmproxy.http.HTTPResponse.make(
        status_code = 500
    )

addons = [GerritAddon()]
