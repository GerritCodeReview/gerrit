// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Workspace;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
@UseLocalDisk
public class WorkspaceIT extends AbstractDaemonTest {
  @Test
  public void e2eTest() throws Exception {
    RevCommit commitInMainline = createChange().getCommit();

    // Insert user commit
    ObjectId userCommit = null;
    Workspace.Id wsId = Workspace.id(user.id(), project, "workspace-1");
    try (Repository ws = repoManager.openWorkspace(wsId)) {
      assertThat(ws.getObjectDatabase().has(commitInMainline)).isTrue();

      CommitBuilder cb = new CommitBuilder();
      PersonIdent ident = serverIdent.get();
      cb.setAuthor(ident);
      cb.setParentId(ObjectId.zeroId());
      cb.setTreeId(ObjectId.zeroId());
      cb.setCommitter(ident);
      cb.setMessage("Workspace Update");
      ObjectInserter ins = ws.getObjectDatabase().newInserter();
      userCommit = ins.insert(cb);
      ins.flush();
      ins.close();

      assertThat(ws.getObjectDatabase().has(userCommit)).isTrue();
    }

    // Not present in main repo
    try (Repository mainline = repoManager.openRepository(project)) {
      assertThat(mainline.getObjectDatabase().has(commitInMainline)).isTrue();
      assertThat(mainline.getObjectDatabase().has(userCommit)).isFalse();
    }

    // Present in main repo
    try (Repository ws = repoManager.openWorkspace(wsId)) {
      assertThat(ws.getObjectDatabase().has(userCommit)).isTrue();
      // Upstream ref database is available
      assertThat(ws.getRefDatabase().exactRef("refs/origin/heads/master")).isNotNull();
    }
  }
}
