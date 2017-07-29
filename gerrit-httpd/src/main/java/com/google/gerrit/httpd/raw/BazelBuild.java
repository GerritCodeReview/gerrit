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

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.TimeUtil;
import com.google.gwtexpui.server.CacheHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BazelBuild {
  private static final Logger log = LoggerFactory.getLogger(BazelBuild.class);

  private final Path sourceRoot;

  public BazelBuild(Path sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  // builds the given label.
  public void build(Label label) throws IOException, BuildFailureException {
    ProcessBuilder proc = newBuildProcess(label);
    proc.directory(sourceRoot.toFile()).redirectErrorStream(true);
    log.info("building " + label.fullName());
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
      log.warn("build failed: " + new String(out, UTF_8));
      throw new BuildFailureException(out);
    }

    long time = TimeUtil.nowMs() - start;
    log.info(String.format("UPDATED    %s in %.3fs", label.fullName(), time / 1000.0));
  }

  // Represents a label in bazel.
  static class Label {
    protected final String pkg;
    protected final String name;

    public String fullName() {
      return "//" + pkg + ":" + name;
    }

    @Override
    public String toString() {
      return fullName();
    }

    // Label in Bazel style.
    Label(String pkg, String name) {
      this.name = name;
      this.pkg = pkg;
    }
  }

  static class BuildFailureException extends Exception {
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

  private Properties loadBuildProperties(Path propPath) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(propPath)) {
      properties.load(in);
    } catch (NoSuchFileException e) {
      // Ignore; will be run from PATH, with a descriptive error if it fails.
    }
    return properties;
  }

  private ProcessBuilder newBuildProcess(Label label) throws IOException {
    Properties properties = loadBuildProperties(sourceRoot.resolve(".bazel_path"));
    String bazel = firstNonNull(properties.getProperty("bazel"), "bazel");
    ProcessBuilder proc = new ProcessBuilder(bazel, "build", label.fullName());
    if (properties.containsKey("PATH")) {
      proc.environment().put("PATH", properties.getProperty("PATH"));
    }
    return proc;
  }

  /** returns the root relative path to the artifact for the given label */
  public Path targetPath(Label l) {
    return sourceRoot.resolve("bazel-bin").resolve(l.pkg).resolve(l.name);
  }

  /** Label for the polygerrit component zip. */
  public Label polygerritComponents() {
    return new Label("polygerrit-ui", "polygerrit_components.bower_components.zip");
  }

  /** Label for the fonts zip file. */
  public Label fontZipLabel() {
    return new Label("polygerrit-ui", "fonts.zip");
  }
}
