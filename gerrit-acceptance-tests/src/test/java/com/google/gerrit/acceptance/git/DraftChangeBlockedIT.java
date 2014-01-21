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

package com.google.gerrit.acceptance.git;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static com.google.gerrit.server.project.Util.ANONYMOUS;
import static com.google.gerrit.server.project.Util.grant;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class DraftChangeBlockedIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  private TestAccount admin;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    grant(cfg, Permission.PUSH, ANONYMOUS,
        "refs/drafts/*").setBlock();
    saveProjectConfig(cfg);

    project = new Project.NameKey("p");
    admin = accounts.admin();
    initSsh(admin);
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());

    db = reviewDbProvider.open();
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
  }

  @Test
  public void testPushDraftChange_Blocked() throws GitAPIException,
      OrmException, IOException {
    // create draft by pushing to 'refs/drafts/'
    PushOneCommit.Result r = pushTo("refs/drafts/master");
    r.assertErrorStatus("cannot upload drafts");
  }

  @Test
  public void testPushDraftChangeMagic_Blocked() throws GitAPIException,
      OrmException, IOException {
    // create draft by using 'draft' option
    PushOneCommit.Result r = pushTo("refs/for/master%draft");
    r.assertErrorStatus("cannot upload drafts");
  }

  private PushOneCommit.Result pushTo(String ref) throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, ref);
  }

  private void saveProjectConfig(ProjectConfig cfg) throws IOException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    try {
      cfg.commit(md);
    } finally {
      md.close();
    }
  }
}
