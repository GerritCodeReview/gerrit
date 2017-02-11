// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.TimeUtil;
import com.google.gwtexpui.server.CacheHeaders;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BuildSystem {
  private static final Logger log = LoggerFactory.getLogger(BuildSystem.class);

  protected final Path sourceRoot;

  public BuildSystem(Path sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  protected abstract ProcessBuilder newBuildProcess(Label l) throws IOException;

  protected static Properties loadBuildProperties(Path propPath) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(propPath)) {
      properties.load(in);
    } catch (NoSuchFileException e) {
      // Ignore; will be run from PATH, with a descriptive error if it fails.
    }
    return properties;
  }

  // builds the given label.
  public void build(Label label) throws IOException, BuildFailureException {
    ProcessBuilder proc = newBuildProcess(label);
    proc.directory(sourceRoot.toFile()).redirectErrorStream(true);
    log.info("building [" + name() + "] " + label.fullName());
    long start = TimeUtil.nowMs();
    Process rebuild = proc.start();
    byte[] out;
    try (InputStream in = rebuild.getInputStream()) {
      out = ByteStreams.toByteArray(in);
    } finally {
      rebuild.getOutputStream().close();
    }

    int status;
    try {
      status = rebuild.waitFor();
    } catch (InterruptedException e) {
      throw new InterruptedIOException("interrupted waiting for " + proc.toString());
    }
    if (status != 0) {
      log.warn("build failed: " + new String(out));
      throw new BuildFailureException(out);
    }

    long time = TimeUtil.nowMs() - start;
    log.info(String.format("UPDATED    %s in %.3fs", label.fullName(), time / 1000.0));
  }

  // Represents a label in either buck or bazel.
  class Label {
    protected final String pkg;
    protected final String name;

    // Regrettably, buck confounds rule names and artifact names,
    // and so we have to lug this along. Non-null only for Buck; in that case,
    // holds the path relative to buck-out/gen/
    protected final String artifact;

    public String fullName() {
      return "//" + pkg + ":" + name;
    }

    @Override
    public String toString() {
      String s = fullName();
      if (!name.equals(artifact)) {
        s += "(" + artifact + ")";
      }
      return s;
    }

    // Label in Buck style.
    Label(String pkg, String name, String artifact) {
      this.name = name;
      this.pkg = pkg;
      this.artifact = artifact;
    }

    // Label in Bazel style.
    Label(String pkg, String name) {
      this(pkg, name, name);
    }
  }

  class BuildFailureException extends Exception {
    private static final long serialVersionUID = 1L;

    final byte[] why;

    BuildFailureException(byte[] why) {
      this.why = why;
    }

    public void display(String rule, HttpServletResponse res) throws IOException {
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.setContentType("text/html");
      res.setCharacterEncoding(UTF_8.name());
      CacheHeaders.setNotCacheable(res);

      Escaper html = HtmlEscapers.htmlEscaper();
      try (PrintWriter w = res.getWriter()) {
        w.write("<html><title>BUILD FAILED</title><body>");
        w.format("<h1>%s FAILED</h1>", html.escape(rule));
        w.write("<pre>");
        w.write(html.escape(RawParseUtils.decode(why)));
        w.write("</pre>");
        w.write("</body></html>");
      }
    }
  }

  /** returns the command to build given target */
  abstract String buildCommand(Label l);

  /** returns the root relative path to the artifact for the given label */
  abstract Path targetPath(Label l);

  /** Label for the agent specific GWT zip. */
  abstract Label gwtZipLabel(String agent);

  /** Label for the polygerrit component zip. */
  abstract Label polygerritComponents();

  /** Label for the fonts zip file. */
  abstract Label fontZipLabel();

  /** Build system name. */
  abstract String name();
}
