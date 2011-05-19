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

import com.google.gerrit.httpd.GitWebConfig;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
abstract class GitWebCssServlet extends HttpServlet {
  @Singleton
  static class Site extends GitWebCssServlet {
    @Inject
    Site(SitePaths paths, GitWebConfig gwc) throws IOException {
      super(paths.site_css, gwc);
    }
  }

  @Singleton
  static class Default extends GitWebCssServlet {
    @Inject
    Default(GitWebConfig gwc) throws IOException {
      super(gwc.getGitwebCSS(), gwc);
    }
  }

  private static final String ENC = "UTF-8";
  private static final long MAX_AGE =
      TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
  private static final String CACHE_CTRL =
      "public, max-age=" + (MAX_AGE / 1000L);

  private final long modified;
  private final byte[] raw_css;
  private final byte[] gz_css;

  GitWebCssServlet(final File src, final GitWebConfig gitWebConfig)
      throws IOException {
    if (src != null) {
      final File dir = src.getParentFile();
      final String name = src.getName();
      final String raw = HtmlDomUtil.readFile(dir, name);
      if (raw != null) {
        modified = src.lastModified();
        raw_css = raw.getBytes(ENC);
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
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    if (raw_css != null) {
      final long now = System.currentTimeMillis();
      rsp.setContentType("text/css");
      rsp.setCharacterEncoding(ENC);
      final byte[] toSend;
      if (RPCServletUtils.acceptsGzipEncoding(req)) {
        rsp.setHeader("Content-Encoding", "gzip");
        toSend = gz_css;
      } else {
        toSend = raw_css;
      }
      rsp.setContentLength(toSend.length);
      rsp.setHeader("Cache-Control", CACHE_CTRL);
      rsp.setDateHeader("Date", now);
      rsp.setDateHeader("Expires", now + MAX_AGE);
      rsp.setDateHeader("Last-Modified", modified);

      final ServletOutputStream os = rsp.getOutputStream();
      try {
        os.write(toSend);
      } finally {
        os.close();
      }
    } else {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
