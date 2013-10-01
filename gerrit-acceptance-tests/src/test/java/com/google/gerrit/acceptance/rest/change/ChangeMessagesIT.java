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

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class ChangeMessagesIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;
  private RestSession session;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    session = new RestSession(server, admin);
    initSsh(admin);
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void messagesNotReturnedByDefault() throws GitAPIException,
      IOException {
    String changeId = createChange();
    postMessage(changeId, "Some nits need to be fixed.");
    ChangeInfo c = getChange(changeId);
    assertNull(c.messages);
  }

  @Test
  public void defaultMessage() throws GitAPIException,
  IOException {
    String changeId = createChange();
    ChangeInfo c = getChangeWithMessages(changeId);
    assertNotNull(c.messages);
    assertEquals(1, c.messages.size());
    assertEquals("Uploaded patch set 1.", c.messages.get(0).message);
  }

  @Test
  public void messagesReturnedInChronologicalOrder() throws GitAPIException,
      IOException {
    String changeId = createChange();
    String firstMessage = "Some nits need to be fixed.";
    postMessage(changeId, firstMessage);
    String secondMessage = "I like this feature.";
    postMessage(changeId, secondMessage);
    ChangeInfo c = getChangeWithMessages(changeId);
    assertNotNull(c.messages);
    assertEquals(3, c.messages.size());
    assertEquals("Uploaded patch set 1.", c.messages.get(0).message);
    assertMessage(firstMessage, c.messages.get(1).message);
    assertMessage(secondMessage, c.messages.get(2).message);
  }

  private String createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/for/master").getChangeId();
  }

  private ChangeInfo getChange(String changeId) throws IOException {
    return getChange(changeId, false);
  }

  private ChangeInfo getChangeWithMessages(String changeId) throws IOException {
    return getChange(changeId, true);
  }

  private ChangeInfo getChange(String changeId, boolean includeMessages)
      throws IOException {
    RestResponse r =
        session.get("/changes/?q=" + changeId
            + (includeMessages ? "&o=MESSAGES" : ""));
    List<ChangeInfo> c = (new Gson()).fromJson(r.getReader(),
        new TypeToken<List<ChangeInfo>>() {}.getType());
    return c.get(0);
  }

  private void assertMessage(String expected, String actual) {
    assertEquals("Patch Set 1:\n\n" + expected, actual);
  }

  private void postMessage(String changeId, String msg) throws IOException {
    ReviewInput in = new ReviewInput();
    in.message = msg;
    session.post("/changes/" + changeId + "/revisions/1/review", in).consume();
  }

  @SuppressWarnings("unused")
  private class ReviewInput {
    String message;
  }
}
