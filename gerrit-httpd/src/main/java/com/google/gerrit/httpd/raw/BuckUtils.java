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

import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

class BuckUtils implements BuildSystem {
  private static final Logger log =
      LoggerFactory.getLogger(BuckUtils.class);
  private final Path sourceRoot;

  BuckUtils(Path sourceRoot) {
    this.sourceRoot = sourceRoot;
  }

  @Override
  public void build(Label label)
      throws IOException, BuildFailureException {
    log.info("buck build " + label);
    Properties properties = loadBuckProperties(
        sourceRoot.resolve("buck-out/gen/tools/buck/buck.properties"));
    String buck = firstNonNull(properties.getProperty("buck"), "buck");
    ProcessBuilder proc = new ProcessBuilder(buck, "build", label.fullName())
        .directory(sourceRoot.toFile())
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
    log.info(
        String.format("UPDATED    %s in %.3fs", label.fullName(), time / 1000.0));
  }

  private static Properties loadBuckProperties(Path propPath) throws IOException {
    Properties properties = new Properties();
    try (InputStream in = Files.newInputStream(propPath)) {
      properties.load(in);
    } catch (NoSuchFileException e) {
      // Ignore; will be run from PATH, with a descriptive error if it fails.
    }
    return properties;
  }

  @Override
  public Path targetPath(Label label) {
    return sourceRoot.resolve("buck-out")
        .resolve("gen").resolve(label.pkg).resolve(label.name).resolve(label.artifact);
  }

  @Override
  public String buildCommand(Label l) {
    return "buck build " + l.toString();
  }

  @Override
  public Label gwtZipLabel(String agent) {
    // TODO(davido): instead of assuming specific Buck's internal
    // target directory for gwt_binary() artifacts, ask Buck for
    // the location of user agent permutation GWT zip, e. g.:
    // $ buck targets --show_output //gerrit-gwtui:ui_safari \
    //    | awk '{print $2}'
    String target = "ui_" + agent;
    return new BuildSystem.Label("//gerrit-gwtui", String.format("__gwt_binary_%s__", target), target + ".zip");
  }
}
