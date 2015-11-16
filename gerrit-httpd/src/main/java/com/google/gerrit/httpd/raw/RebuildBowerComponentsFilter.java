// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.httpd.raw.BuckBuildFilter.BuildFailureException;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

@Singleton
class RebuildBowerComponentsFilter implements Filter {
  private static final String TARGET = "//polygerrit-ui:polygerrit_components";

  private final Path gen;
  private final Path root;
  private final Path zip;

  RebuildBowerComponentsFilter(Path buckOut) {
    gen = buckOut.resolve("gen");
    root = buckOut.getParent();
    zip = BowerComponentsServlet.getZipPath(buckOut);
  }

  @Override
  public synchronized void doFilter(ServletRequest sreq, ServletResponse sres,
      FilterChain chain) throws IOException, ServletException {
    HttpServletResponse res = (HttpServletResponse) sres;
    try {
      BuckBuildFilter.build(root, gen, TARGET);
    } catch (BuildFailureException e) {
      BuckBuildFilter.displayFailure(TARGET, e.why, res);
      return;
    }
    if (!Files.exists(zip)) {
      String msg = "`buck build` did not produce " + zip.toAbsolutePath();
      BuckBuildFilter.displayFailure(TARGET, msg.getBytes(UTF_8), res);
    }
    GerritLauncher.reloadZipFileSystem(zip);
    chain.doFilter(sreq, sres);
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
  }

  @Override
  public void destroy() {
  }
}
