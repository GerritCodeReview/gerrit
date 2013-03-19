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

package com.google.gerrit.acceptance;

import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

public class GcAssert {

  private final GitRepositoryManager repoManager;

  @Inject
  public GcAssert(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  public void assertHasPackFile(Project.NameKey... projects)
      throws RepositoryNotFoundException, IOException {
    for (Project.NameKey p : projects) {
      assertTrue("Project " + p.get() + "has no pack files.",
          getPackFiles(p).length > 0);
    }
  }

  public void assertHasNoPackFile(Project.NameKey... projects)
      throws RepositoryNotFoundException, IOException {
    for (Project.NameKey p : projects) {
      assertTrue("Project " + p.get() + "has pack files.",
          getPackFiles(p).length == 0);
    }
  }

  private String[] getPackFiles(Project.NameKey p)
      throws RepositoryNotFoundException, IOException {
    Repository repo = repoManager.openRepository(p);
    try {
      File packDir = new File(repo.getDirectory(), "objects/pack");
      return packDir.list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".pack");
        }
      });
    } finally {
      repo.close();
    }
  }
}
