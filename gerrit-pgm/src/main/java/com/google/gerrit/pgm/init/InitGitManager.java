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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.api.InitUtil.die;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;

/** Initialize the GitRepositoryManager configuration section. */
@Singleton
class InitGitManager implements InitStep {
  private final ConsoleUI ui;
  private final Section gerrit;

  @Inject
  InitGitManager(ConsoleUI ui, Section.Factory sections) {
    this.ui = ui;
    this.gerrit = sections.get("gerrit", null);
  }

  @Override
  public void run() {
    ui.header("Git Repositories");

    Path d = gerrit.path("Location of Git repositories", "basePath", "git");
    if (d == null) {
      throw die("gerrit.basePath is required");
    }
    FileUtil.mkdirsOrDie(d, "Cannot create");
  }
}
