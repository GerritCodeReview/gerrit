// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/** Creates the {@code All-Users} repository. */
public class AllUsersCreator {
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;

  @Inject
  AllUsersCreator(GitRepositoryManager mgr, AllUsersName allUsersName) {
    this.mgr = mgr;
    this.allUsersName = allUsersName;
  }

  public void create() throws IOException, ConfigInvalidException {
    Repository git = null;
    try {
      git = mgr.openRepository(allUsersName);
    } catch (RepositoryNotFoundException notFound) {
      try {
        git = mgr.createRepository(allUsersName);
      } catch (RepositoryNotFoundException err) {
        String name = allUsersName.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    } finally {
      if (git != null) {
        git.close();
      }
    }
  }
}
