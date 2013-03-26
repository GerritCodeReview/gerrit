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

package com.google.gerrit.server.change;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.change.CommentInfo.Side;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

import junit.framework.TestCase;

import org.easymock.IAnswer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommentsTest extends TestCase {

  private Injector injector;
  private RevisionResource revRes1;
  private RevisionResource revRes2;
  private PatchLineComment plc1;
  private PatchLineComment plc2;
  private PatchLineComment plc3;

  @Override
  protected void setUp() throws Exception {
    @SuppressWarnings("unchecked")
    final DynamicMap<RestView<CommentResource>> views =
        createMock(DynamicMap.class);
    final TypeLiteral<DynamicMap<RestView<CommentResource>>> viewsType =
        new TypeLiteral<DynamicMap<RestView<CommentResource>>>() {};
    final AccountInfo.Loader.Factory alf =
        createMock(AccountInfo.Loader.Factory.class);
    final ReviewDb db = createMock(ReviewDb.class);

    AbstractModule mod = new AbstractModule() {
      @Override
      protected void configure() {
        bind(viewsType).toInstance(views);
        bind(AccountInfo.Loader.Factory.class).toInstance(alf);
        bind(ReviewDb.class).toInstance(db);
      }};

    Account.Id account1 = new Account.Id(1);
    Account.Id account2 = new Account.Id(2);
    AccountInfo.Loader accountLoader = createMock(AccountInfo.Loader.class);
    accountLoader.fill();
    expectLastCall().anyTimes();
    expect(accountLoader.get(account1))
        .andReturn(new AccountInfo(account1)).anyTimes();
    expect(accountLoader.get(account2))
        .andReturn(new AccountInfo(account2)).anyTimes();
    expect(alf.create(true)).andReturn(accountLoader).anyTimes();
    replay(accountLoader, alf);

    revRes1 = createMock(RevisionResource.class);
    revRes2 = createMock(RevisionResource.class);

    PatchLineCommentAccess plca = createMock(PatchLineCommentAccess.class);
    expect(db.patchComments()).andReturn(plca).anyTimes();

    Change.Id changeId = new Change.Id(123);
    PatchSet.Id psId1 = new PatchSet.Id(changeId, 1);
    PatchSet ps1 = new PatchSet(psId1);
    expect(revRes1.getPatchSet()).andReturn(ps1).anyTimes();
    PatchSet.Id psId2 = new PatchSet.Id(changeId, 2);
    PatchSet ps2 = new PatchSet(psId2);
    expect(revRes2.getPatchSet()).andReturn(ps2);

    long timeBase = System.currentTimeMillis();
    plc1 = newPatchLineComment(psId1, "Comment1", null,
        "FileOne.txt", Side.REVISION, 1, account1, timeBase,
        "First Comment");
    plc2 = newPatchLineComment(psId1, "Comment2", "Comment1",
        "FileOne.txt", Side.REVISION, 1, account2, timeBase + 1000,
        "Reply to First Comment");
    plc3 = newPatchLineComment(psId1, "Comment3", "Comment1",
        "FileOne.txt", Side.PARENT, 1, account1, timeBase + 2000,
        "First Parent Comment");

    expect(plca.publishedByPatchSet(psId1))
        .andAnswer(results(plc1, plc2, plc3)).anyTimes();
    expect(plca.publishedByPatchSet(psId2))
        .andAnswer(results()).anyTimes();

    replay(db, revRes1, revRes2, plca);
    injector = Guice.createInjector(mod);
  }

  public void testListComments() throws Exception {
    // test ListComments for patch set 1
    assertListComments(injector, revRes1, ImmutableMap.of(
        "FileOne.txt", Lists.newArrayList(plc3, plc1, plc2)));

    // test ListComments for patch set 2
    assertListComments(injector, revRes2,
        Collections.<String, ArrayList<PatchLineComment>>emptyMap());
  }

  public void testGetComment() throws Exception {
    // test GetComment for existing comment
    assertGetComment(injector, revRes1, plc1, plc1.getKey().get());

    // test GetComment for non-existent comment
    assertGetComment(injector, revRes1, null, "BadComment");
  }

  private static IAnswer<ResultSet<PatchLineComment>> results(
      final PatchLineComment... comments) {
    return new IAnswer<ResultSet<PatchLineComment>>() {
      @Override
      public ResultSet<PatchLineComment> answer() throws Throwable {
        return new ListResultSet<PatchLineComment>(Lists.newArrayList(comments));
      }};
  }

  private static void assertGetComment(Injector inj, RevisionResource res,
      PatchLineComment expected, String uuid) throws Exception {
    GetComment getComment = inj.getInstance(GetComment.class);
    Comments comments = inj.getInstance(Comments.class);
    try {
      CommentResource commentRes = comments.parse(res, IdString.fromUrl(uuid));
      if (expected == null) {
        fail("Expected no comment");
      }
      CommentInfo actual = (CommentInfo) getComment.apply(commentRes);
      assertComment(expected, actual);
    } catch (ResourceNotFoundException e) {
      if (expected != null) {
        fail("Expected to find comment");
      }
    }
  }

  private static void assertListComments(Injector inj, RevisionResource res,
      Map<String, ArrayList<PatchLineComment>> expected) throws Exception {
    Comments comments = inj.getInstance(Comments.class);
    RestReadView<RevisionResource> listView =
        (RestReadView<RevisionResource>) comments.list();
    @SuppressWarnings("unchecked")
    Map<String, List<CommentInfo>> actual =
        (Map<String, List<CommentInfo>>) listView.apply(res);
    assertNotNull(actual);
    assertEquals(expected.size(), actual.size());
    assertEquals(expected.keySet(), actual.keySet());
    for (String filename : expected.keySet()) {
      List<PatchLineComment> expectedComments = expected.get(filename);
      List<CommentInfo> actualComments = actual.get(filename);
      assertNotNull(actualComments);
      assertEquals(expectedComments.size(), actualComments.size());
      for (int i = 0; i < expectedComments.size(); i++) {
        assertComment(expectedComments.get(i), actualComments.get(i));
      }
    }
  }

  private static void assertComment(PatchLineComment plc, CommentInfo ci) {
    assertEquals(plc.getKey().get(), ci.id);
    assertEquals(plc.getParentUuid(), ci.inReplyTo);
    assertEquals("gerritcodereview#comment", ci.kind);
    assertEquals(plc.getMessage(), ci.message);
    assertNotNull(ci.author);
    assertEquals(plc.getAuthor(), ci.author._id);
    assertEquals(plc.getLine(), (int) ci.line);
    assertEquals(plc.getSide() == 0 ? Side.PARENT : Side.REVISION,
        Objects.firstNonNull(ci.side, Side.REVISION));
    assertEquals(plc.getWrittenOn(), ci.updated);
  }

  private static PatchLineComment newPatchLineComment(PatchSet.Id psId,
      String uuid, String inReplyToUuid, String filename, Side side, int line,
      Account.Id authorId, long millis, String message) {
    Patch.Key p = new Patch.Key(psId, filename);
    PatchLineComment.Key id = new PatchLineComment.Key(p, uuid);
    PatchLineComment plc =
        new PatchLineComment(id, line, authorId, inReplyToUuid);
    plc.setMessage(message);
    plc.setSide(side == CommentInfo.Side.PARENT ? (short) 0 : (short) 1);
    plc.setStatus(Status.PUBLISHED);
    plc.setWrittenOn(new Timestamp(millis));
    return plc;
  }
}
