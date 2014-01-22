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

import static com.google.gerrit.acceptance.GitUtil.checkout;
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class ConflictsOperatorIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private Project.NameKey project;
  private ReviewDb db;
  private int count;

  @Before
  public void setUp() throws Exception {
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
        pushFactory.create(db, admin.getIdent(), "Change " + count, file,
            "content " + count);
    count++;
    return push.to(git, "refs/for/master");
  }

  private Set<String> queryConflictingChanges(PushOneCommit.Result change)
      throws IOException {
    RestResponse r =
        adminSession.get("/changes/?q=conflicts:" + change.getChangeId());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Set<ChangeInfo> changes =
        newGson().fromJson(r.getReader(),
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
