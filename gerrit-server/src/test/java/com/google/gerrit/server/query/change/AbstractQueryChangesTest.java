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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.TimeUtil;
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
  private static final int TIMEOUT = 5;

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

    requestContext.setContext(new RequestContext() {
      @Override
      public CurrentUser getCurrentUser() {
        return user;
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    });
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

    assertQueryResult("12345");
    assertQueryResult(String.valueOf(change1.getId().get()), change1);
    assertQueryResult(String.valueOf(change2.getId().get()), change2);
  }

  @Test
  public void byKey() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change = newChange(repo, null, null, null, null).insert();
    String key = change.getKey().get();

    assertQueryResult("I0000000000000000000000000000000000000000");
    for (int i = 0; i <= 36; i++) {
      String q = key.substring(0, 41 - i);
      assertQueryResult(q, change);
    }
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

    assertQueryResult("status:new", change1);
    assertQueryResult("status:NEW", change1);
    assertQueryResult("is:new", change1);
    assertQueryResult("status:merged", change2);
    assertQueryResult("is:merged", change2);
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

    assertQueryResult("status:open", change2, change1);

    assertEquals(2, query("status:OPEN").size());
    assertEquals(2, query("status:o").size());
    assertEquals(2, query("status:op").size());
    assertEquals(2, query("status:ope").size());
    assertEquals(2, query("status:pending").size());
    assertEquals(2, query("status:PENDING").size());
    assertEquals(2, query("status:p").size());
    assertEquals(2, query("status:pe").size());
    assertEquals(2, query("status:pen").size());

    assertQueryResult("is:open", change2, change1);
  }

  private List<ChangeInfo> query(String query) throws Exception {
    return query(newQuery(query));
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

    assertQueryResult("status:closed", change2, change1);

    assertEquals(2, query("status:CLOSED").size());
    assertEquals(2, query("status:c").size());
    assertEquals(2, query("status:cl").size());
    assertEquals(2, query("status:clo").size());
    assertEquals(2, query("status:clos").size());
    assertEquals(2, query("status:close").size());
    assertEquals(2, query("status:closed").size());

    assertQueryResult("is:closed", change2, change1);
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

    assertQueryResult("status:n", change1);
    assertQueryResult("status:ne", change1);
    assertQueryResult("status:new", change1);
    assertQueryResult("status:N", change1);
    assertQueryResult("status:nE", change1);
    assertQueryResult("status:neW", change1);
    assertBadQuery("status:nx");
    assertBadQuery("status:newx");
  }

  @Test
  public void byCommit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    ins.insert();
    String sha = ins.getPatchSet().getRevision().get();

    assertQueryResult("0000000000000000000000000000000000000000");
    for (int i = 0; i <= 36; i++) {
      String q = sha.substring(0, 40 - i);
      assertQueryResult(q, ins.getChange());
    }
  }

  @Test
  public void byOwner() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, userId.get(), null).insert();
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    Change change2 = newChange(repo, null, null, user2, null).insert();

    assertQueryResult("owner:" + userId.get(), change1);
    assertQueryResult("owner:" + user2, change2);
  }

  @Test
  public void byOwnerIn() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, userId.get(), null).insert();
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    Change change2 = newChange(repo, null, null, user2, null).insert();

    assertQueryResult("ownerin:Administrators", change1);
    assertQueryResult("ownerin:\"Registered Users\"", change2, change1);
  }

  @Test
  public void byProject() throws Exception {
    TestRepository<InMemoryRepository> repo1 = createProject("repo1");
    TestRepository<InMemoryRepository> repo2 = createProject("repo2");
    Change change1 = newChange(repo1, null, null, null, null).insert();
    Change change2 = newChange(repo2, null, null, null, null).insert();

    assertQueryResult("project:foo");
    assertQueryResult("project:repo");
    assertQueryResult("project:repo1", change1);
    assertQueryResult("project:repo2", change2);
  }

  @Test
  public void byProjectPrefix() throws Exception {
    TestRepository<InMemoryRepository> repo1 = createProject("repo1");
    TestRepository<InMemoryRepository> repo2 = createProject("repo2");
    Change change1 = newChange(repo1, null, null, null, null).insert();
    Change change2 = newChange(repo2, null, null, null, null).insert();

    assertTrue(query("projects:foo").isEmpty());
    assertQueryResult("projects:repo1", change1);
    assertQueryResult("projects:repo2", change2);

    List<ChangeInfo> results;
    results = query("projects:repo");
    assertEquals(results.toString(), 2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byBranchAndRef() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, "master").insert();
    Change change2 = newChange(repo, null, null, null, "branch").insert();

    assertQueryResult("branch:foo");
    assertQueryResult("branch:master", change1);
    assertQueryResult("branch:refs/heads/master", change1);
    assertQueryResult("ref:master");
    assertQueryResult("ref:refs/heads/master", change1);
    assertQueryResult("branch:refs/heads/master", change1);
    assertQueryResult("branch:branch", change2);
    assertQueryResult("branch:refs/heads/branch", change2);
    assertQueryResult("ref:branch");
    assertQueryResult("ref:refs/heads/branch", change2);
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

    assertQueryResult("topic:foo");
    assertQueryResult("topic:feature1", change1);
    assertQueryResult("topic:feature2", change2);
    assertQueryResult("topic:\"\"", change3);
  }

  @Test
  public void byMessageExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("one").create());
    Change change1 = newChange(repo, commit1, null, null, null).insert();
    RevCommit commit2 = repo.parseBody(repo.commit().message("two").create());
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    assertQueryResult("message:foo");
    assertQueryResult("message:one", change1);
    assertQueryResult("message:two", change2);
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

    assertQueryResult("message:1234");
    assertQueryResult("message:12345", change1);
    assertQueryResult("message:12346", change2);
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

    assertQueryResult("label:Code-Review=-2");
    assertQueryResult("label:Code-Review-2");
    assertQueryResult("label:Code-Review=-1");
    assertQueryResult("label:Code-Review-1");
    assertQueryResult("label:Code-Review=0");
    assertQueryResult("label:Code-Review=+1", change);
    assertQueryResult("label:Code-Review=1", change);
    assertQueryResult("label:Code-Review+1", change);
    assertQueryResult("label:Code-Review=+2");
    assertQueryResult("label:Code-Review=2");
    assertQueryResult("label:Code-Review+2");

    assertQueryResult("label:Code-Review>=0", change);
    assertQueryResult("label:Code-Review>0", change);
    assertQueryResult("label:Code-Review>=1", change);
    assertQueryResult("label:Code-Review>1");
    assertQueryResult("label:Code-Review>=2");

    assertQueryResult("label: Code-Review<=2", change);
    assertQueryResult("label: Code-Review<2", change);
    assertQueryResult("label: Code-Review<=1", change);
    assertQueryResult("label:Code-Review<1");
    assertQueryResult("label:Code-Review<=0");

    assertQueryResult("label:Code-Review=+1,anotheruser");
    assertQueryResult("label:Code-Review=+1,user", change);
    assertQueryResult("label:Code-Review=+1,user=user", change);
    assertQueryResult("label:Code-Review=+1,Administrators", change);
    assertQueryResult("label:Code-Review=+1,group=Administrators", change);
  }

  @Test
  public void limit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change c = null;
    final int n = 5;
    for (int i = 0; i < n; i++) {
      c = newChange(repo, null, null, null, null).insert();
    }
    final Change last = c;

    for (int i = 1; i <= n + 2; i++) {
      final int expectedSize = Math.min(i, n);

      Predicate<List<ChangeInfo>> expectation = new Predicate<List<ChangeInfo>>() {
        @Override
        public boolean apply(List<ChangeInfo> results) {
          return expectedSize ==  results.size() && last.getId().get() == results.get(0)._number;
        }};
      assertQueryResult("status:new limit:" + i, expectation);
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
    assertQueryResult("status:new", changes.get(1), changes.get(0));

    q = newQuery("status:new");
    q.setStart(1);
    assertQueryResult(q, changes.get(0));

    q = newQuery("status:new");
    q.setStart(2);
    assertQueryResult(q);

    q = newQuery("status:new");
    q.setStart(3);
    assertQueryResult(q);
  }


  @Test
  public void startWithLimit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    List<Change> changes = Lists.newArrayList();
    for (int i = 0; i < 3; i++) {
      changes.add(newChange(repo, null, null, null, null).insert());
    }

    assertQueryResult("status:new limit:2", changes.get(2), changes.get(1));

    QueryChanges q = newQuery("status:new limit:2");
    q.setStart(1);
    assertQueryResult(q, changes.get(1), changes.get(0));

    q = newQuery("status:new limit:2");
    q.setStart(2);
    assertQueryResult(q, changes.get(0));

    q = newQuery("status:new limit:2");
    q.setStart(3);
    assertQueryResult(q);
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

    assertQueryResult(
        "status:new",
        changes.get(3), changes.get(4), changes.get(1),
            changes.get(0), changes.get(2));
  }

  @Test
  public void updatedOrderWithMinuteResolution() throws Exception {
    clockStepMs = MILLISECONDS.convert(2, MINUTES);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertTrue(lastUpdatedMs(change1) < lastUpdatedMs(change2));

    assertQueryResult("status:new", change2, change1);

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins1.getPatchSet()), input);
    change1 = db.changes().get(change1.getId());

    assertTrue(lastUpdatedMs(change1) > lastUpdatedMs(change2));
    assertTrue(lastUpdatedMs(change1) - lastUpdatedMs(change2) > MILLISECONDS
        .convert(1, MINUTES));

    assertQueryResult("status:new", change1, change2);
  }

  @Test
  public void updatedOrderWithSubMinuteResolution() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo, null, null, null, null);
    Change change1 = ins1.insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertTrue(lastUpdatedMs(change1) < lastUpdatedMs(change2));

    assertQueryResult("status:new", change2, change1);

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    postReview.apply(new RevisionResource(
        changes.parse(change1.getId()), ins1.getPatchSet()), input);
    change1 = db.changes().get(change1.getId());

    assertTrue(lastUpdatedMs(change1) > lastUpdatedMs(change2));
    assertTrue(lastUpdatedMs(change1) - lastUpdatedMs(change2)
        < MILLISECONDS.convert(1, MINUTES));

    // change1 moved to the top.
    assertQueryResult("status:new", change1, change2);
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

    // assertResultEquals(change,
    // queryOne("status:new ownerin:Administrators"));
    assertQueryResult("status:new ownerin:Administrators limit:2", change);
  }

  @Test
  public void filterOutAllResults() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    int user2 = accountManager.authenticate(AuthRequest.forUser("anotheruser"))
        .getAccountId().get();
    for (int i = 0; i < 5; i++) {
      newChange(repo, null, null, user2, null).insert();
    }

    assertQueryResult("status:new ownerin:Administrators");
    assertQueryResult("status:new ownerin:Administrators limit:2");
  }

  @Test
  public void byFileExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertQueryResult("file:file");
    assertQueryResult("file:dir", change);
    assertQueryResult("file:file1", change);
    assertQueryResult("file:file2", change);
    assertQueryResult("file:dir/file1", change);
    assertQueryResult("file:dir/file2", change);
  }

  @Test
  public void byFileRegex() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertQueryResult("file:.*file.*");
    assertQueryResult("file:^file.*"); // Whole path only.
    assertQueryResult("file:^dir.file.*", change);
  }

  @Test
  public void byPathExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertQueryResult("path:file");
    assertQueryResult("path:dir");
    assertQueryResult("path:file1");
    assertQueryResult("path:file2");
    assertQueryResult("path:dir/file1", change);
    assertQueryResult("path:dir/file2", change);
  }

  @Test
  public void byPathRegex() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("dir/file1", "contents1").add("dir/file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertQueryResult("path:.*file.*");
    assertQueryResult("path:^dir.file.*", change);
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

    assertQueryResult("comment:foo");
    assertQueryResult("comment:toplevel", change);
    assertQueryResult("comment:inline", change);
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
    assertEquals(thirtyHours, lastUpdatedMs(change2) - lastUpdatedMs(change1));
    assertEquals(thirtyHours, now - lastUpdatedMs(change2));
    assertEquals(now, TimeUtil.nowMs());

    assertQueryResult("-age:1d");
    assertQueryResult("-age:" + (30 * 60 - 1) + "m");
    assertQueryResult("-age:2d", change2);

    assertQueryResult("-age:3d", change2, change1);

    assertQueryResult("age:3d");
    assertQueryResult("age:2d", change1);

    assertQueryResult("age:1d", change2, change1);
  }

  @Test
  public void byBefore() throws Exception {
    clockStepMs = MILLISECONDS.convert(30, HOURS);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();
    clockStepMs = 0;

    assertQueryResult("before:2009-09-29");
    assertQueryResult("before:2009-09-30");
    assertQueryResult("before:\"2009-09-30 16:59:00 -0400\"");
    assertQueryResult("before:\"2009-09-30 20:59:00 -0000\"");
    assertQueryResult("before:\"2009-09-30 20:59:00\"");
    assertQueryResult("before:\"2009-09-30 17:02:00 -0400\"", change1);
    assertQueryResult("before:\"2009-10-01 21:02:00 -0000\"", change1);
    assertQueryResult("before:\"2009-10-01 21:02:00\"", change1);
    assertQueryResult("before:2009-10-01", change1);

    assertQueryResult("before:2009-10-03", change2, change1);
  }

  @Test
  public void byAfter() throws Exception {
    clockStepMs = MILLISECONDS.convert(30, HOURS);
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();
    clockStepMs = 0;

    assertQueryResult("after:2009-10-03");
    assertQueryResult("after:\"2009-10-01 20:59:59 -0400\"", change2);
    assertQueryResult("after:\"2009-10-01 20:59:59 -0000\"", change2);
    assertQueryResult("after:2009-10-01", change2);

    assertQueryResult("after:2009-09-30", change2, change1);
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

    assertTrue(query("added:>4").isEmpty());
    assertQueryResult("added:3", change1);
    assertQueryResult("added:>2", change1);
    assertQueryResult("added:>=3", change1);
    assertQueryResult("added:<1", change2);
    assertQueryResult("added:<=0", change2);

    assertTrue(query("deleted:>3").isEmpty());
    assertQueryResult("deleted:2", change2);
    assertQueryResult("deleted:>1", change2);
    assertQueryResult("deleted:>=2", change2);
    assertQueryResult("deleted:<1", change1);
    assertQueryResult("deleted:<=0", change1);

    for (String str : Lists.newArrayList("delta", "size")) {
      assertTrue(query(str + ":<2").isEmpty());
      assertQueryResult(str + ":3", change1);
      assertQueryResult(str + ":>2", change1);
      assertQueryResult(str + ":>=3", change1);
      assertQueryResult(str + ":<3", change2);
      assertQueryResult(str + ":<=2", change2);
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
    assumeTrue(notesMigration.enabled());
    List<Change> changes = setUpHashtagChanges();
    List<ChangeInfo> results = query("hashtag:foo");
    assertEquals(2, results.size());
    assertResultEquals(changes.get(1), results.get(0));
    assertResultEquals(changes.get(0), results.get(1));
    assertQueryResult("hashtag:bar", changes.get(1));
    assertQueryResult("hashtag:\"a tag\"", changes.get(1));
    assertQueryResult("hashtag:\"a tag \"", changes.get(1));
    assertQueryResult("hashtag:\" a tag \"", changes.get(1));
    assertQueryResult("hashtag:\"#a tag\"", changes.get(1));
    assertQueryResult("hashtag:\"# #a tag\"", changes.get(1));
  }

  @Test
  public void byHashtagWithoutNotedb() throws Exception {
    assumeTrue(!notesMigration.enabled());
    setUpHashtagChanges();
    assertTrue(query("hashtag:foo").isEmpty());
    assertTrue(query("hashtag:bar").isEmpty());
    assertTrue(query("hashtag:\" bar \"").isEmpty());
    assertTrue(query("hashtag:\"a tag\"").isEmpty());
    assertTrue(query("hashtag:\" a tag \"").isEmpty());
    assertTrue(query("hashtag:#foo").isEmpty());
    assertTrue(query("hashtag:\"# #foo\"").isEmpty());
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

    assertQueryResult(Integer.toString(change1.getId().get()), change1);
    assertQueryResult("foosubject", change2);
    assertQueryResult("Foo.java", change3);
    assertQueryResult("Code-Review+1", change4);
    assertQueryResult("toplevel", change4);
    assertQueryResult("feature5", change5);
    assertQueryResult("branch6", change6);
    assertQueryResult("refs/heads/branch6", change6);

    assertEquals(6, query("user@example.com").size());
    assertEquals(6, query("repo").size());
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
        projectControlFactory.controlFor(project,
          userFactory.create(ownerId)).controlFor(change).getRefControl(),
        change,
        commit);
  }

  protected void assertResultEquals(Change expected, ChangeInfo actual) {
    assertEquals(expected.getId().get(), actual._number);
  }

  protected void assertResultEquals(String message, Change expected,
      ChangeInfo actual) {
    assertEquals(message, expected.getId().get(), actual._number);
  }

  protected void assertBadQuery(String query) throws Exception {
    try {
      query(newQuery(query));
      fail("expected BadRequestException for query: " + query);
    } catch (BadRequestException e) {
      // Expected.
    }
  }

  protected TestRepository<InMemoryRepository> createProject(String name)
      throws Exception {
    CreateProject create = projectFactory.create(name);
    create.apply(TLR, new ProjectInput());
    return new TestRepository<InMemoryRepository>(
        repoManager.openRepository(new Project.NameKey(name)));
  }

  protected QueryChanges newQuery(String query) {
    QueryChanges q = queryProvider.get();
    q.addQuery(query);
    return q;
  }

  protected List<ChangeInfo> query(QueryChanges q) throws Exception {
    Object result = q.apply(TLR);
    assertTrue(
        String.format("expected List<ChangeInfo>, found %s for [%s]",
          result, q.getQuery(0)),
        result instanceof List);
    @SuppressWarnings("unchecked")
    List<ChangeInfo> results = (List<ChangeInfo>) result;
    if (!results.isEmpty()) {
      assertTrue(
          String.format("expected ChangeInfo, found %s for [%s]",
            result, q.getQuery(0)),
          results.get(0) instanceof ChangeInfo);
    }
    return results;
  }

  protected void assertQueryResult(QueryChanges q,
      Predicate<List<ChangeInfo>> expectation) throws Exception {
    long end = TimeUtil.nowMs() + MILLISECONDS.convert(TIMEOUT, SECONDS);
    List<ChangeInfo> results = query(q);
    while (!expectation.apply(results)) {
      String message = String.format("Expected result not found withing %d seconds. "
          + "Expected List<ChangeInfo>, found %s for [%s]", TIMEOUT, results,
          q.getQuery(0));
      assertTrue(message, TimeUtil.nowMs() < end);
      Thread.sleep(100);
      results = query(q);
    }
  }

  protected void assertQueryResult(String query,
      Predicate<List<ChangeInfo>> expectation) throws Exception {
    assertQueryResult(newQuery(query), expectation);
  }

  protected void assertQueryResult(String query, Change ... changes) throws Exception {
    assertQueryResult(newQuery(query), expectedChanges(changes));
  }

  protected void assertQueryResult(QueryChanges query, Change ... changes) throws Exception {
    assertQueryResult(query, expectedChanges(changes));
  }

  protected static long lastUpdatedMs(Change c) {
    return c.getLastUpdatedOn().getTime();
  }

  protected static Predicate<List<ChangeInfo>> expectedChanges(
      final Change... changes) {
    return new Predicate<List<ChangeInfo>>() {
      @Override
      public boolean apply(List<ChangeInfo> results) {
        if (results.size() != changes.length) {
          return false;
        }

        for (int i = 0; i < changes.length; i++) {
          if (changes[i].getId().get() != results.get(i)._number) {
            return false;
          }
        }

        return true;
      }
    };
  }
}
