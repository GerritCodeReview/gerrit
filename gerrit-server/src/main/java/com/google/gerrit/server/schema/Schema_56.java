// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Schema_56 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final Set<String> keysOne;
  private final Set<String> keysTwo;

  @Inject
  Schema_56(Provider<Schema_55> prior, GitRepositoryManager mgr) {
    super(prior);
    this.mgr = mgr;

    keysOne = new HashSet<String>();
    keysTwo = new HashSet<String>();

    keysOne.add(GitRepositoryManager.REF_CONFIG);
    keysTwo.add(GitRepositoryManager.REF_CONFIG);
    keysTwo.add(Constants.HEAD);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) {
    for (Project.NameKey name : mgr.list()) {
      Repository git;
      try {
        git = mgr.openRepository(name);
      } catch (IOException e) {
        ui.message("warning: Cannot open " + name.get());
        continue;
      }
      try {
        Map<String, Ref> all;
        try {
          all = git.getRefDatabase().getRefs(RefDatabase.ALL);
        } catch (IOException e) {
          ui.message("warning: " + name.get() + ": Cannot read refs: "
              + e.getMessage());
          e.printStackTrace();
          continue;
        }
        if (all.keySet().equals(keysOne) || all.keySet().equals(keysTwo)) {
          try {
            RefUpdate update = git.updateRef(Constants.HEAD);
            update.disableRefLog();
            update.link(GitRepositoryManager.REF_CONFIG);
          } catch (IOException err) {
            ui.message("warning: " + name.get() + ": Cannot update HEAD to "
                + GitRepositoryManager.REF_CONFIG + ": " + err.getMessage());
          }
        }
      } finally {
        git.close();
      }
    }
  }
}
