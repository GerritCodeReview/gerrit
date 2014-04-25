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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/* Revsion edit reader command */
public class RevisionEditReader {

  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;

  @Inject
  public RevisionEditReader(GitRepositoryManager gitManager,
      Provider<CurrentUser> currentUser) {
    this.gitManager = gitManager;
    this.currentUser = currentUser;
  }

  public Map<PatchSet.Id, PatchSet> read(Change change)
      throws AuthException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    Repository repo = gitManager.openRepository(change.getProject());
    try {
      IdentifiedUser me = (IdentifiedUser)currentUser.get();
      Map<String, Ref> names =
          RevisionEdit.getChangeEditsRefs(repo, me, change.getId());
      Map<PatchSet.Id, PatchSet> result = new HashMap<>(names.size());
      for (Map.Entry<String, Ref> entry : names.entrySet()) {
        PatchSet.Id psid = new PatchSet.Id(change.getId(),
            Integer.valueOf(entry.getKey()));
        RevisionEdit edit = new RevisionEdit(me, psid, entry.getValue());
        result.put(psid, edit.getPatchSet(repo));
      }
      return Collections.unmodifiableMap(result);
    } finally {
      repo.close();
    }
  }
}
