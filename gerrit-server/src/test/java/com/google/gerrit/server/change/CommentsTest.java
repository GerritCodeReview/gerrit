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

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.Scopes.SINGLETON;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.FakeRealm;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.GerritServerTests;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.TestChanges;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;

import org.easymock.IAnswer;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class CommentsTest extends GerritServerTests {
  private static final TimeZone TZ =
      TimeZone.getTimeZone("America/Los_Angeles");

  private Injector injector;
  private ReviewDb db;
  private Project.NameKey project;
  private Account.Id ownerId;
  private RevisionResource revRes1;
  private RevisionResource revRes2;
  private RevisionResource revRes3;
  private PatchLineComment plc1;
  private PatchLineComment plc2;
  private PatchLineComment plc3;
  private PatchLineComment plc4;
  private PatchLineComment plc5;
  private PatchLineComment plc6;
  private IdentifiedUser changeOwner;

  @Inject private AllUsersNameProvider allUsers;
  @Inject private Comments comments;
  @Inject private DraftComments drafts;
  @Inject private GetComment getComment;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private InMemoryRepositoryManager repoManager;
  @Inject private NotesMigration migration;
  @Inject private PatchLineCommentsUtil plcUtil;

  @Before
  public void setUp() throws Exception {
    @SuppressWarnings("unchecked")
    final DynamicMap<RestView<CommentResource>> commentViews =
        createMock(DynamicMap.class);
    final TypeLiteral<DynamicMap<RestView<CommentResource>>> commentViewsType =
        new TypeLiteral<DynamicMap<RestView<CommentResource>>>() {};
    @SuppressWarnings("unchecked")
    final DynamicMap<RestView<DraftCommentResource>> draftViews =
        createMock(DynamicMap.class);
    final TypeLiteral<DynamicMap<RestView<DraftCommentResource>>> draftViewsType =
        new TypeLiteral<DynamicMap<RestView<DraftCommentResource>>>() {};

    final AccountLoader.Factory alf =
        createMock(AccountLoader.Factory.class);
    db = createMock(ReviewDb.class);
    final FakeAccountCache accountCache = new FakeAccountCache();
    final PersonIdent serverIdent = new PersonIdent(
        "Gerrit Server", "noreply@gerrit.com", TimeUtil.nowTs(), TZ);
    project = new Project.NameKey("test-project");

    Account co = new Account(new Account.Id(1), TimeUtil.nowTs());
    co.setFullName("Change Owner");
    co.setPreferredEmail("change@owner.com");
    accountCache.put(co);
    ownerId = co.getId();

    Account ou = new Account(new Account.Id(2), TimeUtil.nowTs());
    ou.setFullName("Other Account");
    ou.setPreferredEmail("other@account.com");
    accountCache.put(ou);
    Account.Id otherUserId = ou.getId();

    AbstractModule mod = new AbstractModule() {
      @Override
      protected void configure() {
        bind(commentViewsType).toInstance(commentViews);
        bind(draftViewsType).toInstance(draftViews);
        bind(AccountLoader.Factory.class).toInstance(alf);
        bind(ReviewDb.class).toInstance(db);
        bind(Realm.class).to(FakeRealm.class);
        bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(config);
        bind(ProjectCache.class).toProvider(Providers.<ProjectCache> of(null));
        install(new GitModule());
        bind(GitRepositoryManager.class).to(InMemoryRepositoryManager.class);
        bind(InMemoryRepositoryManager.class)
            .toInstance(new InMemoryRepositoryManager());
        bind(CapabilityControl.Factory.class)
            .toProvider(Providers.<CapabilityControl.Factory> of(null));
        bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toProvider(AnonymousCowardNameProvider.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toInstance("http://localhost:8080/");
        bind(Boolean.class).annotatedWith(DisableReverseDnsLookup.class)
            .toInstance(Boolean.FALSE);
        bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
        bind(AccountCache.class).toInstance(accountCache);
        bind(GitReferenceUpdated.class)
            .toInstance(GitReferenceUpdated.DISABLED);
        bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class)
            .toInstance(serverIdent);
        bind(StarredChangesUtil.class)
            .toProvider(Providers.<StarredChangesUtil> of(null));
      }

      @Provides
      @Singleton
      CurrentUser getUser(IdentifiedUser.GenericFactory userFactory) {
        return userFactory.create(ownerId);
      }
    };

    injector = Guice.createInjector(mod);
    injector.injectMembers(this);

    repoManager.createRepository(project);
    changeOwner = userFactory.create(ownerId);
    IdentifiedUser otherUser = userFactory.create(otherUserId);

    AccountLoader accountLoader = createMock(AccountLoader.class);
    accountLoader.fill();
    expectLastCall().anyTimes();
    expect(accountLoader.get(ownerId))
        .andReturn(new AccountInfo(ownerId.get())).anyTimes();
    expect(accountLoader.get(otherUserId))
        .andReturn(new AccountInfo(otherUserId.get())).anyTimes();
    expect(alf.create(true)).andReturn(accountLoader).anyTimes();
    replay(accountLoader, alf);

    repoManager.createRepository(allUsers.get());

    PatchLineCommentAccess plca = createMock(PatchLineCommentAccess.class);
    expect(db.patchComments()).andReturn(plca).anyTimes();

    Change change1 = newChange();
    PatchSet.Id psId1 = new PatchSet.Id(change1.getId(), 1);
    PatchSet ps1 = new PatchSet(psId1);
    PatchSet.Id psId2 = new PatchSet.Id(change1.getId(), 2);
    PatchSet ps2 = new PatchSet(psId2);

    Change change2 = newChange();
    PatchSet.Id psId3 = new PatchSet.Id(change2.getId(), 1);
    PatchSet ps3 = new PatchSet(psId3);

    long timeBase = TimeUtil.roundToSecond(TimeUtil.nowTs()).getTime();
    plc1 = newPatchLineComment(psId1, "Comment1", null,
        "FileOne.txt", Side.REVISION, 3, ownerId, timeBase,
        "First Comment", new CommentRange(1, 2, 3, 4));
    plc1.setRevId(new RevId("abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd"));
    plc2 = newPatchLineComment(psId1, "Comment2", "Comment1",
        "FileOne.txt", Side.REVISION, 3, otherUserId, timeBase + 1000,
        "Reply to First Comment",  new CommentRange(1, 2, 3, 4));
    plc2.setRevId(new RevId("abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd"));
    plc3 = newPatchLineComment(psId1, "Comment3", "Comment1",
        "FileOne.txt", Side.PARENT, 3, ownerId, timeBase + 2000,
        "First Parent Comment",  new CommentRange(1, 2, 3, 4));
    plc3.setRevId(new RevId("cdefcdefcdefcdefcdefcdefcdefcdefcdefcdef"));
    plc4 = newPatchLineComment(psId2, "Comment4", null, "FileOne.txt",
        Side.REVISION, 3, ownerId, timeBase + 3000, "Second Comment",
        new CommentRange(1, 2, 3, 4), Status.DRAFT);
    plc4.setRevId(new RevId("bcdebcdebcdebcdebcdebcdebcdebcdebcdebcde"));
    plc5 = newPatchLineComment(psId2, "Comment5", null, "FileOne.txt",
        Side.REVISION, 5, ownerId, timeBase + 4000, "Third Comment",
        new CommentRange(3, 4, 5, 6), Status.DRAFT);
    plc5.setRevId(new RevId("bcdebcdebcdebcdebcdebcdebcdebcdebcdebcde"));
    plc6 = newPatchLineComment(psId3, "Comment6", null, "FileOne.txt",
        Side.REVISION, 5, ownerId, timeBase + 5000, "Sixth Comment",
        new CommentRange(3, 4, 5, 6), Status.DRAFT);
    plc6.setRevId(new RevId("1234123412341234123412341234123412341234"));

    List<PatchLineComment> commentsByOwner = Lists.newArrayList();
    commentsByOwner.add(plc1);
    commentsByOwner.add(plc3);
    List<PatchLineComment> commentsByReviewer = Lists.newArrayList();
    commentsByReviewer.add(plc2);
    List<PatchLineComment> drafts1 = Lists.newArrayList();
    drafts1.add(plc4);
    drafts1.add(plc5);
    List<PatchLineComment> drafts2 = Lists.newArrayList();
    drafts2.add(plc6);

    plca.upsert(commentsByOwner);
    expectLastCall().anyTimes();
    plca.upsert(commentsByReviewer);
    expectLastCall().anyTimes();
    plca.upsert(drafts1);
    expectLastCall().anyTimes();
    plca.upsert(drafts2);
    expectLastCall().anyTimes();

    expect(plca.publishedByPatchSet(psId1))
        .andAnswer(results(plc1, plc2, plc3)).anyTimes();
    expect(plca.publishedByPatchSet(psId2))
        .andAnswer(results()).anyTimes();
    expect(plca.draftByPatchSetAuthor(psId1, ownerId))
        .andAnswer(results()).anyTimes();
    expect(plca.draftByPatchSetAuthor(psId2, ownerId))
        .andAnswer(results(plc4, plc5)).anyTimes();
    expect(plca.byChange(change1.getId()))
        .andAnswer(results(plc1, plc2, plc3, plc4, plc5)).anyTimes();
    expect(plca.draftByAuthor(ownerId))
        .andAnswer(results(plc4, plc5, plc6)).anyTimes();
    replay(db, plca);

    ChangeUpdate update = newUpdate(change1, changeOwner);
    update.setPatchSetId(psId1);
    plcUtil.upsertComments(db, update, commentsByOwner);
    update.commit();

    update = newUpdate(change1, otherUser);
    update.setPatchSetId(psId1);
    plcUtil.upsertComments(db, update, commentsByReviewer);
    update.commit();

    update = newUpdate(change1, changeOwner);
    update.setPatchSetId(psId2);
    plcUtil.upsertComments(db, update, drafts1);
    update.commit();

    update = newUpdate(change2, changeOwner);
    update.setPatchSetId(psId3);
    plcUtil.upsertComments(db, update, drafts2);
    update.commit();

    ChangeControl ctl = stubChangeControl(change1);
    revRes1 = new RevisionResource(new ChangeResource(ctl), ps1);
    revRes2 = new RevisionResource(new ChangeResource(ctl), ps2);
    revRes3 = new RevisionResource(new ChangeResource(stubChangeControl(change2)), ps3);
  }

  private ChangeControl stubChangeControl(Change c) throws OrmException {
    return TestChanges.stubChangeControl(
        repoManager, migration, c, allUsers, changeOwner);
  }

  private Change newChange() {
    return TestChanges.newChange(project, changeOwner.getAccountId());
  }

  private ChangeUpdate newUpdate(Change c, final IdentifiedUser user) throws Exception {
    return TestChanges.newUpdate(
        injector, repoManager, migration, c, allUsers, user);
  }

  @Test
  public void testListComments() throws Exception {
    // test ListComments for patch set 1
    assertListComments(revRes1, ImmutableMap.of(
        "FileOne.txt", Lists.newArrayList(plc3, plc1, plc2)));

    // test ListComments for patch set 2
    assertListComments(revRes2,
        Collections.<String, List<PatchLineComment>>emptyMap());
  }

  @Test
  public void testGetCommentExisting() throws Exception {
    // test GetComment for existing comment
    String uuid = plc1.getKey().get();
    CommentResource commentRes = comments.parse(revRes1, IdString.fromUrl(uuid));
    CommentInfo actual = getComment.apply(commentRes);
    assertComment(plc1, actual, true);
  }

  @Test
  public void testGetCommentNotExisting() throws Exception {
    // test GetComment for non-existent comment
    exception.expect(ResourceNotFoundException.class);
    comments.parse(revRes1, IdString.fromUrl("BadComment"));
  }

  @Test
  public void testListDrafts() throws Exception {
    // test ListDrafts for patch set 1
    assertListDrafts(revRes1,
        Collections.<String, List<PatchLineComment>> emptyMap());

    // test ListDrafts for patch set 2
    assertListDrafts(revRes2, ImmutableMap.of(
        "FileOne.txt", Lists.newArrayList(plc4, plc5)));
  }

  @Test
  public void testPatchLineCommentsUtilByCommentStatus() throws OrmException {
    assertThat(plcUtil.publishedByChange(db, revRes2.getNotes()))
        .containsExactly(plc3, plc1, plc2).inOrder();
    assertThat(plcUtil.draftByChange(db, revRes2.getNotes()))
        .containsExactly(plc4, plc5).inOrder();
  }

  @Test
  public void testPatchLineCommentsUtilDraftByChangeAuthor() throws Exception {
    assertThat(plcUtil.draftByChangeAuthor(db, revRes1.getNotes(), ownerId))
        .containsExactly(plc4, plc5).inOrder();
    assertThat(plcUtil.draftByChangeAuthor(db, revRes3.getNotes(), ownerId))
        .containsExactly(plc6);
  }

  private static IAnswer<ResultSet<PatchLineComment>> results(
      final PatchLineComment... comments) {
    return new IAnswer<ResultSet<PatchLineComment>>() {
      @Override
      public ResultSet<PatchLineComment> answer() throws Throwable {
        return new ListResultSet<>(Lists.newArrayList(comments));
      }
    };
  }

  private void assertListComments(RevisionResource res,
      Map<String, ? extends List<PatchLineComment>> expected) throws Exception {
    assertCommentMap(comments.list().apply(res), expected, true);
  }

  private void assertListDrafts(RevisionResource res,
      Map<String, ? extends List<PatchLineComment>> expected) throws Exception {
    assertCommentMap(drafts.list().apply(res), expected, false);
  }

  private void assertCommentMap(Map<String, List<CommentInfo>> actual,
      Map<String, ? extends List<PatchLineComment>> expected,
      boolean isPublished) {
    assertThat(actual.keySet()).containsExactlyElementsIn(expected.keySet());
    for (Map.Entry<String, List<CommentInfo>> entry : actual.entrySet()) {
      List<CommentInfo> actualList = entry.getValue();
      List<PatchLineComment> expectedList = expected.get(entry.getKey());
      assertThat(actualList).hasSize(expectedList.size());
      for (int i = 0; i < expectedList.size(); i++) {
        assertComment(expectedList.get(i), actualList.get(i), isPublished);
      }
    }
  }

  private static void assertComment(PatchLineComment plc, CommentInfo ci,
      boolean isPublished) {
    assertThat(ci.id).isEqualTo(plc.getKey().get());
    assertThat(ci.inReplyTo).isEqualTo(plc.getParentUuid());
    assertThat(ci.message).isEqualTo(plc.getMessage());
    if (isPublished) {
      assertThat(ci.author).isNotNull();
      assertThat(new Account.Id(ci.author._accountId))
          .isEqualTo(plc.getAuthor());
    }
    assertThat(ci.line).isEqualTo(plc.getLine());
    assertThat(MoreObjects.firstNonNull(ci.side, Side.REVISION))
        .isEqualTo(plc.getSide() == 0 ? Side.PARENT : Side.REVISION);
    assertThat(TimeUtil.roundToSecond(ci.updated))
        .isEqualTo(TimeUtil.roundToSecond(plc.getWrittenOn()));
    assertThat(ci.updated).isEqualTo(plc.getWrittenOn());
    assertThat(ci.range.startLine).isEqualTo(plc.getRange().getStartLine());
    assertThat(ci.range.startCharacter)
        .isEqualTo(plc.getRange().getStartCharacter());
    assertThat(ci.range.endLine).isEqualTo(plc.getRange().getEndLine());
    assertThat(ci.range.endCharacter)
        .isEqualTo(plc.getRange().getEndCharacter());
  }

  private static PatchLineComment newPatchLineComment(PatchSet.Id psId,
      String uuid, String inReplyToUuid, String filename, Side side, int line,
      Account.Id authorId, long millis, String message, CommentRange range,
      PatchLineComment.Status status) {
    Patch.Key p = new Patch.Key(psId, filename);
    PatchLineComment.Key id = new PatchLineComment.Key(p, uuid);
    PatchLineComment plc =
        new PatchLineComment(id, line, authorId, inReplyToUuid, TimeUtil.nowTs());
    plc.setMessage(message);
    plc.setRange(range);
    plc.setSide(side == Side.PARENT ? (short) 0 : (short) 1);
    plc.setStatus(status);
    plc.setWrittenOn(new Timestamp(millis));
    return plc;
  }

  private static PatchLineComment newPatchLineComment(PatchSet.Id psId,
      String uuid, String inReplyToUuid, String filename, Side side, int line,
      Account.Id authorId, long millis, String message, CommentRange range) {
    return newPatchLineComment(psId, uuid, inReplyToUuid, filename, side, line,
        authorId, millis, message, range, Status.PUBLISHED);
  }
}
