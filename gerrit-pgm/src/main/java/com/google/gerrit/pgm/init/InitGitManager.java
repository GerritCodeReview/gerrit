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

import static com.google.gerrit.pgm.init.InitUtil.die;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;

/** Initialize the GitRepositoryManager configuration section. */
@Singleton
class InitGitManager implements InitStep {
  private final ConsoleUI ui;
  private final Section gerrit;

  @Inject
  InitGitManager(final ConsoleUI ui, final Section.Factory sections) {
    this.ui = ui;
    this.gerrit = sections.get("gerrit");
  }

  public void run() {
    ui.header("Git Repositories");

    File d = gerrit.path("Location of Git repositories", "basePath", "git");
    File b = gerrit.path("Location of archive directory", "archivePath", "archive");

    if (d == null) {
      throw die("gerrit.basePath is required");
    }

    if (b == null) {
      throw die("gerrit.archivePath is required");
    }

    if (!d.exists() && !d.mkdirs()) {
      throw die("Cannot create : " + d);
    }

    if (!b.exists() && !b.mkdirs()) {
      throw die("Cannot create archive directory : " + b);
    }
  }
}
