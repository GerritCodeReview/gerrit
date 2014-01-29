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

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.extensions.common.ListChangesOption.MESSAGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Iterator;

public class ChangeMessagesIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GerritApi gApi;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
    atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
        identifiedUserFactory.create(Providers.of(db), admin.getId())));
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void messagesNotReturnedByDefault() throws GitAPIException,
      IOException, RestApiException {
    String changeId = createChange();
    postMessage(changeId, "Some nits need to be fixed.");
    ChangeInfo c = getChange("p~master~" + changeId,
        EnumSet.noneOf(ListChangesOption.class));
    assertNull(c.messages);
  }

  @Test
  public void defaultMessage() throws GitAPIException, IOException,
      RestApiException {
    String changeId = createChange();
    ChangeInfo c = getChange("p~master~" + changeId, EnumSet.of(MESSAGES));
    assertNotNull(c.messages);
    assertEquals(1, c.messages.size());
    assertEquals("Uploaded patch set 1.", c.messages.iterator().next().message);
  }

  @Test
  public void messagesReturnedInChronologicalOrder() throws GitAPIException,
      IOException, RestApiException {
    String changeId = createChange();
    String firstMessage = "Some nits need to be fixed.";
    postMessage(changeId, firstMessage);
    String secondMessage = "I like this feature.";
    postMessage(changeId, secondMessage);
    ChangeInfo c = getChange("p~master~" + changeId, EnumSet.of(MESSAGES));
    assertNotNull(c.messages);
    assertEquals(3, c.messages.size());
    Iterator<ChangeMessageInfo> it = c.messages.iterator();
    assertEquals("Uploaded patch set 1.", it.next().message);
    assertMessage(firstMessage, it.next().message);
    assertMessage(secondMessage, it.next().message);
  }

  private String createChange() throws GitAPIException,
      IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/master").getChangeId();
  }

  private void assertMessage(String expected, String actual) {
    assertEquals("Patch Set 1:\n\n" + expected, actual);
  }

  private void postMessage(String changeId, String msg) throws IOException {
    ReviewInput in = new ReviewInput();
    in.message = msg;
    adminSession.post("/changes/" + changeId + "/revisions/1/review", in)
        .consume();
  }

  private ChangeInfo getChange(String triplet, EnumSet<ListChangesOption> s)
      throws RestApiException {
    return gApi.changes().id(triplet).get(s);
  }
}
