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

import com.google.gerrit.httpd.raw.BuckUtils.BuildFailureException;
import com.google.gwtexpui.linker.server.UserAgentRule;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class RecompileGwtUiFilter implements Filter {
  private final boolean gwtuiRecompile =
      System.getProperty("gerrit.disable-gwtui-recompile") == null;
  private final UserAgentRule rule = new UserAgentRule();
  private final Set<String> uaInitialized = new HashSet<>();
  private final Path unpackedWar;
  private final Path gen;
  private final Path root;

  private String lastTarget;
  private long lastTime;

  RecompileGwtUiFilter(Path buckOut, Path unpackedWar) {
    this.unpackedWar = unpackedWar;
    gen = buckOut.resolve("gen");
    root = buckOut.getParent();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String pkg = "gerrit-gwtui";
    String target = "ui_" + rule.select((HttpServletRequest) request);
    if (gwtuiRecompile || !uaInitialized.contains(target)) {
      String rule = "//" + pkg + ":" + target;
      // TODO(davido): instead of assuming specific Buck's internal
      // target directory for gwt_binary() artifacts, ask Buck for
      // the location of user agent permutation GWT zip, e. g.:
      // $ buck targets --show_output //gerrit-gwtui:ui_safari \
      //    | awk '{print $2}'
      String child = String.format("%s/__gwt_binary_%s__", pkg, target);
      File zip = gen.resolve(child).resolve(target + ".zip").toFile();

      synchronized (this) {
        try {
          BuckUtils.build(root, gen, rule);
        } catch (BuildFailureException e) {
          BuckUtils.displayFailure(rule, e.why, (HttpServletResponse) res);
          return;
        }

        if (!target.equals(lastTarget) || lastTime != zip.lastModified()) {
          lastTarget = target;
          lastTime = zip.lastModified();
          unpack(zip, unpackedWar.toFile());
        }
      }
      uaInitialized.add(target);
    }
    chain.doFilter(request, res);
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}

  private static void unpack(File srcwar, File dstwar) throws IOException {
    try (ZipFile zf = new ZipFile(srcwar)) {
      final Enumeration<? extends ZipEntry> e = zf.entries();
      while (e.hasMoreElements()) {
        final ZipEntry ze = e.nextElement();
        final String name = ze.getName();

        if (ze.isDirectory()
            || name.startsWith("WEB-INF/")
            || name.startsWith("META-INF/")
            || name.startsWith("com/google/gerrit/launcher/")
            || name.equals("Main.class")) {
          continue;
        }

        final File rawtmp = new File(dstwar, name);
        mkdir(rawtmp.getParentFile());
        rawtmp.deleteOnExit();

        try (FileOutputStream rawout = new FileOutputStream(rawtmp);
            InputStream in = zf.getInputStream(ze)) {
          final byte[] buf = new byte[4096];
          int n;
          while ((n = in.read(buf, 0, buf.length)) > 0) {
            rawout.write(buf, 0, n);
          }
        }
      }
    }
  }

  private static void mkdir(File dir) throws IOException {
    if (!dir.isDirectory()) {
      mkdir(dir.getParentFile());
      if (!dir.mkdir()) {
        throw new IOException("Cannot mkdir " + dir.getAbsolutePath());
      }
      dir.deleteOnExit();
    }
  }
}
