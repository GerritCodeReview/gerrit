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

package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RepoViewTest extends GerritBaseTests {
  private static final String MASTER = "refs/heads/master";
  private static final String BRANCH = "refs/heads/branch";

  private Repository repo;
  private TestRepository<?> tr;
  private RepoView view;

  @Before
  public void setUp() throws Exception {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    Project.NameKey project = Project.nameKey("project");
    repo = repoManager.createRepository(project);
    tr = new TestRepository<>(repo);
    tr.branch(MASTER).commit().create();
    view = new RepoView(repoManager, project);
  }

  @After
  public void tearDown() {
    view.close();
    repo.close();
  }

  @Test
  public void getConfigIsDefensiveCopy() throws Exception {
    StoredConfig orig = repo.getConfig();
    orig.setString("a", "config", "option", "yes");
    orig.save();

    Config copy = view.getConfig();
    copy.setString("a", "config", "option", "no");

    assertThat(orig.getString("a", "config", "option")).isEqualTo("yes");
    assertThat(repo.getConfig().getString("a", "config", "option")).isEqualTo("yes");
  }

  @Test
  public void getRef() throws Exception {
    ObjectId oldMaster = repo.exactRef(MASTER).getObjectId();
    assertThat(repo.exactRef(MASTER).getObjectId()).isEqualTo(oldMaster);
    assertThat(repo.exactRef(BRANCH)).isNull();
    assertThat(view.getRef(MASTER)).hasValue(oldMaster);
    assertThat(view.getRef(BRANCH)).isEmpty();

    tr.branch(MASTER).commit().create();
    tr.branch(BRANCH).commit().create();
    assertThat(repo.exactRef(MASTER).getObjectId()).isNotEqualTo(oldMaster);
    assertThat(repo.exactRef(BRANCH)).isNotNull();
    assertThat(view.getRef(MASTER)).hasValue(oldMaster);
    assertThat(view.getRef(BRANCH)).isEmpty();
  }

  @Test
  public void getRefsRescansWhenNotCaching() throws Exception {
    ObjectId oldMaster = repo.exactRef(MASTER).getObjectId();
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", oldMaster);

    ObjectId newBranch = tr.branch(BRANCH).commit().create();
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", oldMaster, "branch", newBranch);
  }

  @Test
  public void getRefsUsesCachedValueMatchingGetRef() throws Exception {
    ObjectId master1 = repo.exactRef(MASTER).getObjectId();
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master1);
    assertThat(view.getRef(MASTER)).hasValue(master1);

    // Doesn't reflect new value for master.
    ObjectId master2 = tr.branch(MASTER).commit().create();
    assertThat(repo.exactRef(MASTER).getObjectId()).isEqualTo(master2);
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master1);

    // Branch wasn't previously cached, so does reflect new value.
    ObjectId branch1 = tr.branch(BRANCH).commit().create();
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master1, "branch", branch1);

    // Looking up branch causes it to be cached.
    assertThat(view.getRef(BRANCH)).hasValue(branch1);
    ObjectId branch2 = tr.branch(BRANCH).commit().create();
    assertThat(repo.exactRef(BRANCH).getObjectId()).isEqualTo(branch2);
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master1, "branch", branch1);
  }

  @Test
  public void getRefsReflectsCommands() throws Exception {
    ObjectId master1 = repo.exactRef(MASTER).getObjectId();
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master1);

    ObjectId master2 = tr.commit().create();
    view.getCommands().add(new ReceiveCommand(master1, master2, MASTER));

    assertThat(repo.exactRef(MASTER).getObjectId()).isEqualTo(master1);
    assertThat(view.getRef(MASTER)).hasValue(master2);
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master2);

    view.getCommands().add(new ReceiveCommand(master2, ObjectId.zeroId(), MASTER));

    assertThat(repo.exactRef(MASTER).getObjectId()).isEqualTo(master1);
    assertThat(view.getRef(MASTER)).isEmpty();
    assertThat(view.getRefs(R_HEADS)).isEmpty();
  }

  @Test
  public void getRefsOverwritesCachedValueWithCommand() throws Exception {
    ObjectId master1 = repo.exactRef(MASTER).getObjectId();
    assertThat(view.getRef(MASTER)).hasValue(master1);

    ObjectId master2 = tr.commit().create();
    view.getCommands().add(new ReceiveCommand(master1, master2, MASTER));

    assertThat(repo.exactRef(MASTER).getObjectId()).isEqualTo(master1);
    assertThat(view.getRef(MASTER)).hasValue(master2);
    assertThat(view.getRefs(R_HEADS)).containsExactly("master", master2);

    view.getCommands().add(new ReceiveCommand(master2, ObjectId.zeroId(), MASTER));

    assertThat(repo.exactRef(MASTER).getObjectId()).isEqualTo(master1);
    assertThat(view.getRef(MASTER)).isEmpty();
    assertThat(view.getRefs(R_HEADS)).isEmpty();
  }
}
