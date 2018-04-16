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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.client.ListChangesOption.REVIEWED;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.truth.ThrowableSubject;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.Changes.QueryRequest;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.index.change.StalenessChecker;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.DisabledReviewDb;
import com.google.gerrit.testutil.GerritServerTests;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gerrit.testutil.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractQueryChangesTest extends GerritServerTests {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxPages", 10);
    cfg.setString("trackingid", "query-bug", "footer", "Bug:");
    cfg.setString("trackingid", "query-bug", "match", "QUERY\\d{2,8}");
    cfg.setString("trackingid", "query-bug", "system", "querytests");
    cfg.setString("trackingid", "query-feature", "footer", "Feature");
    cfg.setString("trackingid", "query-feature", "match", "QUERY\\d{2,8}");
    cfg.setString("trackingid", "query-feature", "system", "querytests");
    return cfg;
  }

  @Inject protected AccountManager accountManager;
  @Inject protected AllUsersName allUsersName;
  @Inject protected BatchUpdate.Factory updateFactory;
  @Inject protected ChangeInserter.Factory changeFactory;
  @Inject protected ChangeQueryBuilder queryBuilder;
  @Inject protected GerritApi gApi;
  @Inject protected IdentifiedUser.GenericFactory userFactory;
  @Inject protected ChangeIndexCollection indexes;
  @Inject protected ChangeIndexer indexer;
  @Inject protected IndexConfig indexConfig;
  @Inject protected InMemoryDatabase schemaFactory;
  @Inject protected InMemoryRepositoryManager repoManager;
  @Inject protected InternalChangeQuery internalChangeQuery;
  @Inject protected ChangeNotes.Factory notesFactory;
  @Inject protected PatchSetInserter.Factory patchSetFactory;
  @Inject protected PatchSetUtil psUtil;
  @Inject protected ChangeControl.GenericFactory changeControlFactory;
  @Inject protected ChangeQueryProcessor queryProcessor;
  @Inject protected SchemaCreator schemaCreator;
  @Inject protected Sequences seq;
  @Inject protected ThreadLocalRequestContext requestContext;
  @Inject protected ProjectCache projectCache;
  @Inject protected MetaDataUpdate.Server metaDataUpdateFactory;
  @Inject protected IdentifiedUser.GenericFactory identifiedUserFactory;

  protected Injector injector;
  protected LifecycleManager lifecycle;
  protected ReviewDb db;
  protected Account.Id userId;
  protected CurrentUser user;

  private String systemTimeZone;

  protected abstract Injector createInjector();

  @Before
  public void setUpInjector() throws Exception {
    lifecycle = new LifecycleManager();
    injector = createInjector();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();
    setUpDatabase();
  }

  @After
  public void cleanUp() {
    lifecycle.stop();
    db.close();
  }

  protected void setUpDatabase() throws Exception {
    db = schemaFactory.open();
    schemaCreator.create(db);

    userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    Account userAccount = db.accounts().get(userId);
    userAccount.setPreferredEmail("user@example.com");
    db.accounts().update(ImmutableList.of(userAccount));
    user = userFactory.create(userId);
    requestContext.setContext(newRequestContext(userAccount.getId()));
  }

  protected RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser = userFactory.create(requestUserId);
    return new RequestContext() {
      @Override
      public CurrentUser getUser() {
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
    resetTimeWithClockStep(1, SECONDS);
  }

  private void resetTimeWithClockStep(long clockStep, TimeUnit clockStepUnit) {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    // TODO(dborowitz): Figure out why tests fail when stubbing out
    // SystemReader.
    TestTimeUtil.resetWithClockStep(clockStep, clockStepUnit);
    SystemReader.setInstance(null);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void byId() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    assertQuery("12345");
    assertQuery(change1.getId().get(), change1);
    assertQuery(change2.getId().get(), change2);
  }

  @Test
  public void byKey() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));
    String key = change.getKey().get();

    assertQuery("I0000000000000000000000000000000000000000");
    for (int i = 0; i <= 36; i++) {
      String q = key.substring(0, 41 - i);
      assertQuery(q, change);
    }
  }

  @Test
  public void byTriplet() throws Exception {
    TestRepository<Repo> repo = createProject("iabcde");
    Change change = insert(repo, newChangeForBranch(repo, "branch"));
    String k = change.getKey().get();

    assertQuery("iabcde~branch~" + k, change);
    assertQuery("change:iabcde~branch~" + k, change);
    assertQuery("iabcde~refs/heads/branch~" + k, change);
    assertQuery("change:iabcde~refs/heads/branch~" + k, change);
    assertQuery("iabcde~branch~" + k.substring(0, 10), change);
    assertQuery("change:iabcde~branch~" + k.substring(0, 10), change);

    assertQuery("foo~bar");
    assertThatQueryException("change:foo~bar").hasMessageThat().isEqualTo("Invalid change format");
    assertQuery("otherrepo~branch~" + k);
    assertQuery("change:otherrepo~branch~" + k);
    assertQuery("iabcde~otherbranch~" + k);
    assertQuery("change:iabcde~otherbranch~" + k);
    assertQuery("iabcde~branch~I0000000000000000000000000000000000000000");
    assertQuery("change:iabcde~branch~I0000000000000000000000000000000000000000");
  }

  @Test
  public void byStatus() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.NEW);
    Change change1 = insert(repo, ins1);
    ChangeInserter ins2 = newChangeWithStatus(repo, Change.Status.MERGED);
    Change change2 = insert(repo, ins2);

    assertQuery("status:new", change1);
    assertQuery("status:NEW", change1);
    assertQuery("is:new", change1);
    assertQuery("status:merged", change2);
    assertQuery("is:merged", change2);
  }

  @Test
  public void byStatusOpen() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.NEW);
    Change change1 = insert(repo, ins1);
    ChangeInserter ins2 = newChangeWithStatus(repo, Change.Status.DRAFT);
    Change change2 = insert(repo, ins2);
    insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));

    Change[] expected = new Change[] {change2, change1};
    assertQuery("status:open", expected);
    assertQuery("status:OPEN", expected);
    assertQuery("status:o", expected);
    assertQuery("status:op", expected);
    assertQuery("status:ope", expected);
    assertQuery("status:pending", expected);
    assertQuery("status:PENDING", expected);
    assertQuery("status:p", expected);
    assertQuery("status:pe", expected);
    assertQuery("status:pen", expected);
    assertQuery("is:open", expected);
    assertQuery("is:pending", expected);
  }

  @Test
  public void byStatusDraft() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    insert(repo, newChangeWithStatus(repo, Change.Status.NEW));
    ChangeInserter ins2 = newChangeWithStatus(repo, Change.Status.DRAFT);
    Change change2 = insert(repo, ins2);

    Change[] expected = new Change[] {change2};
    assertQuery("status:draft", expected);
    assertQuery("status:DRAFT", expected);
    assertQuery("status:d", expected);
    assertQuery("status:dr", expected);
    assertQuery("status:dra", expected);
    assertQuery("status:draf", expected);
    assertQuery("is:draft", expected);
  }

  @Test
  public void byStatusClosed() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.MERGED);
    Change change1 = insert(repo, ins1);
    ChangeInserter ins2 = newChangeWithStatus(repo, Change.Status.ABANDONED);
    Change change2 = insert(repo, ins2);
    insert(repo, newChangeWithStatus(repo, Change.Status.NEW));

    Change[] expected = new Change[] {change2, change1};
    assertQuery("status:closed", expected);
    assertQuery("status:CLOSED", expected);
    assertQuery("status:c", expected);
    assertQuery("status:cl", expected);
    assertQuery("status:clo", expected);
    assertQuery("status:clos", expected);
    assertQuery("status:close", expected);
    assertQuery("status:closed", expected);
    assertQuery("is:closed", expected);
  }

  @Test
  public void byStatusAbandoned() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.MERGED);
    insert(repo, ins1);
    ChangeInserter ins2 = newChangeWithStatus(repo, Change.Status.ABANDONED);
    Change change1 = insert(repo, ins2);
    insert(repo, newChangeWithStatus(repo, Change.Status.NEW));

    assertQuery("status:abandoned", change1);
    assertQuery("status:ABANDONED", change1);
    assertQuery("is:abandoned", change1);
  }

  @Test
  public void byStatusPrefix() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.NEW);
    Change change1 = insert(repo, ins1);
    insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));

    assertQuery("status:n", change1);
    assertQuery("status:ne", change1);
    assertQuery("status:new", change1);
    assertQuery("status:N", change1);
    assertQuery("status:nE", change1);
    assertQuery("status:neW", change1);
    assertThatQueryException("status:nx").hasMessageThat().isEqualTo("invalid change status: nx");
    assertThatQueryException("status:newx")
        .hasMessageThat()
        .isEqualTo("invalid change status: newx");
  }

  @Test
  public void byCommit() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo);
    insert(repo, ins);
    String sha = ins.getCommit().name();

    assertQuery("0000000000000000000000000000000000000000");
    for (int i = 0; i <= 36; i++) {
      String q = sha.substring(0, 40 - i);
      assertQuery(q, ins.getChange());
    }
  }

  @Test
  public void byOwner() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);

    assertQuery("owner:" + userId.get(), change1);
    assertQuery("owner:" + user2, change2);

    String nameEmail = user.asIdentifiedUser().getNameEmail();
    assertQuery("owner: \"" + nameEmail + "\"", change1);
  }

  @Test
  public void byAuthor() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);

    // By exact email address
    assertQuery("author:jauthor@example.com", change1);

    // By email address part
    assertQuery("author:jauthor", change1);
    assertQuery("author:example", change1);
    assertQuery("author:example.com", change1);

    // By name part
    assertQuery("author:Author", change1);

    // Case insensitive
    assertQuery("author:jAuThOr", change1);
    assertQuery("author:ExAmPlE", change1);

    // By non-existing email address / name / part
    assertQuery("author:jcommitter@example.com");
    assertQuery("author:somewhere.com");
    assertQuery("author:jcommitter");
    assertQuery("author:Committer");
  }

  @Test
  public void byCommitter() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);

    // By exact email address
    assertQuery("committer:jcommitter@example.com", change1);

    // By email address part
    assertQuery("committer:jcommitter", change1);
    assertQuery("committer:example", change1);
    assertQuery("committer:example.com", change1);

    // By name part
    assertQuery("committer:Committer", change1);

    // Case insensitive
    assertQuery("committer:jCoMmItTeR", change1);
    assertQuery("committer:ExAmPlE", change1);

    // By non-existing email address / name / part
    assertQuery("committer:jauthor@example.com");
    assertQuery("committer:somewhere.com");
    assertQuery("committer:jauthor");
    assertQuery("committer:Author");
  }

  @Test
  public void byOwnerIn() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);
    Change change3 = insert(repo, newChange(repo), user2);
    gApi.changes().id(change3.getId().get()).current().review(ReviewInput.approve());
    gApi.changes().id(change3.getId().get()).current().submit();

    assertQuery("ownerin:Administrators", change1);
    assertQuery("ownerin:\"Registered Users\"", change2, change1);
    assertQuery("ownerin:\"Registered Users\" status:merged", change3);
  }

  @Test
  public void byProject() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("project:foo");
    assertQuery("project:repo");
    assertQuery("project:repo1", change1);
    assertQuery("project:repo2", change2);
  }

  @Test
  public void byParentProject() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2", "repo1");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("parentproject:repo1", change2, change1);
    assertQuery("parentproject:repo2", change2);
  }

  @Test
  public void byProjectPrefix() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("projects:foo");
    assertQuery("projects:repo1", change1);
    assertQuery("projects:repo2", change2);
    assertQuery("projects:repo", change2, change1);
  }

  @Test
  public void byBranchAndRef() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeForBranch(repo, "master"));
    Change change2 = insert(repo, newChangeForBranch(repo, "branch"));

    assertQuery("branch:foo");
    assertQuery("branch:master", change1);
    assertQuery("branch:refs/heads/master", change1);
    assertQuery("ref:master");
    assertQuery("ref:refs/heads/master", change1);
    assertQuery("branch:refs/heads/master", change1);
    assertQuery("branch:branch", change2);
    assertQuery("branch:refs/heads/branch", change2);
    assertQuery("ref:branch");
    assertQuery("ref:refs/heads/branch", change2);
  }

  @Test
  public void byTopic() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithTopic(repo, "feature1");
    Change change1 = insert(repo, ins1);

    ChangeInserter ins2 = newChangeWithTopic(repo, "feature2");
    Change change2 = insert(repo, ins2);

    ChangeInserter ins3 = newChangeWithTopic(repo, "Cherrypick-feature2");
    Change change3 = insert(repo, ins3);

    ChangeInserter ins4 = newChangeWithTopic(repo, "feature2-fixup");
    Change change4 = insert(repo, ins4);

    Change change5 = insert(repo, newChange(repo));

    assertQuery("intopic:foo");
    assertQuery("intopic:feature1", change1);
    assertQuery("intopic:feature2", change4, change3, change2);
    assertQuery("topic:feature2", change2);
    assertQuery("intopic:feature2", change4, change3, change2);
    assertQuery("intopic:fixup", change4);
    assertQuery("topic:\"\"", change5);
    assertQuery("intopic:\"\"", change5);
    assertQuery("intopic:^feature2.*", change4, change2);
    assertQuery("intopic:{^.*feature2$}", change3, change2);
  }

  @Test
  public void byMessageExact() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("one").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("two").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("message:foo");
    assertQuery("message:one", change1);
    assertQuery("message:two", change2);
  }

  @Test
  public void fullTextWithNumbers() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("12345 67890").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("12346 67891").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("message:1234");
    assertQuery("message:12345", change1);
    assertQuery("message:12346", change2);
  }

  @Test
  public void byLabel() throws Exception {
    accountManager.authenticate(AuthRequest.forUser("anotheruser"));
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    ChangeInserter ins3 = newChange(repo, null, null, null, null);
    ChangeInserter ins4 = newChange(repo, null, null, null, null);
    ChangeInserter ins5 = newChange(repo, null, null, null, null);

    Change reviewMinus2Change = insert(repo, ins);
    gApi.changes().id(reviewMinus2Change.getId().get()).current().review(ReviewInput.reject());

    Change reviewMinus1Change = insert(repo, ins2);
    gApi.changes().id(reviewMinus1Change.getId().get()).current().review(ReviewInput.dislike());

    Change noLabelChange = insert(repo, ins3);

    Change reviewPlus1Change = insert(repo, ins4);
    gApi.changes().id(reviewPlus1Change.getId().get()).current().review(ReviewInput.recommend());

    Change reviewPlus2Change = insert(repo, ins5);
    gApi.changes().id(reviewPlus2Change.getId().get()).current().review(ReviewInput.approve());

    Map<String, Short> m =
        gApi.changes()
            .id(reviewPlus1Change.getId().get())
            .reviewer(user.getAccountId().toString())
            .votes();
    assertThat(m).hasSize(1);
    assertThat(m).containsEntry("Code-Review", Short.valueOf((short) 1));

    Map<Integer, Change> changes = new LinkedHashMap<>(5);
    changes.put(2, reviewPlus2Change);
    changes.put(1, reviewPlus1Change);
    changes.put(0, noLabelChange);
    changes.put(-1, reviewMinus1Change);
    changes.put(-2, reviewMinus2Change);

    assertQuery("label:Code-Review=-2", reviewMinus2Change);
    assertQuery("label:Code-Review-2", reviewMinus2Change);
    assertQuery("label:Code-Review=-1", reviewMinus1Change);
    assertQuery("label:Code-Review-1", reviewMinus1Change);
    assertQuery("label:Code-Review=0", noLabelChange);
    assertQuery("label:Code-Review=+1", reviewPlus1Change);
    assertQuery("label:Code-Review=1", reviewPlus1Change);
    assertQuery("label:Code-Review+1", reviewPlus1Change);
    assertQuery("label:Code-Review=+2", reviewPlus2Change);
    assertQuery("label:Code-Review=2", reviewPlus2Change);
    assertQuery("label:Code-Review+2", reviewPlus2Change);

    assertQuery("label:Code-Review>-3", codeReviewInRange(changes, -2, 2));
    assertQuery("label:Code-Review>=-2", codeReviewInRange(changes, -2, 2));
    assertQuery("label:Code-Review>-2", codeReviewInRange(changes, -1, 2));
    assertQuery("label:Code-Review>=-1", codeReviewInRange(changes, -1, 2));
    assertQuery("label:Code-Review>-1", codeReviewInRange(changes, 0, 2));
    assertQuery("label:Code-Review>=0", codeReviewInRange(changes, 0, 2));
    assertQuery("label:Code-Review>0", codeReviewInRange(changes, 1, 2));
    assertQuery("label:Code-Review>=1", codeReviewInRange(changes, 1, 2));
    assertQuery("label:Code-Review>1", reviewPlus2Change);
    assertQuery("label:Code-Review>=2", reviewPlus2Change);
    assertQuery("label:Code-Review>2");

    assertQuery("label:Code-Review<=2", codeReviewInRange(changes, -2, 2));
    assertQuery("label:Code-Review<2", codeReviewInRange(changes, -2, 1));
    assertQuery("label:Code-Review<=1", codeReviewInRange(changes, -2, 1));
    assertQuery("label:Code-Review<1", codeReviewInRange(changes, -2, 0));
    assertQuery("label:Code-Review<=0", codeReviewInRange(changes, -2, 0));
    assertQuery("label:Code-Review<0", codeReviewInRange(changes, -2, -1));
    assertQuery("label:Code-Review<=-1", codeReviewInRange(changes, -2, -1));
    assertQuery("label:Code-Review<-1", reviewMinus2Change);
    assertQuery("label:Code-Review<=-2", reviewMinus2Change);
    assertQuery("label:Code-Review<-2");

    assertQuery("label:Code-Review=+1,anotheruser");
    assertQuery("label:Code-Review=+1,user", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,user=user", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,Administrators", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,group=Administrators", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,user=owner", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,owner", reviewPlus1Change);
    assertQuery("label:Code-Review=+2,owner", reviewPlus2Change);
    assertQuery("label:Code-Review=-2,owner", reviewMinus2Change);
  }

  @Test
  public void byLabelMulti() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Project.NameKey project =
        new Project.NameKey(repo.getRepository().getDescription().getRepositoryName());
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();

    LabelType verified =
        category("Verified", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    cfg.getLabelSections().put(verified.getName(), verified);

    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(cfg, Permission.forLabel(Util.verified().getName()), -1, 1, REGISTERED_USERS, heads);

    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      cfg.commit(md);
    }
    projectCache.evict(cfg.getProject());

    ReviewInput reviewVerified = new ReviewInput().label("Verified", 1);
    ChangeInserter ins = newChange(repo, null, null, null, null);
    ChangeInserter ins2 = newChange(repo, null, null, null, null);
    ChangeInserter ins3 = newChange(repo, null, null, null, null);
    ChangeInserter ins4 = newChange(repo, null, null, null, null);
    ChangeInserter ins5 = newChange(repo, null, null, null, null);

    // CR+1
    Change reviewCRplus1 = insert(repo, ins);
    gApi.changes().id(reviewCRplus1.getId().get()).current().review(ReviewInput.recommend());

    // CR+2
    Change reviewCRplus2 = insert(repo, ins2);
    gApi.changes().id(reviewCRplus2.getId().get()).current().review(ReviewInput.approve());

    // CR+1 VR+1
    Change reviewCRplus1VRplus1 = insert(repo, ins3);
    gApi.changes().id(reviewCRplus1VRplus1.getId().get()).current().review(ReviewInput.recommend());
    gApi.changes().id(reviewCRplus1VRplus1.getId().get()).current().review(reviewVerified);

    // CR+2 VR+1
    Change reviewCRplus2VRplus1 = insert(repo, ins4);
    gApi.changes().id(reviewCRplus2VRplus1.getId().get()).current().review(ReviewInput.approve());
    gApi.changes().id(reviewCRplus2VRplus1.getId().get()).current().review(reviewVerified);

    // VR+1
    Change reviewVRplus1 = insert(repo, ins5);
    gApi.changes().id(reviewVRplus1.getId().get()).current().review(reviewVerified);

    assertQuery("label:Code-Review=+1", reviewCRplus1VRplus1, reviewCRplus1);
    assertQuery(
        "label:Code-Review>=+1",
        reviewCRplus2VRplus1,
        reviewCRplus1VRplus1,
        reviewCRplus2,
        reviewCRplus1);
    assertQuery("label:Code-Review>=+2", reviewCRplus2VRplus1, reviewCRplus2);

    assertQuery(
        "label:Code-Review>=+1 label:Verified=+1", reviewCRplus2VRplus1, reviewCRplus1VRplus1);
    assertQuery("label:Code-Review>=+2 label:Verified=+1", reviewCRplus2VRplus1);
  }

  @Test
  public void byLabelNotOwner() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    Account.Id user1 = createAccount("user1");

    Change reviewPlus1Change = insert(repo, ins);

    // post a review with user1
    requestContext.setContext(newRequestContext(user1));
    gApi.changes().id(reviewPlus1Change.getId().get()).current().review(ReviewInput.recommend());

    assertQuery("label:Code-Review=+1,user=user1", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,owner");
  }

  private Change[] codeReviewInRange(Map<Integer, Change> changes, int start, int end) {
    int size = 0;
    Change[] range = new Change[end - start + 1];
    for (int i : changes.keySet()) {
      if (i >= start && i <= end) {
        range[size] = changes.get(i);
        size++;
      }
    }
    return range;
  }

  private String createGroup(String name, String owner) throws Exception {
    GroupInput in = new GroupInput();
    in.name = name;
    in.ownerId = owner;
    gApi.groups().create(in);
    return name;
  }

  private Account.Id createAccount(String name) throws Exception {
    return accountManager.authenticate(AuthRequest.forUser(name)).getAccountId();
  }

  @Test
  public void byLabelGroup() throws Exception {
    Account.Id user1 = createAccount("user1");
    createAccount("user2");
    TestRepository<Repo> repo = createProject("repo");

    // create group and add users
    String g1 = createGroup("group1", "Administrators");
    String g2 = createGroup("group2", "Administrators");
    gApi.groups().id(g1).addMembers("user1");
    gApi.groups().id(g2).addMembers("user2");

    // create a change
    Change change1 = insert(repo, newChange(repo), user1);

    // post a review with user1
    requestContext.setContext(newRequestContext(user1));
    gApi.changes()
        .id(change1.getId().get())
        .current()
        .review(new ReviewInput().label("Code-Review", 1));

    // verify that query with user1 will return results.
    requestContext.setContext(newRequestContext(userId));
    assertQuery("label:Code-Review=+1,group1", change1);
    assertQuery("label:Code-Review=+1,group=group1", change1);
    assertQuery("label:Code-Review=+1,user=user1", change1);
    assertQuery("label:Code-Review=+1,user=user2");
    assertQuery("label:Code-Review=+1,group=group2");
  }

  @Test
  public void limit() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change last = null;
    int n = 5;
    for (int i = 0; i < n; i++) {
      last = insert(repo, newChange(repo));
    }

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
      String q = "status:new limit:" + i;
      List<ChangeInfo> results = newQuery(q).get();
      assertThat(results).named(q).hasSize(expectedSize);
      assertThat(results.get(results.size() - 1)._moreChanges)
          .named(q)
          .isEqualTo(expectedMoreChanges);
      assertThat(results.get(0)._number).isEqualTo(last.getId().get());
    }
  }

  @Test
  public void start() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    List<Change> changes = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      changes.add(insert(repo, newChange(repo)));
    }

    assertQuery("status:new", changes.get(1), changes.get(0));
    assertQuery(newQuery("status:new").withStart(1), changes.get(0));
    assertQuery(newQuery("status:new").withStart(2));
    assertQuery(newQuery("status:new").withStart(3));
  }

  @Test
  public void startWithLimit() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    List<Change> changes = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      changes.add(insert(repo, newChange(repo)));
    }

    assertQuery("status:new limit:2", changes.get(2), changes.get(1));
    assertQuery(newQuery("status:new limit:2").withStart(1), changes.get(1), changes.get(0));
    assertQuery(newQuery("status:new limit:2").withStart(2), changes.get(0));
    assertQuery(newQuery("status:new limit:2").withStart(3));
  }

  @Test
  public void maxPages() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));

    QueryRequest query = newQuery("status:new").withLimit(10);
    assertQuery(query, change);
    assertQuery(query.withStart(1));
    assertQuery(query.withStart(99));
    assertThatQueryException(query.withStart(100))
        .hasMessageThat()
        .isEqualTo("Cannot go beyond page 10 of results");
    assertQuery(query.withLimit(100).withStart(100));
  }

  @Test
  public void updateOrder() throws Exception {
    resetTimeWithClockStep(2, MINUTES);
    TestRepository<Repo> repo = createProject("repo");
    List<ChangeInserter> inserters = new ArrayList<>();
    List<Change> changes = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inserters.add(newChange(repo));
      changes.add(insert(repo, inserters.get(i)));
    }

    for (int i : ImmutableList.of(2, 0, 1, 4, 3)) {
      gApi.changes()
          .id(changes.get(i).getId().get())
          .current()
          .review(new ReviewInput().message("modifying " + i));
    }

    assertQuery(
        "status:new",
        changes.get(3),
        changes.get(4),
        changes.get(1),
        changes.get(0),
        changes.get(2));
  }

  @Test
  public void updatedOrderWithMinuteResolution() throws Exception {
    resetTimeWithClockStep(2, MINUTES);
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo);
    Change change1 = insert(repo, ins1);
    Change change2 = insert(repo, newChange(repo));

    assertThat(lastUpdatedMs(change1)).isLessThan(lastUpdatedMs(change2));
    assertQuery("status:new", change2, change1);

    gApi.changes().id(change1.getId().get()).topic("new-topic");
    change1 = notesFactory.create(db, change1.getProject(), change1.getId()).getChange();

    assertThat(lastUpdatedMs(change1)).isGreaterThan(lastUpdatedMs(change2));
    assertThat(lastUpdatedMs(change1) - lastUpdatedMs(change2))
        .isGreaterThan(MILLISECONDS.convert(1, MINUTES));

    // change1 moved to the top.
    assertQuery("status:new", change1, change2);
  }

  @Test
  public void updatedOrderWithSubMinuteResolution() throws Exception {
    resetTimeWithClockStep(1, SECONDS);

    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo);
    Change change1 = insert(repo, ins1);
    Change change2 = insert(repo, newChange(repo));

    assertThat(lastUpdatedMs(change1)).isLessThan(lastUpdatedMs(change2));

    assertQuery("status:new", change2, change1);

    gApi.changes().id(change1.getId().get()).topic("new-topic");
    change1 = notesFactory.create(db, change1.getProject(), change1.getId()).getChange();

    assertThat(lastUpdatedMs(change1)).isGreaterThan(lastUpdatedMs(change2));
    assertThat(lastUpdatedMs(change1) - lastUpdatedMs(change2))
        .isLessThan(MILLISECONDS.convert(1, MINUTES));

    // change1 moved to the top.
    assertQuery("status:new", change1, change2);
  }

  @Test
  public void filterOutMoreThanOnePageOfResults() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    for (int i = 0; i < 5; i++) {
      insert(repo, newChange(repo), user2);
    }

    assertQuery("status:new ownerin:Administrators", change);
    assertQuery("status:new ownerin:Administrators limit:2", change);
  }

  @Test
  public void filterOutAllResults() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    for (int i = 0; i < 5; i++) {
      insert(repo, newChange(repo), user2);
    }

    assertQuery("status:new ownerin:Administrators");
    assertQuery("status:new ownerin:Administrators limit:2");
  }

  @Test
  public void byFileExact() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit =
        repo.parseBody(
            repo.commit()
                .message("one")
                .add("dir/file1", "contents1")
                .add("dir/file2", "contents2")
                .create());
    Change change = insert(repo, newChangeForCommit(repo, commit));

    assertQuery("file:file");
    assertQuery("file:dir", change);
    assertQuery("file:file1", change);
    assertQuery("file:file2", change);
    assertQuery("file:dir/file1", change);
    assertQuery("file:dir/file2", change);
  }

  @Test
  public void byFileRegex() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit =
        repo.parseBody(
            repo.commit()
                .message("one")
                .add("dir/file1", "contents1")
                .add("dir/file2", "contents2")
                .create());
    Change change = insert(repo, newChangeForCommit(repo, commit));

    assertQuery("file:.*file.*");
    assertQuery("file:^file.*"); // Whole path only.
    assertQuery("file:^dir.file.*", change);
  }

  @Test
  public void byPathExact() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit =
        repo.parseBody(
            repo.commit()
                .message("one")
                .add("dir/file1", "contents1")
                .add("dir/file2", "contents2")
                .create());
    Change change = insert(repo, newChangeForCommit(repo, commit));

    assertQuery("path:file");
    assertQuery("path:dir");
    assertQuery("path:file1");
    assertQuery("path:file2");
    assertQuery("path:dir/file1", change);
    assertQuery("path:dir/file2", change);
  }

  @Test
  public void byPathRegex() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit =
        repo.parseBody(
            repo.commit()
                .message("one")
                .add("dir/file1", "contents1")
                .add("dir/file2", "contents2")
                .create());
    Change change = insert(repo, newChangeForCommit(repo, commit));

    assertQuery("path:.*file.*");
    assertQuery("path:^dir.file.*", change);
  }

  @Test
  public void byComment() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo);
    Change change = insert(repo, ins);

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    ReviewInput.CommentInput commentInput = new ReviewInput.CommentInput();
    commentInput.line = 1;
    commentInput.message = "inline";
    input.comments =
        ImmutableMap.<String, List<ReviewInput.CommentInput>>of(
            Patch.COMMIT_MSG, ImmutableList.<ReviewInput.CommentInput>of(commentInput));
    gApi.changes().id(change.getId().get()).current().review(input);

    Map<String, List<CommentInfo>> comments =
        gApi.changes().id(change.getId().get()).current().comments();
    assertThat(comments).hasSize(1);
    CommentInfo comment = Iterables.getOnlyElement(comments.get(Patch.COMMIT_MSG));
    assertThat(comment.message).isEqualTo(commentInput.message);
    ChangeMessageInfo lastMsg =
        Iterables.getLast(gApi.changes().id(change.getId().get()).get().messages, null);
    assertThat(lastMsg.message).isEqualTo("Patch Set 1:\n\n(1 comment)\n\n" + input.message);

    assertQuery("comment:foo");
    assertQuery("comment:toplevel", change);
    assertQuery("comment:inline", change);
  }

  @Test
  public void byAge() throws Exception {
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);
    resetTimeWithClockStep(thirtyHoursInMs, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.getMillis();
    Change change1 = insert(repo, newChange(repo), null, new Timestamp(startMs));
    Change change2 = insert(repo, newChange(repo), null, new Timestamp(startMs + thirtyHoursInMs));

    // Stop time so age queries use the same endpoint.
    TestTimeUtil.setClockStep(0, MILLISECONDS);
    TestTimeUtil.setClock(new Timestamp(startMs + 2 * thirtyHoursInMs));
    long nowMs = TimeUtil.nowMs();

    assertThat(lastUpdatedMs(change2) - lastUpdatedMs(change1)).isEqualTo(thirtyHoursInMs);
    assertThat(nowMs - lastUpdatedMs(change2)).isEqualTo(thirtyHoursInMs);
    assertThat(TimeUtil.nowMs()).isEqualTo(nowMs);

    assertQuery("-age:1d");
    assertQuery("-age:" + (30 * 60 - 1) + "m");
    assertQuery("-age:2d", change2);
    assertQuery("-age:3d", change2, change1);
    assertQuery("age:3d");
    assertQuery("age:2d", change1);
    assertQuery("age:1d", change2, change1);
  }

  @Test
  public void byBeforeUntil() throws Exception {
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);
    resetTimeWithClockStep(thirtyHoursInMs, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.getMillis();
    Change change1 = insert(repo, newChange(repo), null, new Timestamp(startMs));
    Change change2 = insert(repo, newChange(repo), null, new Timestamp(startMs + thirtyHoursInMs));
    TestTimeUtil.setClockStep(0, MILLISECONDS);

    for (String predicate : new String[] {"before:", "until:"}) {
      assertQuery(predicate + "2009-09-29");
      assertQuery(predicate + "2009-09-30");
      assertQuery(predicate + "\"2009-09-30 16:59:00 -0400\"");
      assertQuery(predicate + "\"2009-09-30 20:59:00 -0000\"");
      assertQuery(predicate + "\"2009-09-30 20:59:00\"");
      assertQuery(predicate + "\"2009-09-30 17:02:00 -0400\"", change1);
      assertQuery(predicate + "\"2009-10-01 21:02:00 -0000\"", change1);
      assertQuery(predicate + "\"2009-10-01 21:02:00\"", change1);
      assertQuery(predicate + "2009-10-01", change1);
      assertQuery(predicate + "2009-10-03", change2, change1);
    }
  }

  @Test
  public void byAfterSince() throws Exception {
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);
    resetTimeWithClockStep(thirtyHoursInMs, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.getMillis();
    Change change1 = insert(repo, newChange(repo), null, new Timestamp(startMs));
    Change change2 = insert(repo, newChange(repo), null, new Timestamp(startMs + thirtyHoursInMs));
    TestTimeUtil.setClockStep(0, MILLISECONDS);

    for (String predicate : new String[] {"after:", "since:"}) {
      assertQuery(predicate + "2009-10-03");
      assertQuery(predicate + "\"2009-10-01 20:59:59 -0400\"", change2);
      assertQuery(predicate + "\"2009-10-01 20:59:59 -0000\"", change2);
      assertQuery(predicate + "2009-10-01", change2);
      assertQuery(predicate + "2009-09-30", change2, change1);
    }
  }

  @Test
  public void bySize() throws Exception {
    TestRepository<Repo> repo = createProject("repo");

    // added = 3, deleted = 0, delta = 3
    RevCommit commit1 = repo.parseBody(repo.commit().add("file1", "foo\n\foo\nfoo").create());
    // added = 0, deleted = 2, delta = 2
    RevCommit commit2 = repo.parseBody(repo.commit().parent(commit1).add("file1", "foo").create());

    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("added:>4");
    assertQuery("-added:<=4");

    assertQuery("added:3", change1);
    assertQuery("-(added:<3 OR added>3)", change1);

    assertQuery("added:>2", change1);
    assertQuery("-added:<=2", change1);

    assertQuery("added:>=3", change1);
    assertQuery("-added:<3", change1);

    assertQuery("added:<1", change2);
    assertQuery("-added:>=1", change2);

    assertQuery("added:<=0", change2);
    assertQuery("-added:>0", change2);

    assertQuery("deleted:>3");
    assertQuery("-deleted:<=3");

    assertQuery("deleted:2", change2);
    assertQuery("-(deleted:<2 OR deleted>2)", change2);

    assertQuery("deleted:>1", change2);
    assertQuery("-deleted:<=1", change2);

    assertQuery("deleted:>=2", change2);
    assertQuery("-deleted:<2", change2);

    assertQuery("deleted:<1", change1);
    assertQuery("-deleted:>=1", change1);

    assertQuery("deleted:<=0", change1);

    for (String str : Lists.newArrayList("delta", "size")) {
      assertQuery(str + ":<2");
      assertQuery(str + ":3", change1);
      assertQuery(str + ":>2", change1);
      assertQuery(str + ":>=3", change1);
      assertQuery(str + ":<3", change2);
      assertQuery(str + ":<=2", change2);
    }
  }

  private List<Change> setUpHashtagChanges() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    HashtagsInput in = new HashtagsInput();
    in.add = ImmutableSet.of("foo");
    gApi.changes().id(change1.getId().get()).setHashtags(in);

    in.add = ImmutableSet.of("foo", "bar", "a tag");
    gApi.changes().id(change2.getId().get()).setHashtags(in);

    return ImmutableList.of(change1, change2);
  }

  @Test
  public void byHashtagWithNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    List<Change> changes = setUpHashtagChanges();
    assertQuery("hashtag:foo", changes.get(1), changes.get(0));
    assertQuery("hashtag:bar", changes.get(1));
    assertQuery("hashtag:\"a tag\"", changes.get(1));
    assertQuery("hashtag:\"a tag \"", changes.get(1));
    assertQuery("hashtag:\" a tag \"", changes.get(1));
    assertQuery("hashtag:\"#a tag\"", changes.get(1));
    assertQuery("hashtag:\"# #a tag\"", changes.get(1));
  }

  @Test
  public void byHashtagWithoutNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();

    notesMigration.setWriteChanges(true);
    notesMigration.setReadChanges(true);
    db.close();
    db = schemaFactory.open();
    List<Change> changes;
    try {
      changes = setUpHashtagChanges();
      notesMigration.setWriteChanges(false);
      notesMigration.setReadChanges(false);
    } finally {
      db.close();
    }
    db = schemaFactory.open();
    for (Change c : changes) {
      indexer.index(db, c); // Reindex without hashtag field.
    }
    assertQuery("hashtag:foo");
    assertQuery("hashtag:bar");
    assertQuery("hashtag:\" bar \"");
    assertQuery("hashtag:\"a tag\"");
    assertQuery("hashtag:\" a tag \"");
    assertQuery("hashtag:#foo");
    assertQuery("hashtag:\"# #foo\"");
  }

  @Test
  public void byDefault() throws Exception {
    TestRepository<Repo> repo = createProject("repo");

    Change change1 = insert(repo, newChange(repo));

    RevCommit commit2 = repo.parseBody(repo.commit().message("foosubject").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    RevCommit commit3 = repo.parseBody(repo.commit().add("Foo.java", "foo contents").create());
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));

    ChangeInserter ins4 = newChange(repo);
    Change change4 = insert(repo, ins4);
    ReviewInput ri4 = new ReviewInput();
    ri4.message = "toplevel";
    ri4.labels = ImmutableMap.<String, Short>of("Code-Review", (short) 1);
    gApi.changes().id(change4.getId().get()).current().review(ri4);

    ChangeInserter ins5 = newChangeWithTopic(repo, "feature5");
    Change change5 = insert(repo, ins5);

    Change change6 = insert(repo, newChangeForBranch(repo, "branch6"));

    assertQuery(change1.getId().get(), change1);
    assertQuery(ChangeTriplet.format(change1), change1);
    assertQuery("foosubject", change2);
    assertQuery("Foo.java", change3);
    assertQuery("Code-Review+1", change4);
    assertQuery("toplevel", change4);
    assertQuery("feature5", change5);
    assertQuery("branch6", change6);
    assertQuery("refs/heads/branch6", change6);

    Change[] expected = new Change[] {change6, change5, change4, change3, change2, change1};
    assertQuery("user@example.com", expected);
    assertQuery("repo", expected);
  }

  @Test
  public void byDefaultWithCommitPrefix() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit = repo.parseBody(repo.commit().message("message").create());
    Change change = insert(repo, newChangeForCommit(repo, commit));

    assertQuery(commit.getId().getName().substring(0, 6), change);
  }

  @Test
  public void implicitVisibleTo() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Change change2 = insert(repo, newChangeWithStatus(repo, Change.Status.DRAFT), userId);

    String q = "project:repo";
    assertQuery(q, change2, change1);

    // Second user cannot see first user's drafts.
    requestContext.setContext(
        newRequestContext(
            accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId()));
    assertQuery(q, change1);
  }

  @Test
  public void explicitVisibleTo() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Change change2 = insert(repo, newChangeWithStatus(repo, Change.Status.DRAFT), userId);

    String q = "project:repo";
    assertQuery(q, change2, change1);

    // Second user cannot see first user's drafts.
    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    assertQuery(q + " visibleto:" + user2.get(), change1);
  }

  @Test
  public void byCommentBy() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    int user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId().get();

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = "inline";
    input.comments =
        ImmutableMap.<String, List<ReviewInput.CommentInput>>of(
            Patch.COMMIT_MSG, ImmutableList.<ReviewInput.CommentInput>of(comment));
    gApi.changes().id(change1.getId().get()).current().review(input);

    input = new ReviewInput();
    input.message = "toplevel";
    gApi.changes().id(change2.getId().get()).current().review(input);

    assertQuery("commentby:" + userId.get(), change2, change1);
    assertQuery("commentby:" + user2);
  }

  @Test
  public void byDraftBy() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    assertQuery("has:draft");

    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = Patch.COMMIT_MSG;
    gApi.changes().id(change1.getId().get()).current().createDraft(in);

    in = new DraftInput();
    in.line = 2;
    in.message = "nit: point in the end of the statement";
    in.path = Patch.COMMIT_MSG;
    gApi.changes().id(change2.getId().get()).current().createDraft(in);

    int user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId().get();

    assertQuery("has:draft", change2, change1);
    assertQuery("draftby:" + userId.get(), change2, change1);
    assertQuery("draftby:" + user2);
  }

  @Test
  public void byDraftByExcludesZombieDrafts() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    Project.NameKey project = new Project.NameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    Change change = insert(repo, newChange(repo));
    Change.Id id = change.getId();

    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = Patch.COMMIT_MSG;
    gApi.changes().id(id.get()).current().createDraft(in);

    assertQuery("draftby:" + userId, change);
    assertQuery("commentby:" + userId);

    TestRepository<Repo> allUsers = new TestRepository<>(repoManager.openRepository(allUsersName));

    Ref draftsRef = allUsers.getRepository().exactRef(RefNames.refsDraftComments(id, userId));
    assertThat(draftsRef).isNotNull();

    ReviewInput rin = ReviewInput.dislike();
    rin.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    gApi.changes().id(id.get()).current().review(rin);

    assertQuery("draftby:" + userId);
    assertQuery("commentby:" + userId, change);
    assertThat(allUsers.getRepository().exactRef(draftsRef.getName())).isNull();

    // Re-add drafts ref and ensure it gets filtered out during indexing.
    allUsers.update(draftsRef.getName(), draftsRef.getObjectId());
    assertThat(allUsers.getRepository().exactRef(draftsRef.getName())).isNotNull();

    if (PrimaryStorage.of(change) == PrimaryStorage.REVIEW_DB) {
      // Record draft ref in noteDbState as well.
      ReviewDb db = ReviewDbUtil.unwrapDb(this.db);
      change = db.changes().get(id);
      NoteDbChangeState.applyDelta(
          change,
          NoteDbChangeState.Delta.create(
              id, Optional.empty(), ImmutableMap.of(userId, draftsRef.getObjectId())));
      db.changes().update(Collections.singleton(change));
    }

    indexer.index(db, project, id);
    assertQuery("draftby:" + userId);
  }

  @Test
  public void byStarredBy() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    insert(repo, newChange(repo));

    gApi.accounts().self().starChange(change1.getId().toString());
    gApi.accounts().self().starChange(change2.getId().toString());

    int user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId().get();

    assertQuery("starredby:self", change2, change1);
    assertQuery("starredby:" + user2);
  }

  @Test
  public void byStar() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));
    insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));

    gApi.accounts()
        .self()
        .setStars(
            change1.getId().toString(),
            new StarsInput(new HashSet<>(Arrays.asList("red", "blue"))));
    gApi.accounts()
        .self()
        .setStars(
            change2.getId().toString(),
            new StarsInput(
                new HashSet<>(Arrays.asList(StarredChangesUtil.DEFAULT_LABEL, "green", "blue"))));

    // check labeled stars
    assertQuery("star:red", change1);
    assertQuery("star:blue", change2, change1);
    assertQuery("has:stars", change2, change1);

    // check default star
    assertQuery("has:star", change2);
    assertQuery("is:starred", change2);
    assertQuery("starredby:self", change2);
    assertQuery("star:" + StarredChangesUtil.DEFAULT_LABEL, change2);
  }

  @Test
  public void byFrom() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));

    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = "inline";
    input.comments =
        ImmutableMap.<String, List<ReviewInput.CommentInput>>of(
            Patch.COMMIT_MSG, ImmutableList.<ReviewInput.CommentInput>of(comment));
    gApi.changes().id(change2.getId().get()).current().review(input);

    assertQuery("from:" + userId.get(), change2, change1);
    assertQuery("from:" + user2, change2);
  }

  @Test
  public void conflicts() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 =
        repo.parseBody(
            repo.commit()
                .add("file1", "contents1")
                .add("dir/file2", "contents2")
                .add("dir/file3", "contents3")
                .create());
    RevCommit commit2 = repo.parseBody(repo.commit().add("file1", "contents1").create());
    RevCommit commit3 =
        repo.parseBody(repo.commit().add("dir/file2", "contents2 different").create());
    RevCommit commit4 = repo.parseBody(repo.commit().add("file4", "contents4").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));
    Change change4 = insert(repo, newChangeForCommit(repo, commit4));

    assertQuery("conflicts:" + change1.getId().get(), change3);
    assertQuery("conflicts:" + change2.getId().get());
    assertQuery("conflicts:" + change3.getId().get(), change1);
    assertQuery("conflicts:" + change4.getId().get());
  }

  @Test
  public void reviewedBy() throws Exception {
    resetTimeWithClockStep(2, MINUTES);
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));

    gApi.changes().id(change1.getId().get()).current().review(new ReviewInput().message("comment"));

    Account.Id user2 =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    requestContext.setContext(newRequestContext(user2));

    gApi.changes().id(change2.getId().get()).current().review(new ReviewInput().message("comment"));

    PatchSet.Id ps3_1 = change3.currentPatchSetId();
    change3 = newPatchSet(repo, change3);
    assertThat(change3.currentPatchSetId()).isNotEqualTo(ps3_1);
    // Response to previous patch set still counts as reviewing.
    gApi.changes()
        .id(change3.getId().get())
        .revision(ps3_1.get())
        .review(new ReviewInput().message("comment"));

    List<ChangeInfo> actual;
    actual = assertQuery(newQuery("is:reviewed").withOption(REVIEWED), change3, change2);
    assertThat(actual.get(0).reviewed).isTrue();
    assertThat(actual.get(1).reviewed).isTrue();

    actual = assertQuery(newQuery("-is:reviewed").withOption(REVIEWED), change1);
    assertThat(actual.get(0).reviewed).isNull();

    actual = assertQuery("reviewedby:" + userId.get());

    actual =
        assertQuery(newQuery("reviewedby:" + user2.get()).withOption(REVIEWED), change3, change2);
    assertThat(actual.get(0).reviewed).isTrue();
    assertThat(actual.get(1).reviewed).isTrue();
  }

  @Test
  public void reviewerAndCc() throws Exception {
    Account.Id user1 = createAccount("user1");
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));
    insert(repo, newChange(repo));

    AddReviewerInput rin = new AddReviewerInput();
    rin.reviewer = user1.toString();
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change1.getId().get()).addReviewer(rin);

    rin = new AddReviewerInput();
    rin.reviewer = user1.toString();
    rin.state = ReviewerState.CC;
    gApi.changes().id(change2.getId().get()).addReviewer(rin);

    assertQuery("is:reviewer");
    assertQuery("reviewer:self");
    gApi.changes().id(change3.getChangeId()).revision("current").review(ReviewInput.recommend());
    assertQuery("is:reviewer", change3);
    assertQuery("reviewer:self", change3);

    if (notesMigration.readChanges()) {
      assertQuery("reviewer:" + user1, change1);
      assertQuery("cc:" + user1, change2);
    } else {
      assertQuery("reviewer:" + user1, change2, change1);
      assertQuery("cc:" + user1);
    }
  }

  @Test
  public void byReviewed() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherUser =
        accountManager.authenticate(AuthRequest.forUser("anotheruser")).getAccountId();
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    assertQuery("is:reviewed");
    assertQuery("status:reviewed");
    assertQuery("-is:reviewed", change2, change1);
    assertQuery("-status:reviewed", change2, change1);

    requestContext.setContext(newRequestContext(otherUser));
    gApi.changes().id(change1.getChangeId()).current().review(ReviewInput.recommend());

    assertQuery("is:reviewed", change1);
    assertQuery("status:reviewed", change1);
    assertQuery("-is:reviewed", change2);
    assertQuery("-status:reviewed", change2);
  }

  @Test
  public void reviewerin() throws Exception {
    Account.Id user1 = accountManager.authenticate(AuthRequest.forUser("user1")).getAccountId();
    Account.Id user2 = accountManager.authenticate(AuthRequest.forUser("user2")).getAccountId();
    TestRepository<Repo> repo = createProject("repo");

    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    insert(repo, newChange(repo));

    AddReviewerInput rin = new AddReviewerInput();
    rin.reviewer = user1.toString();
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change1.getId().get()).addReviewer(rin);

    rin = new AddReviewerInput();
    rin.reviewer = user2.toString();
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change2.getId().get()).addReviewer(rin);

    String group = gApi.groups().create("foo").get().name;
    gApi.groups().id(group).addMembers(user2.toString());

    List<String> members =
        gApi.groups()
            .id(group)
            .members()
            .stream()
            .map(a -> a._accountId.toString())
            .collect(toList());
    assertThat(members).contains(user2.toString());

    assertQuery("reviewerin:\"Registered Users\"", change2, change1);
    assertQuery("reviewerin:" + group, change2);

    gApi.changes().id(change2.getId().get()).current().review(ReviewInput.approve());
    gApi.changes().id(change2.getId().get()).current().submit();

    assertQuery("reviewerin:" + group);
    assertQuery("status:merged reviewerin:" + group, change2);
  }

  @Test
  public void submitRecords() throws Exception {
    Account.Id user1 = createAccount("user1");
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    gApi.changes().id(change1.getId().get()).current().review(ReviewInput.approve());
    requestContext.setContext(newRequestContext(user1));
    gApi.changes().id(change2.getId().get()).current().review(ReviewInput.recommend());
    requestContext.setContext(newRequestContext(user.getAccountId()));

    assertQuery("is:submittable", change1);
    assertQuery("-is:submittable", change2);
    assertQuery("submittable:ok", change1);
    assertQuery("submittable:not_ready", change2);

    assertQuery("label:CodE-RevieW=ok", change1);
    assertQuery("label:CodE-RevieW=ok,user=user", change1);
    assertQuery("label:CodE-RevieW=ok,Administrators", change1);
    assertQuery("label:CodE-RevieW=ok,group=Administrators", change1);
    assertQuery("label:CodE-RevieW=ok,owner", change1);
    assertQuery("label:CodE-RevieW=ok,user1");
    assertQuery("label:CodE-RevieW=need", change2);
    // NEED records don't have associated users.
    assertQuery("label:CodE-RevieW=need,user1");
    assertQuery("label:CodE-RevieW=need,user");
  }

  @Test
  public void hasEdit() throws Exception {
    Account.Id user1 = createAccount("user1");
    Account.Id user2 = createAccount("user2");
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    String changeId1 = change1.getKey().get();
    Change change2 = insert(repo, newChange(repo));
    String changeId2 = change2.getKey().get();

    requestContext.setContext(newRequestContext(user1));
    assertQuery("has:edit");
    gApi.changes().id(changeId1).edit().create();
    gApi.changes().id(changeId2).edit().create();

    requestContext.setContext(newRequestContext(user2));
    assertQuery("has:edit");
    gApi.changes().id(changeId2).edit().create();

    requestContext.setContext(newRequestContext(user1));
    assertQuery("has:edit", change2, change1);

    requestContext.setContext(newRequestContext(user2));
    assertQuery("has:edit", change2);
  }

  @Test
  public void byUnresolved() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));

    // Change1 has one resolved comment (unresolvedcount = 0)
    // Change2 has one unresolved comment (unresolvedcount = 1)
    // Change3 has one resolved comment and one unresolved comment (unresolvedcount = 1)
    addComment(change1.getChangeId(), "comment 1", false);
    addComment(change2.getChangeId(), "comment 2", true);
    addComment(change3.getChangeId(), "comment 3", false);
    addComment(change3.getChangeId(), "comment 4", true);

    assertQuery("has:unresolved", change3, change2);

    assertQuery("unresolved:0", change1);
    List<ChangeInfo> changeInfos = assertQuery("unresolved:>=0", change3, change2, change1);
    assertThat(changeInfos.get(0).unresolvedCommentCount).isEqualTo(1); // Change3
    assertThat(changeInfos.get(1).unresolvedCommentCount).isEqualTo(1); // Change2
    assertThat(changeInfos.get(2).unresolvedCommentCount).isEqualTo(0); // Change1
    assertQuery("unresolved:>0", change3, change2);

    assertQuery("unresolved:<1", change1);
    assertQuery("unresolved:<=1", change3, change2, change1);
    assertQuery("unresolved:1", change3, change2);
    assertQuery("unresolved:>1");
    assertQuery("unresolved:>=1", change3, change2);
  }

  @Test
  public void byCommitsOnBranchNotMerged() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    int n = 10;
    List<String> shas = new ArrayList<>(n);
    List<Integer> expectedIds = new ArrayList<>(n);
    Branch.NameKey dest = null;
    for (int i = 0; i < n; i++) {
      ChangeInserter ins = newChange(repo);
      insert(repo, ins);
      if (dest == null) {
        dest = ins.getChange().getDest();
      }
      shas.add(ins.getCommit().name());
      expectedIds.add(ins.getChange().getId().get());
    }

    for (int i = 1; i <= 11; i++) {
      Iterable<ChangeData> cds =
          internalChangeQuery.byCommitsOnBranchNotMerged(repo.getRepository(), db, dest, shas, i);
      Iterable<Integer> ids = FluentIterable.from(cds).transform(in -> in.getId().get());
      String name = "limit " + i;
      assertThat(ids).named(name).hasSize(n);
      assertThat(ids).named(name).containsExactlyElementsIn(expectedIds);
    }
  }

  @Test
  public void prepopulatedFields() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));

    db = new DisabledReviewDb();
    requestContext.setContext(newRequestContext(userId));
    // Use QueryProcessor directly instead of API so we get ChangeDatas back.
    List<ChangeData> cds =
        queryProcessor.query(queryBuilder.parse(change.getId().toString())).entities();
    assertThat(cds).hasSize(1);

    ChangeData cd = cds.get(0);
    cd.change();
    cd.patchSets();
    cd.currentApprovals();
    cd.changedLines();
    cd.reviewedBy();
    cd.reviewers();
    cd.unresolvedCommentCount();

    // TODO(dborowitz): Swap out GitRepositoryManager somehow? Will probably be
    // necessary for NoteDb anyway.
    cd.isMergeable();

    exception.expect(DisabledReviewDb.Disabled.class);
    cd.messages();
  }

  @Test
  public void prepopulateOnlyRequestedFields() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));

    db = new DisabledReviewDb();
    requestContext.setContext(newRequestContext(userId));
    // Use QueryProcessor directly instead of API so we get ChangeDatas back.
    List<ChangeData> cds =
        queryProcessor
            .setRequestedFields(
                ImmutableSet.of(ChangeField.PATCH_SET.getName(), ChangeField.CHANGE.getName()))
            .query(queryBuilder.parse(change.getId().toString()))
            .entities();
    assertThat(cds).hasSize(1);

    ChangeData cd = cds.get(0);
    cd.change();
    cd.patchSets();

    exception.expect(DisabledReviewDb.Disabled.class);
    cd.currentApprovals();
  }

  @Test
  public void reindexIfStale() throws Exception {
    Account.Id user = createAccount("user");
    Project.NameKey project = new Project.NameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    Change change = insert(repo, newChange(repo));
    String changeId = change.getKey().get();
    ChangeNotes notes = notesFactory.create(db, change.getProject(), change.getId());
    PatchSet ps = psUtil.get(db, notes, change.currentPatchSetId());

    requestContext.setContext(newRequestContext(user));
    gApi.changes().id(changeId).edit().create();
    assertQuery("has:edit", change);
    assertThat(indexer.reindexIfStale(project, change.getId()).get()).isFalse();

    // Delete edit ref behind index's back.
    RefUpdate ru =
        repo.getRepository().updateRef(RefNames.refsEdit(user, change.getId(), ps.getId()));
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);

    // Index is stale.
    assertQuery("has:edit", change);
    assertThat(indexer.reindexIfStale(project, change.getId()).get()).isTrue();
    assertQuery("has:edit");
  }

  @Test
  public void refStateFields() throws Exception {
    // This test method manages primary storage manually.
    assume().that(notesMigration.changePrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
    Account.Id user = createAccount("user");
    Project.NameKey project = new Project.NameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    String path = "file";
    RevCommit commit = repo.parseBody(repo.commit().message("one").add(path, "contents").create());
    Change change = insert(repo, newChangeForCommit(repo, commit));
    Change.Id id = change.getId();
    int c = id.get();
    String changeId = change.getKey().get();
    requestContext.setContext(newRequestContext(user));

    // Ensure one of each type of supported ref is present for the change. If
    // any more refs are added, update this test to reflect them.

    // Edit
    gApi.changes().id(changeId).edit().create();

    // Star
    gApi.accounts().self().starChange(change.getId().toString());

    if (notesMigration.readChanges()) {
      // Robot comment.
      ReviewInput rin = new ReviewInput();
      RobotCommentInput rcin = new RobotCommentInput();
      rcin.robotId = "happyRobot";
      rcin.robotRunId = "1";
      rcin.line = 1;
      rcin.message = "nit: trailing whitespace";
      rcin.path = path;
      rin.robotComments = ImmutableMap.of(path, ImmutableList.of(rcin));
      gApi.changes().id(c).current().review(rin);
    }

    // Draft.
    DraftInput din = new DraftInput();
    din.path = path;
    din.line = 1;
    din.message = "draft";
    gApi.changes().id(c).current().createDraft(din);

    if (notesMigration.readChanges()) {
      // Force NoteDb primary.
      change = ReviewDbUtil.unwrapDb(db).changes().get(id);
      change.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
      ReviewDbUtil.unwrapDb(db).changes().update(Collections.singleton(change));
      indexer.index(db, change);
    }

    QueryOptions opts =
        IndexedChangeQuery.createOptions(indexConfig, 0, 1, StalenessChecker.FIELDS);
    ChangeData cd = indexes.getSearchIndex().get(id, opts).get();

    String cs = RefNames.shard(c);
    int u = user.get();
    String us = RefNames.shard(u);

    List<String> expectedStates =
        Lists.newArrayList(
            "repo:refs/users/" + us + "/edit-" + c + "/1",
            "All-Users:refs/starred-changes/" + cs + "/" + u);
    if (notesMigration.readChanges()) {
      expectedStates.add("repo:refs/changes/" + cs + "/meta");
      expectedStates.add("repo:refs/changes/" + cs + "/robot-comments");
      expectedStates.add("All-Users:refs/draft-comments/" + cs + "/" + u);
    }
    assertThat(
            cd.getRefStates()
                .stream()
                .map(String::new)
                // Omit SHA-1, we're just concerned with the project/ref names.
                .map(s -> s.substring(0, s.lastIndexOf(':')))
                .collect(toList()))
        .containsExactlyElementsIn(expectedStates);

    List<String> expectedPatterns = Lists.newArrayList("repo:refs/users/*/edit-" + c + "/*");
    expectedPatterns.add("All-Users:refs/starred-changes/" + cs + "/*");
    if (notesMigration.readChanges()) {
      expectedPatterns.add("All-Users:refs/draft-comments/" + cs + "/*");
    }
    assertThat(cd.getRefStatePatterns().stream().map(String::new).collect(toList()))
        .containsExactlyElementsIn(expectedPatterns);
  }

  @Test
  public void watched() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.NEW);
    Change change1 = insert(repo, ins1);

    TestRepository<Repo> repo2 = createProject("repo2");

    ChangeInserter ins2 = newChangeWithStatus(repo2, Change.Status.NEW);
    insert(repo2, ins2);

    assertQuery("is:watched");
    assertQuery("watchedby:self");

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = "repo";
    pwi.filter = null;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    assertQuery("is:watched", change1);
    assertQuery("watchedby:self", change1);
  }

  @Test
  public void trackingid() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 =
        repo.parseBody(repo.commit().message("Change one\n\nBug:QUERY123").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 =
        repo.parseBody(repo.commit().message("Change two\n\nFeature:QUERY456").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("tr:QUERY123", change1);
    assertQuery("bug:QUERY123", change1);
    assertQuery("tr:QUERY456", change2);
    assertQuery("bug:QUERY456", change2);
    assertQuery("tr:QUERY-123");
    assertQuery("bug:QUERY-123");
    assertQuery("tr:QUERY12");
    assertQuery("bug:QUERY12");
    assertQuery("tr:QUERY789");
    assertQuery("bug:QUERY789");
  }

  @Test
  public void assignee() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    AssigneeInput input = new AssigneeInput();
    input.assignee = user.getUserName();
    gApi.changes().id(change1.getChangeId()).setAssignee(input);

    assertQuery("is:assigned", change1);
    assertQuery("-is:assigned", change2);
    assertQuery("is:unassigned", change2);
    assertQuery("-is:unassigned", change1);
    assertQuery("assignee:" + user.getUserName(), change1);
    assertQuery("-assignee:" + user.getUserName(), change2);
  }

  protected ChangeInserter newChange(TestRepository<Repo> repo) throws Exception {
    return newChange(repo, null, null, null, null);
  }

  protected ChangeInserter newChangeForCommit(TestRepository<Repo> repo, RevCommit commit)
      throws Exception {
    return newChange(repo, commit, null, null, null);
  }

  protected ChangeInserter newChangeForBranch(TestRepository<Repo> repo, String branch)
      throws Exception {
    return newChange(repo, null, branch, null, null);
  }

  protected ChangeInserter newChangeWithStatus(TestRepository<Repo> repo, Change.Status status)
      throws Exception {
    return newChange(repo, null, null, status, null);
  }

  protected ChangeInserter newChangeWithTopic(TestRepository<Repo> repo, String topic)
      throws Exception {
    return newChange(repo, null, null, null, topic);
  }

  protected ChangeInserter newChange(
      TestRepository<Repo> repo,
      @Nullable RevCommit commit,
      @Nullable String branch,
      @Nullable Change.Status status,
      @Nullable String topic)
      throws Exception {
    if (commit == null) {
      commit = repo.parseBody(repo.commit().message("message").create());
    }

    branch = MoreObjects.firstNonNull(branch, "refs/heads/master");
    if (!branch.startsWith("refs/heads/")) {
      branch = "refs/heads/" + branch;
    }

    Change.Id id = new Change.Id(seq.nextChangeId());
    ChangeInserter ins =
        changeFactory
            .create(id, commit, branch)
            .setValidatePolicy(CommitValidators.Policy.NONE)
            .setStatus(status)
            .setTopic(topic);
    return ins;
  }

  protected Change insert(TestRepository<Repo> repo, ChangeInserter ins) throws Exception {
    return insert(repo, ins, null, TimeUtil.nowTs());
  }

  protected Change insert(TestRepository<Repo> repo, ChangeInserter ins, @Nullable Account.Id owner)
      throws Exception {
    return insert(repo, ins, owner, TimeUtil.nowTs());
  }

  protected Change insert(
      TestRepository<Repo> repo,
      ChangeInserter ins,
      @Nullable Account.Id owner,
      Timestamp createdOn)
      throws Exception {
    Project.NameKey project =
        new Project.NameKey(repo.getRepository().getDescription().getRepositoryName());
    Account.Id ownerId = owner != null ? owner : userId;
    IdentifiedUser user = userFactory.create(ownerId);
    try (BatchUpdate bu = updateFactory.create(db, project, user, createdOn)) {
      bu.insertChange(ins);
      bu.execute();
      return ins.getChange();
    }
  }

  protected Change newPatchSet(TestRepository<Repo> repo, Change c) throws Exception {
    // Add a new file so the patch set is not a trivial rebase, to avoid default
    // Code-Review label copying.
    int n = c.currentPatchSetId().get() + 1;
    RevCommit commit =
        repo.parseBody(repo.commit().message("message").add("file" + n, "contents " + n).create());
    ChangeControl ctl = changeControlFactory.controlFor(db, c, user);

    PatchSetInserter inserter =
        patchSetFactory
            .create(ctl, new PatchSet.Id(c.getId(), n), commit)
            .setNotify(NotifyHandling.NONE)
            .setFireRevisionCreated(false)
            .setValidatePolicy(CommitValidators.Policy.NONE);
    try (BatchUpdate bu = updateFactory.create(db, c.getProject(), user, TimeUtil.nowTs());
        ObjectInserter oi = repo.getRepository().newObjectInserter()) {
      bu.setRepository(repo.getRepository(), repo.getRevWalk(), oi);
      bu.addOp(c.getId(), inserter);
      bu.execute();
    }

    return inserter.getChange();
  }

  protected ThrowableSubject assertThatQueryException(Object query) throws Exception {
    return assertThatQueryException(newQuery(query));
  }

  protected ThrowableSubject assertThatQueryException(QueryRequest query) throws Exception {
    try {
      query.get();
      throw new AssertionError("expected BadRequestException for query: " + query);
    } catch (BadRequestException e) {
      return assertThat(e);
    }
  }

  protected TestRepository<Repo> createProject(String name) throws Exception {
    gApi.projects().create(name).get();
    return new TestRepository<>(repoManager.openRepository(new Project.NameKey(name)));
  }

  protected TestRepository<Repo> createProject(String name, String parent) throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = name;
    input.parent = parent;
    gApi.projects().create(input).get();
    return new TestRepository<>(repoManager.openRepository(new Project.NameKey(name)));
  }

  protected QueryRequest newQuery(Object query) {
    return gApi.changes().query(query.toString());
  }

  protected List<ChangeInfo> assertQuery(Object query, Change... changes) throws Exception {
    return assertQuery(newQuery(query), changes);
  }

  protected List<ChangeInfo> assertQuery(QueryRequest query, Change... changes) throws Exception {
    List<ChangeInfo> result = query.get();
    Iterable<Integer> ids = ids(result);
    assertThat(ids)
        .named(format(query, ids, changes))
        .containsExactlyElementsIn(ids(changes))
        .inOrder();
    return result;
  }

  private String format(QueryRequest query, Iterable<Integer> actualIds, Change... expectedChanges)
      throws RestApiException {
    StringBuilder b = new StringBuilder();
    b.append("query '").append(query.getQuery()).append("' with expected changes ");
    b.append(format(Arrays.stream(expectedChanges).map(Change::getChangeId).iterator()));
    b.append(" and result ");
    b.append(format(actualIds));
    return b.toString();
  }

  private String format(Iterable<Integer> changeIds) throws RestApiException {
    return format(changeIds.iterator());
  }

  private String format(Iterator<Integer> changeIds) throws RestApiException {
    StringBuilder b = new StringBuilder();
    b.append("[");
    while (changeIds.hasNext()) {
      int id = changeIds.next();
      ChangeInfo c = gApi.changes().id(id).get();
      b.append("{")
          .append(id)
          .append(" (")
          .append(c.changeId)
          .append("), ")
          .append("dest=")
          .append(new Branch.NameKey(new Project.NameKey(c.project), c.branch))
          .append(", ")
          .append("status=")
          .append(c.status)
          .append(", ")
          .append("lastUpdated=")
          .append(c.updated.getTime())
          .append("}");
      if (changeIds.hasNext()) {
        b.append(", ");
      }
    }
    b.append("]");
    return b.toString();
  }

  protected static Iterable<Integer> ids(Change... changes) {
    return FluentIterable.from(Arrays.asList(changes)).transform(in -> in.getId().get());
  }

  protected static Iterable<Integer> ids(Iterable<ChangeInfo> changes) {
    return FluentIterable.from(changes).transform(in -> in._number);
  }

  protected static long lastUpdatedMs(Change c) {
    return c.getLastUpdatedOn().getTime();
  }

  private void addComment(int changeId, String message, Boolean unresolved) throws Exception {
    ReviewInput input = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = message;
    comment.unresolved = unresolved;
    input.comments =
        ImmutableMap.<String, List<ReviewInput.CommentInput>>of(
            Patch.COMMIT_MSG, ImmutableList.<ReviewInput.CommentInput>of(comment));
    gApi.changes().id(changeId).current().review(input);
  }
}
