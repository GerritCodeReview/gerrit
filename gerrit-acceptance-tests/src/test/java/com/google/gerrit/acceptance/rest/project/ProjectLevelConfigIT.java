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

package com.google.gerrit.acceptance.rest.project;

import static com.google.gerrit.acceptance.git.GitUtil.checkout;
import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.fetch;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ProjectLevelConfigIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private ProjectCache projectCache;

  private ReviewDb db;
  private TestAccount admin;
  private String project;
  private Git git;

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    initSsh(admin);
    SshSession sshSession = new SshSession(server, admin);

    project = "p";
    createProject(sshSession, project, null, true);
    git = cloneProject(sshSession.getUrl() + "/" + project);
    fetch(git, "refs/meta/config:refs/heads/config");
    checkout(git, "refs/heads/config");

    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void accessProjectSpecificConfig() throws GitAPIException, IOException {
    String configName = "test.config";
    Config cfg = new Config();
    cfg.setString("s1", null, "k1", "v1");
    cfg.setString("s2", "ss", "k2", "v2");
    PushOneCommit push =
        new PushOneCommit(db, admin.getIdent(), "Create Project Level Config",
            configName, cfg.toText());
    push.to(git, GitRepositoryManager.REF_CONFIG);

    ProjectState state = projectCache.get(new Project.NameKey(project));
    assertEquals(cfg.toText(), state.getConfig(configName).get().toText());
  }

  @Test
  public void nonExistingConfig() throws GitAPIException, IOException {
    ProjectState state = projectCache.get(new Project.NameKey(project));
    assertEquals("", state.getConfig("test.config").get().toText());
  }
}
