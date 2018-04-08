// Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.io.ByteStreams;
import com.google.gerrit.config.GitwebCgiConfig;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
@Singleton
class GitwebJavaScriptServlet extends HttpServlet {
  private final long modified;
  private final byte[] raw;

  @Inject
  GitwebJavaScriptServlet(GitwebCgiConfig gitwebCgiConfig) throws IOException {
    byte[] png;
    Path src = gitwebCgiConfig.getGitwebJs();
    if (src != null) {
      try (InputStream in = Files.newInputStream(src)) {
        png = ByteStreams.toByteArray(in);
      } catch (NoSuchFileException e) {
        png = null;
      }
      modified = lastModified(src);
    } else {
      modified = -1;
      png = null;
    }
    raw = png;
  }

  @Override
  protected long getLastModified(HttpServletRequest req) {
    return modified;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    if (raw != null) {
      rsp.setContentType("text/javascript");
      rsp.setContentLength(raw.length);
      rsp.setDateHeader("Last-Modified", modified);
      CacheHeaders.setCacheable(req, rsp, 5, TimeUnit.MINUTES);

      try (ServletOutputStream os = rsp.getOutputStream()) {
        os.write(raw);
      }
    } else {
      CacheHeaders.setNotCacheable(rsp);
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
