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
import java.nio.file.Paths;
import java.util.Properties;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BuckUtils {
  private static final Logger log = LoggerFactory.getLogger(BuckUtils.class);

  static void build(Path root, Path gen, String target) throws IOException, BuildFailureException {
    log.info("buck build " + target);
    Properties properties = loadBuckProperties(gen);
    String buck = firstNonNull(properties.getProperty("buck"), "buck");
    ProcessBuilder proc =
        new ProcessBuilder(buck, "build", target)
            .directory(root.toFile())
            .redirectErrorStream(true);
    if (properties.containsKey("PATH")) {
      proc.environment().put("PATH", properties.getProperty("PATH"));
    }
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
      throw new InterruptedIOException("interrupted waiting for " + buck);
    }
    if (status != 0) {
      throw new BuildFailureException(out);
    }

    long time = TimeUtil.nowMs() - start;
    log.info(String.format("UPDATED    %s in %.3fs", target, time / 1000.0));
  }

  private static Properties loadBuckProperties(Path gen) throws IOException {
    Properties properties = new Properties();
    Path p = gen.resolve(Paths.get("tools/buck/buck.properties"));
    try (InputStream in = Files.newInputStream(p)) {
      properties.load(in);
    } catch (NoSuchFileException e) {
      // Ignore; will be run from PATH, with a descriptive error if it fails.
    }
    return properties;
  }

  static void displayFailure(String rule, byte[] why, HttpServletResponse res) throws IOException {
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

  static class BuildFailureException extends Exception {
    private static final long serialVersionUID = 1L;

    final byte[] why;

    BuildFailureException(byte[] why) {
      this.why = why;
    }
  }
}
