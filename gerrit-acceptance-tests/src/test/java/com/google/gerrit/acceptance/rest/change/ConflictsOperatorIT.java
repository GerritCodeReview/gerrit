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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.git.GitUtil.checkout;
import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTestWithSecondaryIndex;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.GitUtil;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

public class ConflictsOperatorIT extends AbstractDaemonTestWithSecondaryIndex {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  @Inject
  private AllProjectsNameProvider allProjects;

  private TestAccount admin;
  private RestSession session;
  private Project.NameKey project;
  private ReviewDb db;
  private int count;

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    session = new RestSession(server, admin);
    initSsh(admin);

    project = new Project.NameKey("p");

    db = reviewDbProvider.open();
  }

  @Test
  public void noConflictingChanges() throws JSchException, IOException,
      GitAPIException {
    Git git = createProject();
    PushOneCommit.Result change = createChange(git, true);
    createChange(git, false);

    Set<String> changes = queryConflictingChanges(change);
    assertEquals(0, changes.size());
  }

  @Test
  public void conflictingChanges() throws JSchException, IOException,
      GitAPIException {
    Git git = createProject();
    PushOneCommit.Result change = createChange(git, true);
    PushOneCommit.Result conflictingChange1 = createChange(git, true);
    PushOneCommit.Result conflictingChange2 = createChange(git, true);
    createChange(git, false);

    Set<String> changes = queryConflictingChanges(change);
    assertChanges(changes, conflictingChange1, conflictingChange2);
  }

  @Test
  public void conflictingChanges100() throws JSchException, IOException,
      GitAPIException {
    manyConflictingChanges(100);
  }

  @Test
  public void conflictingChanges1000() throws JSchException, IOException,
      GitAPIException, ConfigInvalidException {
    setMaxQueryLimit(2000);
    manyConflictingChanges(1000);
  }

  private void manyConflictingChanges(int count) throws JSchException, IOException,
      GitAPIException {
    Git git = createProject();
    PushOneCommit.Result change = createChange(git, true);
    int c = 0;
    for (int i = 0; i < count; i++) {
      createChange(git, true);
      if (c >= 100) {
        System.out.println();
        c = 0;
      }
      System.out.print(".");
      c++;
    }
    createChange(git, false);

    long start = (new Date()).getTime();
    Set<String> changes = queryConflictingChanges(change);
    long end = (new Date()).getTime();
    System.out.println();
    System.out.println("Querying " + count + " conflicting changes took " + (end - start)/1000 + "s");
    assertEquals(count, changes.size());
  }

  private void setMaxQueryLimit(int limit)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects.get());
    md.setMessage(String.format("Grant %s", GlobalCapability.QUERY_LIMIT));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
    Permission p = s.getPermission(GlobalCapability.QUERY_LIMIT, true);
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    PermissionRule rule = new PermissionRule(config.resolve(adminGroup));
    rule.setMax(limit);
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private Git createProject() throws JSchException, IOException,
      GitAPIException {
    SshSession sshSession = new SshSession(server, admin);
    try {
      GitUtil.createProject(sshSession, project.get(), null, true);
      return cloneProject(sshSession.getUrl() + "/" + project.get());
    } finally {
      sshSession.close();
    }
  }

  private PushOneCommit.Result createChange(Git git, boolean conflicting)
      throws GitAPIException, IOException {
    checkout(git, "origin/master");
    String file = conflicting ? "test.txt" : "test-" + count + ".txt";
    PushOneCommit push =
        new PushOneCommit(db, admin.getIdent(), "Change " + count, file,
            "content " + count);
    count++;
    return push.to(git, "refs/for/master");
  }

  private Set<String> queryConflictingChanges(PushOneCommit.Result change)
      throws IOException {
    RestResponse r =
        session.get("/changes/?q=conflicts:" + change.getChangeId());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Set<ChangeInfo> changes =
        (new Gson()).fromJson(r.getReader(),
            new TypeToken<Set<ChangeInfo>>() {}.getType());
    r.consume();
    return ImmutableSet.copyOf(Iterables.transform(changes,
        new Function<ChangeInfo, String>() {
          @Override
          public String apply(ChangeInfo input) {
            return input.id;
          }
        }));
  }

  private void assertChanges(Set<String> actualChanges,
      PushOneCommit.Result... expectedChanges) {
    assertEquals(expectedChanges.length, actualChanges.size());
    for (PushOneCommit.Result c : expectedChanges) {
      assertTrue(actualChanges.contains(id(c)));
    }
  }

  private String id(PushOneCommit.Result change) {
    return project.get() + "~master~" + change.getChangeId();
  }
}
