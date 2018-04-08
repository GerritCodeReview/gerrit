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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.Rule;
import org.junit.Test;

public class Schema_161_to_162_Test {
  @Rule public InMemoryTestEnvironment testEnv = new InMemoryTestEnvironment();

  @Inject private AllProjectsName allProjectsName;
  @Inject private AllUsersName allUsersName;
  @Inject private GerritApi gApi;
  @Inject private GitRepositoryManager repoManager;
  @Inject private Schema_162 schema162;
  @Inject private ReviewDb db;
  @Inject @GerritPersonIdent private PersonIdent serverUser;

  @Test
  public void skipCorrectInheritance() throws Exception {
    assertThatAllUsersInheritsFrom(allProjectsName.get());
    ObjectId oldHead;
    try (Repository git = repoManager.openRepository(allUsersName)) {
      oldHead = git.findRef(RefNames.REFS_CONFIG).getObjectId();
    }

    schema162.migrateData(db, new TestUpdateUI());

    // Check that the parent remained unchanged and that no commit was made
    assertThatAllUsersInheritsFrom(allProjectsName.get());
    try (Repository git = repoManager.openRepository(allUsersName)) {
      assertThat(oldHead).isEqualTo(git.findRef(RefNames.REFS_CONFIG).getObjectId());
    }
  }

  @Test
  public void fixIncorrectInheritance() throws Exception {
    String testProject = gApi.projects().create("test").get().name;
    assertThatAllUsersInheritsFrom(allProjectsName.get());

    try (Repository git = repoManager.openRepository(allUsersName);
        MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git)) {
      ProjectConfig cfg = ProjectConfig.read(md);
      cfg.getProject().setParentName(testProject);
      md.getCommitBuilder().setCommitter(serverUser);
      md.getCommitBuilder().setAuthor(serverUser);
      md.setMessage("Test");
      cfg.commit(md);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
    assertThatAllUsersInheritsFrom(testProject);

    schema162.migrateData(db, new TestUpdateUI());

    assertThatAllUsersInheritsFrom(allProjectsName.get());
  }

  private void assertThatAllUsersInheritsFrom(String parent) throws Exception {
    assertThat(gApi.projects().name(allUsersName.get()).access().inheritsFrom.name)
        .isEqualTo(parent);
  }
}
