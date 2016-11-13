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

import java.io.IOException;
import java.nio.file.Path;

public class BazelBuild extends BuildSystem {
  public BazelBuild(Path sourceRoot) { super(sourceRoot); }

  @Override
  public void build(Label l) throws IOException, BuildFailureException {
    throw new BuildFailureException("not implemented yet.".getBytes());
  }

  protected ProcessBuilder newBuildProcess(Label label) throws IOException {
    ProcessBuilder proc = new ProcessBuilder("bazel", "build", label.fullName());
    return proc;
  }

  @Override
  public String buildCommand(Label l) {
    return "bazel build " + l.toString();
  }

  @Override
  public Path targetPath(Label l) {
    return sourceRoot.resolve("bazel-bin").resolve(l.pkg).resolve(l.name);
  }

  @Override
  public Label gwtZipLabel(String agent) {
    return new Label("gerrit-gwtui", "ui_" + agent + ".zip");
  }

  @Override
  public Label polygerritComponents() {
    return new Label("polygerrit-ui",
        "polygerrit_components.bower_components.zip");
  }

  @Override
  public Label fontZipLabel() {
    return new Label("polygerrit-ui", "fonts.zip");
  }
}
