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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.List;

@Ignore
public abstract class AbstractQueryChangesTest {
  private static final TopLevelResource TLR = TopLevelResource.INSTANCE;

  @Inject protected AccountManager accountManager;
  @Inject protected ChangeControl.GenericFactory changeControlFactory;
  @Inject protected ChangeInserter.Factory changeFactory;
  @Inject protected CreateProject.Factory projectFactory;
  @Inject protected IdentifiedUser.RequestFactory userFactory;
  @Inject protected InMemoryDatabase schemaFactory;
  @Inject protected InMemoryRepositoryManager repoManager;
  @Inject protected PostReview postReview;
  @Inject protected ProjectControl.GenericFactory projectControlFactory;
  @Inject protected Provider<QueryChanges> queryProvider;
  @Inject protected SchemaCreator schemaCreator;
  @Inject protected ThreadLocalRequestContext requestContext;

  protected LifecycleManager lifecycle;
  protected ReviewDb db;
  protected Account.Id userId;
  protected CurrentUser user;
  protected int clockStepMs = 1;
  private long ts = ChangeUtil.SORT_KEY_EPOCH * 1000;

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
    lifecycle.stop();
    requestContext.setContext(null);
    db.close();
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void byId() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    assertTrue(query("12345").isEmpty());
    assertResultEquals(change1, queryOne(change1.getId().get()));
    assertResultEquals(change2, queryOne(change2.getId().get()));
  }

  @Test
  public void byKey() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change = newChange(repo, null, null, null, null).insert();
    String key = change.getKey().get();

    assertTrue(query("I0000000000000000000000000000000000000000").isEmpty());
    for (int i = 0; i <= 36; i++) {
      String q = key.substring(0, 41 - i);
      assertResultEquals("result for " + q, change, queryOne(q));
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

    assertResultEquals(change1, queryOne("status:new"));
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
    assertEquals(2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
    results = query("is:open");
    assertEquals(2, results.size());
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
    assertEquals(2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
    results = query("is:closed");
    assertEquals(2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byCommit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    ins.insert();
    String sha = ins.getPatchSet().getRevision().get();

    assertTrue(query("0000000000000000000000000000000000000000").isEmpty());
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
    assertEquals(results.toString(), 2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  @Test
  public void byProject() throws Exception {
    TestRepository<InMemoryRepository> repo1 = createProject("repo1");
    TestRepository<InMemoryRepository> repo2 = createProject("repo2");
    Change change1 = newChange(repo1, null, null, null, null).insert();
    Change change2 = newChange(repo2, null, null, null, null).insert();

    assertTrue(query("project:foo").isEmpty());
    assertResultEquals(change1, queryOne("project:repo1"));
    assertResultEquals(change2, queryOne("project:repo2"));
  }

  @Test
  public void byBranchAndRef() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, "master").insert();
    Change change2 = newChange(repo, null, null, null, "branch").insert();

    assertTrue(query("branch:foo").isEmpty());
    assertResultEquals(change1, queryOne("branch:master"));
    assertResultEquals(change1, queryOne("branch:refs/heads/master"));
    assertTrue(query("ref:master").isEmpty());
    assertResultEquals(change1, queryOne("ref:refs/heads/master"));
    assertResultEquals(change1, queryOne("branch:refs/heads/master"));
    assertResultEquals(change2, queryOne("branch:branch"));
    assertResultEquals(change2, queryOne("branch:refs/heads/branch"));
    assertTrue(query("ref:branch").isEmpty());
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

    assertTrue(query("topic:foo").isEmpty());
    assertResultEquals(change1, queryOne("topic:feature1"));
    assertResultEquals(change2, queryOne("topic:feature2"));
  }

  @Test
  public void byMessageExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("one").create());
    Change change1 = newChange(repo, commit1, null, null, null).insert();
    RevCommit commit2 = repo.parseBody(repo.commit().message("two").create());
    Change change2 = newChange(repo, commit2, null, null, null).insert();

    assertTrue(query("topic:foo").isEmpty());
    assertResultEquals(change1, queryOne("message:one"));
    assertResultEquals(change2, queryOne("message:two"));
  }

  @Test
  public void byLabel() throws Exception {
    accountManager.authenticate(AuthRequest.forUser("anotheruser"));
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    Change change = ins.insert();
    ChangeControl ctl = changeControlFactory.controlFor(change, user);

    PostReview.Input input = new PostReview.Input();
    input.message = "toplevel";
    input.labels = ImmutableMap.<String, Short> of("Code-Review", (short) 1);
    postReview.apply(new RevisionResource(
        new ChangeResource(ctl), ins.getPatchSet()), input);

    assertTrue(query("label:Code-Review=-2").isEmpty());
    assertTrue(query("label:Code-Review-2").isEmpty());
    assertTrue(query("label:Code-Review=-1").isEmpty());
    assertTrue(query("label:Code-Review-1").isEmpty());
    assertTrue(query("label:Code-Review=0").isEmpty());
    assertResultEquals(change, queryOne("label:Code-Review=+1"));
    assertResultEquals(change, queryOne("label:Code-Review=1"));
    assertResultEquals(change, queryOne("label:Code-Review+1"));
    assertTrue(query("label:Code-Review=+2").isEmpty());
    assertTrue(query("label:Code-Review=2").isEmpty());
    assertTrue(query("label:Code-Review+2").isEmpty());

    // TODO(dborowitz): > and < are broken at head.

    assertResultEquals(change, queryOne("label:Code-Review>=0"));
    //assertResultEquals(change, queryOne("label:Code-Review>0"));
    assertResultEquals(change, queryOne("label:Code-Review>=1"));
    //assertTrue(query("label:Code-Review>1").isEmpty());
    assertTrue(query("label:Code-Review>=2").isEmpty());

    assertResultEquals(change, queryOne("label: Code-Review<=2"));
    //assertResultEquals(change, queryOne("label: Code-Review<2"));
    assertResultEquals(change, queryOne("label: Code-Review<=1"));
    //assertTrue(query("label: Code-Review<1").isEmpty());
    assertTrue(query("label: Code-Review<=0").isEmpty());

    assertTrue(query("label:Code-Review=+1,anotheruser").isEmpty());
    assertResultEquals(change, queryOne("label:Code-Review=+1,user"));
    assertResultEquals(change, queryOne("label:Code-Review=+1,user=user"));
    assertResultEquals(change, queryOne("label:Code-Review=+1,Administrators"));
    assertResultEquals(change, queryOne("label:Code-Review=+1,group=Administrators"));
  }

  @Test
  public void limit() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    Change change1 = newChange(repo, null, null, null, null).insert();
    Change change2 = newChange(repo, null, null, null, null).insert();

    // TODO(dborowitz): Limit is broken in SQL (returns N+1 results) with some
    // kinds of predicates but not others.
    //assertResultEquals(change2, queryOne("status:new limit:1"));
    List<ChangeInfo> results = query("status:new limit:2");
    assertEquals(2, results.size());
    assertResultEquals(change2, results.get(0));
    assertResultEquals(change1, results.get(1));
  }

  protected ChangeInserter newChange(
      TestRepository<InMemoryRepository> repo,
      @Nullable RevCommit commit, @Nullable String key, @Nullable Integer owner,
      @Nullable String branch) throws Exception {
    if (commit == null) {
      commit = repo.parseBody(repo.commit().message("message").create());
    }
    Account.Id ownerId = owner != null ? new Account.Id(owner) : userId;
    branch = Objects.firstNonNull(branch, "refs/heads/master");
    if (!branch.startsWith("refs/heads/")) {
      branch = "refs/heads/" + branch;
    }
    Project.NameKey project = new Project.NameKey(
        repo.getRepository().getDescription().getRepositoryName());

    Change.Id id = new Change.Id(db.nextChangeId());
    if (key == null) {
      key = "I" + Hashing.sha1().newHasher()
          .putInt(id.get())
          .putString(project.get(), Charsets.UTF_8)
          .putString(commit.name(), Charsets.UTF_8)
          .putInt(ownerId.get())
          .putString(branch, Charsets.UTF_8)
          .hash()
          .toString();
    }

    Change change = new Change(new Change.Key(key), id, ownerId,
        new Branch.NameKey(project, branch));
    change.setLastUpdatedOn(new Timestamp(ts));
    ts += clockStepMs;
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

  protected TestRepository<InMemoryRepository> createProject(String name)
      throws Exception {
    CreateProject create = projectFactory.create(name);
    create.apply(TLR, new CreateProject.Input());
    return new TestRepository<InMemoryRepository>(
        repoManager.openRepository(new Project.NameKey(name)));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected List<ChangeInfo> query(Object query) throws Exception {
    QueryChanges q = queryProvider.get();
    q.addQuery(query.toString());
    Object result = q.apply(TLR);
    assertTrue(
        String.format("expected List<ChangeInfo>, found %s for [%s]",
          result, query),
        result instanceof List);
    List results = (List) result;
    if (!results.isEmpty()) {
      assertTrue(
          String.format("expected ChangeInfo, found %s for [%s]",
            result, query),
          results.get(0) instanceof ChangeInfo);
    }
    return (List<ChangeInfo>) result;
  }

  protected ChangeInfo queryOne(Object query) throws Exception {
    List<ChangeInfo> results = query(query);
    assertTrue(
        String.format("expected singleton List<ChangeInfo>, found %s for [%s]",
          results, query),
        results.size() == 1);
    return results.get(0);
  }
}
