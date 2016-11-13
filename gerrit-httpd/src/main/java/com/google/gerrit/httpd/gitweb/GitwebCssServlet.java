// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.httpd.gitweb;

import static com.google.gerrit.common.FileUtil.lastModified;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.config.GitwebCgiConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
abstract class GitwebCssServlet extends HttpServlet {
  @Singleton
  static class Site extends GitwebCssServlet {
    @Inject
    Site(SitePaths paths) throws IOException {
      super(paths.site_css);
    }
  }

  @Singleton
  static class Default extends GitwebCssServlet {
    @Inject
    Default(GitwebCgiConfig gwcc) throws IOException {
      super(gwcc.getGitwebCss());
    }
  }

  private final long modified;
  private final byte[] raw_css;
  private final byte[] gz_css;

  GitwebCssServlet(final Path src) throws IOException {
    if (src != null) {
      final Path dir = src.getParent();
      final String name = src.getFileName().toString();
      final String raw = HtmlDomUtil.readFile(dir, name);
      if (raw != null) {
        modified = lastModified(src);
        raw_css = raw.getBytes(UTF_8);
        gz_css = HtmlDomUtil.compress(raw_css);
      } else {
        modified = -1L;
        raw_css = null;
        gz_css = null;
      }
    } else {
      modified = -1;
      raw_css = null;
      gz_css = null;
    }
  }

  @Override
  protected long getLastModified(final HttpServletRequest req) {
    return modified;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    if (raw_css != null) {
      rsp.setContentType("text/css");
      rsp.setCharacterEncoding(UTF_8.name());
      final byte[] toSend;
      if (RPCServletUtils.acceptsGzipEncoding(req)) {
        rsp.setHeader("Content-Encoding", "gzip");
        toSend = gz_css;
      } else {
        toSend = raw_css;
      }
      rsp.setContentLength(toSend.length);
      rsp.setDateHeader("Last-Modified", modified);
      CacheHeaders.setCacheable(req, rsp, 5, TimeUnit.MINUTES);

      try (ServletOutputStream os = rsp.getOutputStream()) {
        os.write(toSend);
      }
    } else {
      CacheHeaders.setNotCacheable(rsp);
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
