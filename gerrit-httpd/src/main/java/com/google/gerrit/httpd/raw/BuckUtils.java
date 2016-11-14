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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Properties;

class BuckUtils extends BuildSystem {
  BuckUtils(Path sourceRoot) {
    super(sourceRoot);
  }

  @Override
  protected ProcessBuilder newBuildProcess(Label label) throws IOException {
    Properties properties = loadBuckProperties(
        sourceRoot.resolve("buck-out/gen/tools/buck/buck.properties"));
    String buck = firstNonNull(properties.getProperty("buck"), "buck");
    ProcessBuilder proc = new ProcessBuilder(buck, "build", label.fullName());
    if (properties.containsKey("PATH")) {
      proc.environment().put("PATH", properties.getProperty("PATH"));
    }
    return proc;
  }

  private static Properties loadBuckProperties(Path propPath)
      throws IOException {
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
        .resolve("gen").resolve(label.artifact);
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
    String t = "ui_" + agent;
    return new BuildSystem.Label("gerrit-gwtui", t,
        String.format("gerrit-gwtui/__gwt_binary_%s__/%s.zip", t, t));
  }

  @Override
  public Label polygerritComponents() {
    return new Label("polygerrit-ui", "polygerrit_components",
        "polygerrit-ui/polygerrit_components/" +
        "polygerrit_components.bower_components.zip");
  }

  @Override
  public Label fontZipLabel() {
    return new Label("polygerrit-ui", "fonts", "polygerrit-ui/fonts/fonts.zip");
  }

  @Override
  public String name() {
    return "buck";
  }
}
