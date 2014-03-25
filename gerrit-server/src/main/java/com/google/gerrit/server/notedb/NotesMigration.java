// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

/**
 * Holds the current state of the notes DB migration.
 * <p>
 * During a transitional period, different subsets of the former gwtorm DB
 * functionality may be enabled on the site, possibly only for reading or
 * writing.
 */
@Singleton
public class NotesMigration {
  @VisibleForTesting
  static NotesMigration allEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("notedb", null, "write", true);
    cfg.setBoolean("notedb", "patchSetApprovals", "read", true);
    return new NotesMigration(cfg);
  }

  private final boolean write;
  private final boolean readPatchSetApprovals;

  @Inject
  NotesMigration(@GerritServerConfig Config cfg) {
    write = cfg.getBoolean("notedb", null, "write", false);
    readPatchSetApprovals =
        cfg.getBoolean("notedb", "patchSetApprovals", "read", false);
  }

  public boolean write() {
    return write;
  }

  public boolean readPatchSetApprovals() {
    return readPatchSetApprovals;
  }
}
