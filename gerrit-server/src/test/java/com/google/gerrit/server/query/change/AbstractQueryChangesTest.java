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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.fail;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Ignore
@RunWith(ConfigSuite.class)
public abstract class AbstractQueryChangesTest {
  private static final TopLevelResource TLR = TopLevelResource.INSTANCE;

  @ConfigSuite.Config
  public static Config noteDbEnabled() {
    return NotesMigration.allEnabledConfig();
  }

  @ConfigSuite.Parameter public Config config;
  @Inject protected AccountManager accountManager;
  @Inject protected ChangeInserter.Factory changeFactory;
  @Inject protected ChangesCollection changes;
  @Inject protected CreateProject.Factory projectFactory;
  @Inject protected GerritApi gApi;
  @Inject protected IdentifiedUser.RequestFactory userFactory;
  @Inject protected InMemoryDatabase schemaFactory;
  @Inject protected InMemoryRepositoryManager repoManager;
  @Inject protected NotesMigration notesMigration;
  @Inject protected PostReview postReview;
  @Inject protected ProjectControl.GenericFactory projectControlFactory;
  @Inject protected Provider<QueryChanges> queryProvider;
  @Inject protected SchemaCreator schemaCreator;
  @Inject protected ThreadLocalRequestContext requestContext;
  @Inject protected GroupCache groupCache;
  @Inject protected PerformCreateGroup.Factory performCreateGroupFactory;

  protected LifecycleManager lifecycle;
  protected ReviewDb db;
  protected Account.Id userId;
  protected CurrentUser user;
  protected volatile long clockStepMs;

  private String systemTimeZone;

  protected abstract Injector createInjector();

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = createInjector();
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    userId = accountManager.authenticate(AuthRequest.forUser("user"))
        .getAccountId();
    Account userAccount = db.accounts().get(userId);
    userAccount.setPreferredEmail("user@example.com");
    db.accounts().update(ImmutableList.of(userAccount));
    user = userFactory.create(userId);
    requestContext.setContext(newRequestContext(userAccount.getId()));
  }

  private RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser = userFactory.create(requestUserId);
    return new RequestContext() {
      @Override
      public CurrentUser getCurrentUser() {
        return requestUser;
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    };
  }

  @After
  public void tearDownInjector() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    clockStepMs = 1;
    final AtomicLong clockMs = new AtomicLong(
        new DateTime(2009, 9, 30, 17, 0, 0).getMillis());

    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @After
  public void resetTime() {
    DateTimeUtils.setCurrentMillisSystem();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void byId() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertThat(query("12345")).isEmpty();
    assertResultEquals(change1, queryOne(change1.getId().get()));
    assertResultEquals(change2, queryOne(change2.getId().get()));
  }

  @Test
  public void byKey() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change = newChange(repo, null, null, null, null).insert();
    String key = change.getKey().get();

    assertThat(query("I0000000000000000000000000000000000000000")).isEmpty();
    for (int i = 0; i <= 36; i++) {
      String q = key.substring(0, 41 - i);
      assertResultEquals("result for " + q, change, queryOne(q));
    }
  }

  @Test
  public void byTriplet() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change = newChange(repo, null, null, null, "branch").insert();
    String k = change.getKey().get();

    assertResultEquals(change, queryOne("repo~branch~" + k));
    assertResultEquals(change, queryOne("change:repo~branch~" + k));
    assertResultEquals(change, queryOne("repo~refs/heads/branch~" + k));
    assertResultEquals(change, queryOne("change:repo~refs/heads/branch~" + k));
    assertResultEquals(change, queryOne("repo~branch~" + k.substring(0, 10)));
    assertResultEquals(change,
        queryOne("change:repo~branch~" + k.substring(0, 10)));

    assertThat(query("foo~bar")).isEmpty();
    assertBadQuery("change:foo~bar");
    assertThat(query("otherrepo~branch~" + k)).isEmpty();
    assertThat(query("change:otherrepo~branch~" + k)).isEmpty();
    assertThat(query("repo~otherbranch~" + k)).isEmpty();
    assertThat(query("change:repo~otherbranch~" + k)).isEmpty();
    assertThat(query("repo~branch~I0000000000000000000000000000000000000000"))
        .isEmpty();
    assertThat(query(
          "change:repo~branch~I0000000000000000000000000000000000000000"))
        .isEmpty();
  }

  @Test
  public void byStatus() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.getChange();
    change1.setStatus(Change.Status.NEW);
    ins1.insert();
    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    Change change2 = ins2.getChange();
    change2.setStatus(Change.Status.MERGED);
    ins2.insert();

    assertResultEquals(change1, queryOne("status:new"));
    assertResultEquals(change1, queryOne("status:NEW"));
    assertResultEquals(change1, queryOne("is:new"));
    assertResultEquals(change2, queryOne("status:merged"));
    assertResultEquals(change2, queryOne("is:merged"));
  }

  @Test
  public void byStatusOpen() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.getChange();
    change1.setStatus(Change.Status.NEW);
    ins1.insert();
    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    Change change2 = ins2.getChange();
    change2.setStatus(Change.Status.DRAFT);
    ins2.insert();
    ChangeInserter ins3 = newChange(repo, null, null, null, null);
    Change change3 = ins3.getChange();
    change3.setStatus(Change.Status.MERGED);
    ins3.insert();

    List<ChangeInfo> results;
    results = query("status:open");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    assertThat(query("status:OPEN")).hasSize(2);
    assertThat(query("status:o")).hasSize(2);
    assertThat(query("status:op")).hasSize(2);
    assertThat(query("status:ope")).hasSize(2);
    assertThat(query("status:pending")).hasSize(2);
    assertThat(query("status:PENDING")).hasSize(2);
    assertThat(query("status:p")).hasSize(2);
    assertThat(query("status:pe")).hasSize(2);
    assertThat(query("status:pen")).hasSize(2);

    results = query("is:open");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byStatusClosed() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.getChange();
    change1.setStatus(Change.Status.MERGED);
    ins1.insert();
    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    Change change2 = ins2.getChange();
    change2.setStatus(Change.Status.ABANDONED);
    ins2.insert();
    ChangeInserter ins3 = newChange(repo, null, null, null, null);
    Change change3 = ins3.getChange();
    change3.setStatus(Change.Status.NEW);
    ins3.insert();

    List<ChangeInfo> results;
    results = query("status:closed");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    assertThat(query("status:CLOSED")).hasSize(2);
    assertThat(query("status:c")).hasSize(2);
    assertThat(query("status:cl")).hasSize(2);
    assertThat(query("status:clo")).hasSize(2);
    assertThat(query("status:clos")).hasSize(2);
    assertThat(query("status:close")).hasSize(2);
    assertThat(query("status:closed")).hasSize(2);

    results = query("is:closed");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byStatusPrefix() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.getChange();
    change1.setStatus(Change.Status.NEW);
    ins1.insert();
    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    Change change2 = ins2.getChange();
    change2.setStatus(Change.Status.MERGED);
    ins2.insert();

    assertResultEquals(change1, queryOne("status:n"));
    assertResultEquals(change1, queryOne("status:ne"));
    assertResultEquals(change1, queryOne("status:new"));
    assertResultEquals(change1, queryOne("status:N"));
    assertResultEquals(change1, queryOne("status:nE"));
    assertResultEquals(change1, queryOne("status:neW"));
    assertBadQuery("status:nx");
    assertBadQuery("status:newx");
  }

  @Test
  public void byCommit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    ins.insert();
    String sha = ins.getPatchSet().getRevision().get();

    assertThat(query("0000000000000000000000000000000000000000")).isEmpty();
    for (int i = 0; i <= 36; i++) {
      String q = sha.substring(0, 40 - i);
      assertResultEquals("result for " + q, ins.getChange(), queryOne(q));
    }
  }

  @Test
  public void byOwner() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, userId.get(), null).insert();
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    Change change2 = newChange(repo, null, null, user2, null).insert();

    assertResultEquals(change1, queryOne("owner:" + userId.get()));
    assertResultEquals(change2, queryOne("owner:" + user2));
  }

  @Test
  public void byOwnerIn() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, userId.get(), null).insert();
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    Change change2 = newChange(repo, null, null, user2, null).insert();

    assertResultEquals(change1, queryOne("ownerin:Administrators"));
    List<ChangeInfo> results = query("ownerin:\"Registered Users\"");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byProject() throws Exception {
    TestRepository<InMemoryRepository> repo1 = createProject("repo1");
    TestRepository<InMemoryRepository> repo2 = createProject("repo2");
    Change change1 = newChange(repo1, null, null, null, null).insert();
    Change change2 = newChange(repo2, null, null, null, null).insert();

    assertThat(query("project:foo")).isEmpty();
    assertThat(query("project:repo")).isEmpty();
    assertResultEquals(change1, queryOne("project:repo1"));
    assertResultEquals(change2, queryOne("project:repo2"));
  }

  @Test
  public void byProjectPrefix() throws Exception {
    TestRepository<InMemoryRepository> repo1 = createProject("repo1");
    TestRepository<InMemoryRepository> repo2 = createProject("repo2");
    Change change1 = newChange(repo1, null, null, null, null).insert();
    Change change2 = newChange(repo2, null, null, null, null).insert();

    assertThat(query("projects:foo")).isEmpty();
    assertResultEquals(change1, queryOne("projects:repo1"));
    assertResultEquals(change2, queryOne("projects:repo2"));

    List<ChangeInfo> results;
    results = query("projects:repo");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byBranchAndRef() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, "master").insert();
    Change change2 = newChange(repo, null, null, null, "branch").insert();

    assertThat(query("branch:foo")).isEmpty();
    assertResultEquals(change1, queryOne("branch:master"));
    assertResultEquals(change1, queryOne("branch:refs/heads/master"));
    assertThat(query("ref:master")).isEmpty();
    assertResultEquals(change1, queryOne("ref:refs/heads/master"));
    assertResultEquals(change1, queryOne("branch:refs/heads/master"));
    assertResultEquals(change2, queryOne("branch:branch"));
    assertResultEquals(change2, queryOne("branch:refs/heads/branch"));
    assertThat(query("ref:branch")).isEmpty();
    assertResultEquals(change2, queryOne("ref:refs/heads/branch"));
  }

  @Test
  public void byTopic() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.getChange();
    change1.setTopic("feature1");
    ins1.insert();

    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    Change change2 = ins2.getChange();
    change2.setTopic("feature2");
    ins2.insert();

    Change change3 = newChange(repo, null, null, null, null).insert();

    assertThat(query("topic:foo")).isEmpty();
    assertResultEquals(change1, queryOne("topic:feature1"));
    assertResultEquals(change2, queryOne("topic:feature2"));
    assertResultEquals(change3, queryOne("topic:\"\""));
  }

  @Test
  public void byMessageExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("one").create());
    Change change1 = newChange(repo, commit1, null, null, null).insert();
    RevCommit commit2 = repo.parseBody(repo.commit().message("two").create());
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    assertThat(query("message:foo")).isEmpty();
    assertResultEquals(change1, queryOne("message:one"));
    assertResultEquals(change2, queryOne("message:two"));
  }

  @Test
  public void fullTextWithNumbers() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit1 =
        repo.parseBody(repo.commit().message("12345 67890").create());
    Change change1 = newChange(repo, commit1, null, null, null).insert();
    RevCommit commit2 =
        repo.parseBody(repo.commit().message("12346 67891").create());
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    assertThat(query("message:1234")).isEmpty();
    assertResultEquals(change1, queryOne("message:12345"));
    assertResultEquals(change2, queryOne("message:12346"));
  }

  @Test
  public void byLabel() throws Exception {
    accountManager.authenticate(AuthRequest.forUser("anotheruser"));
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    Change change = ins.insert();

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    input.labels = ImmutableMap.<String, Short> of("Code-Review", (short) 1);
    postReview.apply(new RevisionResource(
        changes.parse(change.getId()), ins.getPatchSet()), input);

    assertThat(query("label:Code-Review=-2")).isEmpty();
    assertThat(query("label:Code-Review-2")).isEmpty();
    assertThat(query("label:Code-Review=-1")).isEmpty();
    assertThat(query("label:Code-Review-1")).isEmpty();
    assertThat(query("label:Code-Review=0")).isEmpty();
    assertResultEquals(change, queryOne("label:Code-Review=+1"));
    assertResultEquals(change, queryOne("label:Code-Review=1"));
    assertResultEquals(change, queryOne("label:Code-Review+1"));
    assertThat(query("label:Code-Review=+2")).isEmpty();
    assertThat(query("label:Code-Review=2")).isEmpty();
    assertThat(query("label:Code-Review+2")).isEmpty();

    assertResultEquals(change, queryOne("label:Code-Review>=0"));
    assertResultEquals(change, queryOne("label:Code-Review>0"));
    assertResultEquals(change, queryOne("label:Code-Review>=1"));
    assertThat(query("label:Code-Review>1")).isEmpty();
    assertThat(query("label:Code-Review>=2")).isEmpty();

    assertResultEquals(change, queryOne("label: Code-Review<=2"));
    assertResultEquals(change, queryOne("label: Code-Review<2"));
    assertResultEquals(change, queryOne("label: Code-Review<=1"));
    assertThat(query("label:Code-Review<1")).isEmpty();
    assertThat(query("label:Code-Review<=0")).isEmpty();

    assertThat(query("label:Code-Review=+1,anotheruser")).isEmpty();
    assertResultEquals(change, queryOne("label:Code-Review=+1,user"));
    assertResultEquals(change, queryOne("label:Code-Review=+1,user=user"));
    assertResultEquals(change, queryOne("label:Code-Review=+1,Administrators"));
    assertResultEquals(change, queryOne("label:Code-Review=+1,group=Administrators"));
  }

  private void createGroup(String name, AccountGroup.Id owner, Account.Id member)
      throws Exception {
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(name);
    args.ownerGroupId = owner;
    args.initialMembers = ImmutableList.of(member);
    performCreateGroupFactory.create(args).createGroup();
  }

  @Test
  public void byLabelGroup() throws Exception {
    Account.Id user1 = accountManager
        .authenticate(AuthRequest.forUser("user1")).getAccountId();
    Account.Id user2 = accountManager
        .authenticate(AuthRequest.forUser("user2")).getAccountId();
    TestRepository<InMemoryRepository> repo = createProject("repo");

    // create group and add users
    AccountGroup.Id adminGroup =
        groupCache.get(new AccountGroup.NameKey("Administrators")).getId();
    createGroup("group1", adminGroup, user1);
    createGroup("group2", adminGroup, user2);

    // create a change
    ChangeInserter ins = newChange(repo, null, null, user1.get(), null);
    Change change1 = ins.insert();

    // post a review with user1
    requestContext.setContext(newRequestContext(user1));
    ReviewInput input = new ReviewInput();
    input.labels = ImmutableMap.<String, Short> of("Code-Review", (short) 1);
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins.getPatchSet()), input);

    // verify that query with user1 will return results.
    requestContext.setContext(newRequestContext(userId));
    assertResultEquals(change1, queryOne("label:Code-Review=+1,group1"));
    assertResultEquals(change1, queryOne("label:Code-Review=+1,group=group1"));
    assertResultEquals(change1, queryOne("label:Code-Review=+1,user=user1"));
    assertThat(query("label:Code-Review=+1,user=user2")).isEmpty();
    assertThat(query("label:Code-Review=+1,group=group2")).isEmpty();
  }

  @Test
  public void limit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change last = null;
    int n = 5;
    for (int i = 0; i < n; i++) {
      last = newChange(repo, null, null, null, null).insert();
    }

    List<ChangeInfo> results;
    for (int i = 1; i <= n + 2; i++) {
      int expectedSize;
      Boolean expectedMoreChanges;
      if (i < n) {
        expectedSize = i;
        expectedMoreChanges = true;
      } else {
        expectedSize = n;
        expectedMoreChanges = null;
      }
      results = query("status:new limit:" + i);
      String msg = "i=" + i;
      assert_().withFailureMessage(msg).that(results).hasSize(expectedSize);
      assertResultEquals(last, results.get(0));
      assert_().withFailureMessage(msg)
          .that(results.get(results.size() - 1)._moreChanges)
          .isEqualTo(expectedMoreChanges);
    }
  }

  @Test
  public void start() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    List<Change> changes = Lists.newArrayList();
    for (int i = 0; i < 2; i++) {
      changes.add(newChange(repo, null, null, null, null).insert());
    }

    QueryChanges q;
    List<ChangeInfo> results;
    results = query("status:new");
    assertThat(results).hasSize(2);
    assertResultEquals(changes.get(1), results.get(0));
    assertResultEquals(changes.get(0), results.get(1));

    q = newQuery("status:new");
    q.setStart(1);
    results = query(q);
    assertThat(results).hasSize(1);
    assertResultEquals(changes.get(0), results.get(0));

    q = newQuery("status:new");
    q.setStart(2);
    results = query(q);
    assertThat(results).isEmpty();

    q = newQuery("status:new");
    q.setStart(3);
    results = query(q);
    assertThat(results).isEmpty();
  }

  @Test
  public void startWithLimit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    List<Change> changes = Lists.newArrayList();
    for (int i = 0; i < 3; i++) {
      changes.add(newChange(repo, null, null, null, null).insert());
    }

    QueryChanges q;
    List<ChangeInfo> results;
    results = query("status:new limit:2");
    assertThat(results).hasSize(2);
    assertResultEquals(changes.get(2), results.get(0));
    assertResultEquals(changes.get(1), results.get(1));

    q = newQuery("status:new limit:2");
    q.setStart(1);
    results = query(q);
    assertThat(results).hasSize(2);
    assertResultEquals(changes.get(1), results.get(0));
    assertResultEquals(changes.get(0), results.get(1));

    q = newQuery("status:new limit:2");
    q.setStart(2);
    results = query(q);
    assertThat(results).hasSize(1);
    assertResultEquals(changes.get(0), results.get(0));

    q = newQuery("status:new limit:2");
    q.setStart(3);
    results = query(q);
    assertThat(results).isEmpty();
  }

  @Test
  public void updateOrder() throws Exception {
    clockStepMs = MILLISECONDS.convert(2, MINUTES);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    List<ChangeInserter> inserters = Lists.newArrayList();
    List<Change> changes = Lists.newArrayList();
    for (int i = 0; i < 5; i++) {
      inserters.add(newChange(repo, null, null, null, null));
      changes.add(inserters.get(i).insert());
    }

    for (int i : ImmutableList.of(2, 0, 1, 4, 3)) {
      ReviewInput input = new ReviewInput();
      input.message = "modifying " + i;
      postReview.apply(
          new RevisionResource(
            this.changes.parse(changes.get(i).getId()),
            inserters.get(i).getPatchSet()),
          input);
      changes.set(i, db.changes().get(changes.get(i).getId()));
    }

    List<ChangeInfo> results = query("status:new");
    assertThat(results).hasSize(5);
    assertResultEquals(changes.get(3), results.get(0));
    assertResultEquals(changes.get(4), results.get(1));
    assertResultEquals(changes.get(1), results.get(2));
    assertResultEquals(changes.get(0), results.get(3));
    assertResultEquals(changes.get(2), results.get(4));
  }

  @Test
  public void updatedOrderWithMinuteResolution() throws Exception {
    clockStepMs = MILLISECONDS.convert(2, MINUTES);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertThat(lastUpdatedMs(change1) < lastUpdatedMs(change2)).isTrue();

    List<ChangeInfo> results;
    results = query("status:new");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins1.getPatchSet()), input);
    change1 = db.changes().get(change1.getId());

    assertThat(lastUpdatedMs(change1) > lastUpdatedMs(change2)).isTrue();
    assertThat(lastUpdatedMs(change1) - lastUpdatedMs(change2)
        > MILLISECONDS.convert(1, MINUTES)).isTrue();

    results = query("status:new");
    assertThat(results).hasSize(2);
    // change1 moved to the top.
    assertResultEquals(change1, results.get(0));
    assertResultEquals(change2, results.get(1));
  }

  @Test
  public void updatedOrderWithSubMinuteResolution() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertThat(lastUpdatedMs(change1) < lastUpdatedMs(change2)).isTrue();

    List<ChangeInfo> results;
    results = query("status:new");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins1.getPatchSet()), input);
    change1 = db.changes().get(change1.getId());

    assertThat(lastUpdatedMs(change1) > lastUpdatedMs(change2)).isTrue();
    assertThat(lastUpdatedMs(change1) - lastUpdatedMs(change2)
        < MILLISECONDS.convert(1, MINUTES)).isTrue();

    results = query("status:new");
    assertThat(results).hasSize(2);
    // change1 moved to the top.
    assertResultEquals(change1, results.get(0));
    assertResultEquals(change2, results.get(1));
  }

  @Test
  public void filterOutMoreThanOnePageOfResults() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change = newChange(repo, null, null, userId.get(), null).insert();
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    for (int i = 0; i < 5; i++) {
      newChange(repo, null, null, user2, null).insert();
    }

    assertResultEquals(change, queryOne("status:new ownerin:Administrators"));
    assertResultEquals(change,
        queryOne("status:new ownerin:Administrators limit:2"));
  }

  @Test
  public void filterOutAllResults() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    for (int i = 0; i < 5; i++) {
      newChange(repo, null, null, user2, null).insert();
    }

    assertThat(query("status:new ownerin:Administrators")).isEmpty();
    assertThat(query("status:new ownerin:Administrators limit:2")).isEmpty();
  }

  @Test
  public void byFileExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertThat(query("file:file")).isEmpty();
    assertResultEquals(change, queryOne("file:dir"));
    assertResultEquals(change, queryOne("file:file1"));
    assertResultEquals(change, queryOne("file:file2"));
    assertResultEquals(change, queryOne("file:dir/file1"));
    assertResultEquals(change, queryOne("file:dir/file2"));
  }

  @Test
  public void byFileRegex() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertThat(query("file:.*file.*")).isEmpty();
    assertThat(query("file:^file.*")).isEmpty(); // Whole path only.
    assertResultEquals(change, queryOne("file:^dir.file.*"));
  }

  @Test
  public void byPathExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertThat(query("path:file")).isEmpty();
    assertThat(query("path:dir")).isEmpty();
    assertThat(query("path:file1")).isEmpty();
    assertThat(query("path:file2")).isEmpty();
    assertResultEquals(change, queryOne("path:dir/file1"));
    assertResultEquals(change, queryOne("path:dir/file2"));
  }

  @Test
  public void byPathRegex() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertThat(query("path:.*file.*")).isEmpty();
    assertResultEquals(change, queryOne("path:^dir.file.*"));
  }

  @Test
  public void byComment() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    Change change = ins.insert();

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = "inline";
    input.comments = ImmutableMap.<String, List<ReviewInput.CommentInput>> of(
        Patch.COMMIT_MSG, ImmutableList.<ReviewInput.CommentInput> of(comment));
    postReview.apply(new RevisionResource(
        changes.parse(change.getId()), ins.getPatchSet()), input);

    assertThat(query("comment:foo")).isEmpty();
    assertResultEquals(change, queryOne("comment:toplevel"));
    assertResultEquals(change, queryOne("comment:inline"));
  }

  @Test
  public void byAge() throws Exception {
    long thirtyHours = MILLISECONDS.convert(30, HOURS);
    clockStepMs = thirtyHours;
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();
    clockStepMs = 0; // Queried by AgePredicate constructor.
    long now = TimeUtil.nowMs();
    assertThat(lastUpdatedMs(change2) - lastUpdatedMs(change1)).isEqualTo(thirtyHours);
    assertThat(now - lastUpdatedMs(change2)).isEqualTo(thirtyHours);
    assertThat(TimeUtil.nowMs()).isEqualTo(now);

    assertThat(query("-age:1d")).isEmpty();
    assertThat(query("-age:" + (30 * 60 - 1) + "m")).isEmpty();
    assertResultEquals(change2, queryOne("-age:2d"));

    List<ChangeInfo> results;
    results = query("-age:3d");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    assertThat(query("age:3d")).isEmpty();
    assertResultEquals(change1, queryOne("age:2d"));

    results = query("age:1d");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byBefore() throws Exception {
    clockStepMs = MILLISECONDS.convert(30, HOURS);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();
    clockStepMs = 0;

    assertThat(query("before:2009-09-29")).isEmpty();
    assertThat(query("before:2009-09-30")).isEmpty();
    assertThat(query("before:\"2009-09-30 16:59:00 -0400\"")).isEmpty();
    assertThat(query("before:\"2009-09-30 20:59:00 -0000\"")).isEmpty();
    assertThat(query("before:\"2009-09-30 20:59:00\"")).isEmpty();
    assertResultEquals(change1,
        queryOne("before:\"2009-09-30 17:02:00 -0400\""));
    assertResultEquals(change1,
        queryOne("before:\"2009-10-01 21:02:00 -0000\""));
    assertResultEquals(change1,
        queryOne("before:\"2009-10-01 21:02:00\""));
    assertResultEquals(change1, queryOne("before:2009-10-01"));

    List<ChangeInfo> results;
    results = query("before:2009-10-03");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byAfter() throws Exception {
    clockStepMs = MILLISECONDS.convert(30, HOURS);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();
    clockStepMs = 0;

    assertThat(query("after:2009-10-03")).isEmpty();
    assertResultEquals(change2,
        queryOne("after:\"2009-10-01 20:59:59 -0400\""));
    assertResultEquals(change2,
        queryOne("after:\"2009-10-01 20:59:59 -0000\""));
    assertResultEquals(change2, queryOne("after:2009-10-01"));

    List<ChangeInfo> results;
    results = query("after:2009-09-30");
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void bySize() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");

    // added = 3, deleted = 0, delta = 3
    RevCommit commit1 = repo.parseBody(
        repo.commit().add("file1", "foo\n\foo\nfoo").create());
    // added = 0, deleted = 2, delta = 2
    RevCommit commit2 = repo.parseBody(
        repo.commit().parent(commit1).add("file1", "foo").create());

    Change change1 = newChange(repo, commit1, null, null, null).insert();
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    assertThat(query("added:>4")).isEmpty();
    assertResultEquals(change1, queryOne("added:3"));
    assertResultEquals(change1, queryOne("added:>2"));
    assertResultEquals(change1, queryOne("added:>=3"));
    assertResultEquals(change2, queryOne("added:<1"));
    assertResultEquals(change2, queryOne("added:<=0"));

    assertThat(query("deleted:>3")).isEmpty();
    assertResultEquals(change2, queryOne("deleted:2"));
    assertResultEquals(change2, queryOne("deleted:>1"));
    assertResultEquals(change2, queryOne("deleted:>=2"));
    assertResultEquals(change1, queryOne("deleted:<1"));
    assertResultEquals(change1, queryOne("deleted:<=0"));

    for (String str : Lists.newArrayList("delta", "size")) {
      assertThat(query(str + ":<2")).isEmpty();
      assertResultEquals(change1, queryOne(str + ":3"));
      assertResultEquals(change1, queryOne(str + ":>2"));
      assertResultEquals(change1, queryOne(str + ":>=3"));
      assertResultEquals(change2, queryOne(str + ":<3"));
      assertResultEquals(change2, queryOne(str + ":<=2"));
    }
  }

  private List<Change> setUpHashtagChanges() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    HashtagsInput in = new HashtagsInput();
    in.add = ImmutableSet.of("foo");
    gApi.changes().id(change1.getId().get()).setHashtags(in);

    in.add = ImmutableSet.of("foo", "bar", "a tag");
    gApi.changes().id(change2.getId().get()).setHashtags(in);

    return ImmutableList.of(change1, change2);
  }

  @Test
  public void byHashtagWithNotedb() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    List<Change> changes = setUpHashtagChanges();
    List<ChangeInfo> results = query("hashtag:foo");
    assertThat(results).hasSize(2);
    assertResultEquals(changes.get(1), results.get(0));
    assertResultEquals(changes.get(0), results.get(1));
    assertResultEquals(changes.get(1), queryOne("hashtag:bar"));
    assertResultEquals(changes.get(1), queryOne("hashtag:\"a tag\""));
    assertResultEquals(changes.get(1), queryOne("hashtag:\"a tag \""));
    assertResultEquals(changes.get(1), queryOne("hashtag:\" a tag \""));
    assertResultEquals(changes.get(1), queryOne("hashtag:\"#a tag\""));
    assertResultEquals(changes.get(1), queryOne("hashtag:\"# #a tag\""));
  }

  @Test
  public void byHashtagWithoutNotedb() throws Exception {
    assume().that(notesMigration.enabled()).isFalse();
    setUpHashtagChanges();
    assertThat(query("hashtag:foo")).isEmpty();
    assertThat(query("hashtag:bar")).isEmpty();
    assertThat(query("hashtag:\" bar \"")).isEmpty();
    assertThat(query("hashtag:\"a tag\"")).isEmpty();
    assertThat(query("hashtag:\" a tag \"")).isEmpty();
    assertThat(query("hashtag:#foo")).isEmpty();
    assertThat(query("hashtag:\"# #foo\"")).isEmpty();
  }

  @Test
  public void byDefault() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");

    Change change1 = newChange(repo, null, null, null, null).insert();

    RevCommit commit2 = repo.parseBody(
        repo.commit().message("foosubject").create());
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    RevCommit commit3 = repo.parseBody(
        repo.commit()
        .add("Foo.java", "foo contents")
        .create());
    Change change3 = newChange(repo, commit3, null, null, null).insert();

    ChangeInserter ins4 = newChange(repo, null, null, null, null);
    Change change4 = ins4.insert();
    ReviewInput ri4 = new ReviewInput();
    ri4.message = "toplevel";
    ri4.labels = ImmutableMap.<String, Short> of("Code-Review", (short) 1);
    postReview.apply(new RevisionResource(
        changes.parse(change4.getId()), ins4.getPatchSet()), ri4);

    ChangeInserter ins5 = newChange(repo, null, null, null, null);
    Change change5 = ins5.getChange();
    change5.setTopic("feature5");
    ins5.insert();

    Change change6 = newChange(repo, null, null, null, "branch6").insert();

    assertResultEquals(change1,
        queryOne(Integer.toString(change1.getId().get())));
    assertResultEquals(change1, queryOne(ChangeTriplet.format(change1)));
    assertResultEquals(change2, queryOne("foosubject"));
    assertResultEquals(change3, queryOne("Foo.java"));
    assertResultEquals(change4, queryOne("Code-Review+1"));
    assertResultEquals(change4, queryOne("toplevel"));
    assertResultEquals(change5, queryOne("feature5"));
    assertResultEquals(change6, queryOne("branch6"));
    assertResultEquals(change6, queryOne("refs/heads/branch6"));

    assertThat(query("user@example.com")).hasSize(6);
    assertThat(query("repo")).hasSize(6);
  }

  @Test
  public void implicitVisibleTo() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, userId.get(), null).insert();
    ChangeInserter ins2 = newChange(repo, null, null, userId.get(), null);
    Change change2 = ins2.getChange();
    change2.setStatus(Change.Status.DRAFT);
    ins2.insert();

    String q = "project:repo";
    List<ChangeInfo> results = query(q);
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    // Second user cannot see first user's drafts.
    requestContext.setContext(newRequestContext(accountManager
        .authenticate(AuthRequest.forUser("anotheruser")).getAccountId()));
    assertResultEquals(change1, queryOne(q));
  }

  @Test
  public void explicitVisibleTo() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, userId.get(), null).insert();
    ChangeInserter ins2 = newChange(repo, null, null, userId.get(), null);
    Change change2 = ins2.getChange();
    change2.setStatus(Change.Status.DRAFT);
    ins2.insert();

    String q = "project:repo";
    List<ChangeInfo> results = query(q);
    assertThat(results).hasSize(2);
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));

    // Second user cannot see first user's drafts.
    Account.Id user2 = accountManager
        .authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId();
    assertResultEquals(change1, queryOne(q + " visibleto:" + user2.get()));
  }

  protected ChangeInserter newChange(
      TestRepository<InMemoryRepository> repo,
      @Nullable RevCommit commit, @Nullable String key, @Nullable Integer owner,
      @Nullable String branch) throws Exception {
    if (commit == null) {
      commit = repo.parseBody(repo.commit().message("message").create());
    }
    Account.Id ownerId = owner != null ? new Account.Id(owner) : userId;
    branch = MoreObjects.firstNonNull(branch, "refs/heads/master");
    if (!branch.startsWith("refs/heads/")) {
      branch = "refs/heads/" + branch;
    }
    Project.NameKey project = new Project.NameKey(
        repo.getRepository().getDescription().getRepositoryName());

    Change.Id id = new Change.Id(db.nextChangeId());
    if (key == null) {
      key = "I" + Hashing.sha1().newHasher()
          .putInt(id.get())
          .putString(project.get(), UTF_8)
          .putString(commit.name(), UTF_8)
          .putInt(ownerId.get())
          .putString(branch, UTF_8)
          .hash()
          .toString();
    }

    Change change = new Change(new Change.Key(key), id, ownerId,
        new Branch.NameKey(project, branch), TimeUtil.nowTs());
    return changeFactory.create(
        projectControlFactory.controlFor(project, userFactory.create(ownerId)),
        change,
        commit);
  }

  protected void assertResultEquals(Change expected, ChangeInfo actual) {
    assertThat(actual._number).isEqualTo(expected.getId().get());
  }

  protected void assertResultEquals(String message, Change expected,
      ChangeInfo actual) {
    assert_().withFailureMessage(message).that(actual._number)
        .isEqualTo(expected.getId().get());
  }

  protected void assertBadQuery(Object query) throws Exception {
    try {
      query(query);
      fail("expected BadRequestException for query: " + query);
    } catch (BadRequestException e) {
      // Expected.
    }
  }

  protected TestRepository<InMemoryRepository> createProject(String name)
      throws Exception {
    CreateProject create = projectFactory.create(name);
    create.apply(TLR, new ProjectInput());
    return new TestRepository<>(
        repoManager.openRepository(new Project.NameKey(name)));
  }

  protected QueryChanges newQuery(Object query) {
    QueryChanges q = queryProvider.get();
    q.addQuery(query.toString());
    return q;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected List<ChangeInfo> query(QueryChanges q) throws Exception {
    Object result = q.apply(TLR);
    assert_()
        .withFailureMessage(
            String.format("expected List<ChangeInfo>, found %s for [%s]",
                result, q.getQuery(0))).that(result).isInstanceOf(List.class);
    List results = (List) result;
    if (!results.isEmpty()) {
      assert_()
          .withFailureMessage(
              String.format("expected ChangeInfo, found %s for [%s]", result,
                  q.getQuery(0))).that(results.get(0))
          .isInstanceOf(ChangeInfo.class);
    }
    return (List<ChangeInfo>) result;
  }

  protected List<ChangeInfo> query(Object query) throws Exception {
    return query(newQuery(query));
  }

  protected ChangeInfo queryOne(Object query) throws Exception {
    List<ChangeInfo> results = query(query);
    assert_()
        .withFailureMessage(
            String.format(
                "expected singleton List<ChangeInfo>, found %s for [%s]",
                results, query)).that(results).hasSize(1);
    return results.get(0);
  }

  protected static long lastUpdatedMs(Change c) {
    return c.getLastUpdatedOn().getTime();
  }
}
