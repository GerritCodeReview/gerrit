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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.REVIEWED;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.base.MoreObjects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Streams;
import com.google.common.truth.ThrowableSubject;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.FakeSubmitRule;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.Changes.QueryRequest;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.httpd.raw.IndexPreloadingUtil;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.account.VersionedAccountQueries;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.testing.TestGroupBackend;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritServerTests;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractQueryChangesTest extends GerritServerTests {
  @Inject protected Accounts accounts;
  @Inject protected AccountCache accountCache;
  @Inject @ServerInitiated protected Provider<AccountsUpdate> accountsUpdate;
  @Inject protected AccountManager accountManager;
  @Inject protected AllUsersName allUsersName;
  @Inject protected BatchUpdate.Factory updateFactory;
  @Inject protected AllProjectsName allProjectsName;
  @Inject protected ChangeInserter.Factory changeFactory;
  @Inject protected ChangeQueryBuilder queryBuilder;
  @Inject protected GerritApi gApi;
  @Inject protected IdentifiedUser.GenericFactory userFactory;
  @Inject protected ChangeIndexCollection indexes;
  @Inject protected ChangeIndexer indexer;
  @Inject protected ExtensionRegistry extensionRegistry;
  @Inject protected IndexConfig indexConfig;
  @Inject protected InMemoryRepositoryManager repoManager;
  @Inject protected Provider<AnonymousUser> anonymousUserProvider;
  @Inject protected Provider<InternalChangeQuery> queryProvider;
  @Inject protected ChangeNotes.Factory notesFactory;
  @Inject protected OneOffRequestContext oneOffRequestContext;
  @Inject protected PatchSetInserter.Factory patchSetFactory;
  @Inject protected PatchSetUtil psUtil;
  @Inject protected ChangeNotes.Factory changeNotesFactory;
  @Inject protected Provider<ChangeQueryProcessor> queryProcessorProvider;
  @Inject protected SchemaCreator schemaCreator;
  @Inject protected Sequences seq;
  @Inject protected ThreadLocalRequestContext requestContext;
  @Inject protected TestGroupBackend testGroupBackend;
  @Inject protected ProjectCache projectCache;
  @Inject protected MetaDataUpdate.Server metaDataUpdateFactory;
  @Inject protected AuthRequest.Factory authRequestFactory;
  @Inject protected ExternalIdFactory externalIdFactory;
  @Inject protected ProjectOperations projectOperations;
  @Inject protected GroupOperations groupOperations;

  @Inject private ProjectConfig.Factory projectConfigFactory;

  protected Injector injector;
  protected LifecycleManager lifecycle;
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
    initAfterLifecycleStart();
    setUpDatabase();
  }

  @After
  public void cleanUp() {
    lifecycle.stop();
  }

  protected void initAfterLifecycleStart() throws Exception {}

  protected void setUpDatabase() throws Exception {
    schemaCreator.create();

    userId = accountManager.authenticate(authRequestFactory.createForUser("user")).getAccountId();
    String email = "user@example.com";
    accountsUpdate
        .get()
        .update(
            "Add Email",
            userId,
            u ->
                u.addExternalId(externalIdFactory.createEmail(userId, email))
                    .setPreferredEmail(email));
    resetUser();
  }

  protected RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser = userFactory.create(requestUserId);
    return () -> requestUser;
  }

  protected void resetUser() {
    user = userFactory.create(userId);
    requestContext.setContext(newRequestContext(userId));
  }

  @After
  public void tearDownInjector() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
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
    if (systemTimeZone != null) {
      System.setProperty("user.timezone", systemTimeZone);
      systemTimeZone = null;
    }
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
    Exception thrown = assertThrows(BadRequestException.class, () -> assertQuery("is:draft"));
    assertThat(thrown).hasMessageThat().isEqualTo("Unrecognized value: draft");
    thrown = assertThrows(BadRequestException.class, () -> assertQuery("status:draft"));
    assertThat(thrown).hasMessageThat().isEqualTo("Unrecognized value: draft");
  }

  @Test
  public void byStatusOr() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.NEW);
    Change change1 = insert(repo, ins1);
    ChangeInserter ins2 = newChangeWithStatus(repo, Change.Status.MERGED);
    Change change2 = insert(repo, ins2);

    assertQuery("status:new OR status:merged", change2, change1);
    assertQuery("status:new or status:merged", change2, change1);
  }

  @Test
  public void byStatusOpen() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChangeWithStatus(repo, Change.Status.NEW);
    Change change1 = insert(repo, ins1);
    insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));

    Change[] expected = new Change[] {change1};
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
    Exception thrown = assertThrows(BadRequestException.class, () -> assertQuery("status:newx"));
    assertThat(thrown).hasMessageThat().isEqualTo("Unrecognized value: newx");
    thrown = assertThrows(BadRequestException.class, () -> assertQuery("status:nx"));
    assertThat(thrown).hasMessageThat().isEqualTo("Unrecognized value: nx");
  }

  @Test
  public void byPrivate() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);

    // No private changes.
    assertQuery("is:open", change2, change1);
    assertQuery("is:private");

    gApi.changes().id(change1.getChangeId()).setPrivate(true, null);

    // Change1 is private, but should be still visible to its owner.
    assertQuery("is:open", change1, change2);
    assertQuery("is:private", change1);

    // Switch request context to user2.
    requestContext.setContext(newRequestContext(user2));
    assertQuery("is:open", change2);
    assertQuery("is:private");
  }

  @Test
  public void byWip() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);

    assertQuery("is:open", change1);
    assertQuery("is:wip");

    gApi.changes().id(change1.getChangeId()).setWorkInProgress();

    assertQuery("is:wip", change1);

    gApi.changes().id(change1.getChangeId()).setReadyForReview();

    assertQuery("is:wip");
  }

  @Test
  public void excludeWipChangeFromReviewersDashboards() throws Exception {
    Account.Id user1 = createAccount("user1");
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWorkInProgress(repo), userId);

    assertQuery("is:wip", change1);
    assertQuery("reviewer:" + user1);

    gApi.changes().id(change1.getChangeId()).setReadyForReview();
    assertQuery("is:wip");
    assertQuery("reviewer:" + user1);

    gApi.changes().id(change1.getChangeId()).setWorkInProgress();
    assertQuery("is:wip", change1);
    assertQuery("reviewer:" + user1);
  }

  @Test
  public void byStarted() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWorkInProgress(repo));

    assertQuery("is:started");

    gApi.changes().id(change1.getChangeId()).setReadyForReview();
    assertQuery("is:started", change1);

    gApi.changes().id(change1.getChangeId()).setWorkInProgress();
    assertQuery("is:started", change1);
  }

  private void assertReviewers(Collection<AccountInfo> reviewers, Object... expected)
      throws Exception {
    if (expected.length == 0) {
      assertThat(reviewers).isNull();
      return;
    }

    // Convert AccountInfos to strings, either account ID or email.
    List<String> reviewerIds =
        reviewers.stream()
            .map(
                ai -> {
                  if (ai._accountId != null) {
                    return ai._accountId.toString();
                  }
                  return ai.email;
                })
            .collect(toList());
    assertThat(reviewerIds).containsExactly(expected);
  }

  @Test
  public void restorePendingReviewers() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);
    Change change1 = insert(repo, newChangeWorkInProgress(repo));
    Account.Id user1 = createAccount("user1");
    Account.Id user2 = createAccount("user2");
    String email1 = "email1@example.com";
    String email2 = "email2@example.com";

    ReviewInput in =
        ReviewInput.noScore()
            .reviewer(user1.toString())
            .reviewer(user2.toString(), ReviewerState.CC, false)
            .reviewer(email1)
            .reviewer(email2, ReviewerState.CC, false);
    gApi.changes().id(change1.getId().get()).current().review(in);

    List<ChangeInfo> changeInfos =
        assertQuery(newQuery("is:wip").withOption(DETAILED_LABELS), change1);
    assertThat(changeInfos).isNotEmpty();

    Map<ReviewerState, Collection<AccountInfo>> pendingReviewers =
        changeInfos.get(0).pendingReviewers;
    assertThat(pendingReviewers).isNotNull();

    assertReviewers(pendingReviewers.get(ReviewerState.REVIEWER), user1.toString(), email1);
    assertReviewers(pendingReviewers.get(ReviewerState.CC), user2.toString(), email2);
    assertReviewers(pendingReviewers.get(ReviewerState.REMOVED));

    // Pending reviewers may also be presented in the REMOVED state. Toggle the
    // change to ready and then back to WIP and remove reviewers to produce.
    assertThat(pendingReviewers.get(ReviewerState.REMOVED)).isNull();
    gApi.changes().id(change1.getId().get()).setReadyForReview();
    gApi.changes().id(change1.getId().get()).setWorkInProgress();
    gApi.changes().id(change1.getId().get()).reviewer(user1.toString()).remove();
    gApi.changes().id(change1.getId().get()).reviewer(user2.toString()).remove();
    gApi.changes().id(change1.getId().get()).reviewer(email1).remove();
    gApi.changes().id(change1.getId().get()).reviewer(email2).remove();

    changeInfos = assertQuery(newQuery("is:wip").withOption(DETAILED_LABELS), change1);
    assertThat(changeInfos).isNotEmpty();

    pendingReviewers = changeInfos.get(0).pendingReviewers;
    assertThat(pendingReviewers).isNotNull();
    assertReviewers(pendingReviewers.get(ReviewerState.REVIEWER));
    assertReviewers(pendingReviewers.get(ReviewerState.CC));
    assertReviewers(
        pendingReviewers.get(ReviewerState.REMOVED),
        user1.toString(),
        user2.toString(),
        email1,
        email2);
  }

  @Test
  public void byCommit() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo);
    Change change = insert(repo, ins);
    String sha = ins.getCommitId().name();

    assertQuery("0000000000000000000000000000000000000000");
    assertQuery("commit:0000000000000000000000000000000000000000");
    for (int i = 0; i <= 36; i++) {
      String q = sha.substring(0, 40 - i);
      assertQuery(q, change);
      assertQuery("commit:" + q, change);
    }
  }

  @Test
  public void byOwner() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);

    assertQuery("is:owner", change1);
    assertQuery("owner:" + userId.get(), change1);
    assertQuery("owner:" + user2, change2);

    String nameEmail = user.asIdentifiedUser().getNameEmail();
    assertQuery("owner: \"" + nameEmail + "\"", change1);
  }

  @Test
  public void byUploader() throws Exception {
    assume().that(getSchema().hasField(ChangeField.UPLOADER)).isTrue();
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    CurrentUser user2CurrentUser = userFactory.create(user2);

    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    assertQuery("is:uploader", change1);
    assertQuery("uploader:" + userId.get(), change1);
    change1 = newPatchSet(repo, change1, user2CurrentUser);
    // Uploader has changed
    assertQuery("uploader:" + userId.get());
    assertQuery("uploader:" + user2.get(), change1);

    requestContext.setContext(newRequestContext(user2));
    assertQuery("is:uploader", change1); // self (user2)

    String nameEmail = user2CurrentUser.asIdentifiedUser().getNameEmail();
    assertQuery("uploader: \"" + nameEmail + "\"", change1);
  }

  @Test
  public void byAuthorExact() throws Exception {
    byAuthorOrCommitterExact("author:");
  }

  @Test
  public void byAuthorFullText() throws Exception {
    byAuthorOrCommitterFullText("author:");
  }

  @Test
  public void byCommitterExact() throws Exception {
    byAuthorOrCommitterExact("committer:");
  }

  @Test
  public void byCommitterFullText() throws Exception {
    byAuthorOrCommitterFullText("committer:");
  }

  private void byAuthorOrCommitterExact(String searchOperator) throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    PersonIdent johnDoe = new PersonIdent("John Doe", "john.doe@example.com");
    PersonIdent john = new PersonIdent("John", "john@example.com");
    PersonIdent doeSmith = new PersonIdent("Doe Smith", "doe_smith@example.com");
    Account ua = user.asIdentifiedUser().getAccount();
    PersonIdent myself = new PersonIdent("I Am", ua.preferredEmail());
    PersonIdent selfName = new PersonIdent("My Self", "my.self@example.com");

    Change change1 = createChange(repo, johnDoe);
    Change change2 = createChange(repo, john);
    Change change3 = createChange(repo, doeSmith);
    createChange(repo, selfName);

    // Only email address.
    assertQuery(searchOperator + "john.doe@example.com", change1);
    assertQuery(searchOperator + "john@example.com", change2);
    assertQuery(searchOperator + "Doe_SmIth@example.com", change3); // Case insensitive.

    // Right combination of email address and name.
    assertQuery(searchOperator + "\"John Doe <john.doe@example.com>\"", change1);
    assertQuery(searchOperator + "\" John <john@example.com> \"", change2);
    assertQuery(searchOperator + "\"doE SMITH <doe_smitH@example.com>\"", change3);

    // Wrong combination of email address and name.
    assertQuery(searchOperator + "\"John <john.doe@example.com>\"");
    assertQuery(searchOperator + "\"Doe John <john@example.com>\"");
    assertQuery(searchOperator + "\"Doe John <doe_smith@example.com>\"");

    // Partial name
    assertQuery(searchOperator + "ohn");
    assertQuery(searchOperator + "smith", change3);

    // The string 'self' in the name should not be matched
    assertQuery(searchOperator + "self");

    // ':self' matches a change created with the current user's email address
    Change change5 = createChange(repo, myself);
    assertQuery(searchOperator + "me", change5);
    assertQuery(searchOperator + "self", change5);
  }

  private void byAuthorOrCommitterFullText(String searchOperator) throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    PersonIdent johnDoe = new PersonIdent("John Doe", "john.doe@example.com");
    PersonIdent john = new PersonIdent("John", "john@example.com");
    PersonIdent doeSmith = new PersonIdent("Doe Smith", "doe_smith@example.com");
    Change change1 = createChange(repo, johnDoe);
    Change change2 = createChange(repo, john);
    Change change3 = createChange(repo, doeSmith);

    // By exact name.
    assertQuery(searchOperator + "\"John Doe\"", change1);
    assertQuery(searchOperator + "\"john\"", change2, change1);
    assertQuery(searchOperator + "\"Doe smith\"", change3);

    // By name part.
    assertQuery(searchOperator + "Doe", change3, change1);
    assertQuery(searchOperator + "smith", change3);

    // By wrong combination.
    assertQuery(searchOperator + "\"John Smith\"");

    // By invalid query.
    // SchemaUtil.getNameParts will return an empty set for query only containing these characters.
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> assertQuery(searchOperator + "@.- /_"));
    assertThat(thrown).hasMessageThat().contains("invalid value");
  }

  protected Change createChange(TestRepository<Repo> repo, PersonIdent person) throws Exception {
    RevCommit commit =
        repo.parseBody(repo.commit().message("message").author(person).committer(person).create());
    return insert(repo, newChangeForCommit(repo, commit), null);
  }

  @Test
  public void byOwnerIn() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);
    Change change3 = insert(repo, newChange(repo), user2);
    gApi.changes().id(change3.getId().get()).current().review(ReviewInput.approve());
    gApi.changes().id(change3.getId().get()).current().submit();

    assertQuery("ownerin:Administrators", change1);
    assertQuery("ownerin:\"Registered Users\"", change3, change2, change1);
    assertQuery("ownerin:\"Registered Users\" project:repo", change3, change2, change1);
    assertQuery("ownerin:\"Registered Users\" status:merged", change3);

    GroupDescription.Basic externalGroup = testGroupBackend.create("External Group");
    try {
      testGroupBackend.setMembershipsOf(
          user2, new ListGroupMembership(ImmutableList.of(externalGroup.getGroupUUID())));

      assertQuery("ownerin:\"" + "testbackend:" + externalGroup.getName() + "\"", change3, change2);

      String nameOfGroupThatContainsExternalGroupAsSubgroup = "test-group-1";
      AccountGroup.UUID uuidOfGroupThatContainsExternalGroupAsSubgroup =
          groupOperations
              .newGroup()
              .name(nameOfGroupThatContainsExternalGroupAsSubgroup)
              .addSubgroup(externalGroup.getGroupUUID())
              .create();
      assertQuery(
          "ownerin:\"" + nameOfGroupThatContainsExternalGroupAsSubgroup + "\"", change3, change2);

      String nameOfGroupThatContainsExternalGroupAsSubSubgroup = "test-group-2";
      groupOperations
          .newGroup()
          .name(nameOfGroupThatContainsExternalGroupAsSubSubgroup)
          .addSubgroup(uuidOfGroupThatContainsExternalGroupAsSubgroup)
          .create();
      assertQuery(
          "ownerin:\"" + nameOfGroupThatContainsExternalGroupAsSubSubgroup + "\"",
          change3,
          change2);
    } finally {
      testGroupBackend.removeMembershipsOf(user2);
      testGroupBackend.remove(externalGroup.getGroupUUID());
    }
  }

  @Test
  public void byUploaderIn() throws Exception {
    assume().that(getSchema().hasField(ChangeField.UPLOADER)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo), userId);
    assertQuery("uploaderin:Administrators", change1);

    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    CurrentUser user2CurrentUser = userFactory.create(user2);
    newPatchSet(repo, change1, user2CurrentUser);
    assertQuery("uploaderin:Administrators");
    assertQuery("uploaderin:\"Registered Users\"", change1);

    GroupDescription.Basic externalGroup = testGroupBackend.create("External Group");
    try {
      testGroupBackend.setMembershipsOf(
          user2, new ListGroupMembership(ImmutableList.of(externalGroup.getGroupUUID())));

      assertQuery("uploaderin:\"" + "testbackend:" + externalGroup.getName() + "\"", change1);

      String nameOfGroupThatContainsExternalGroupAsSubgroup = "test-group-1";
      AccountGroup.UUID uuidOfGroupThatContainsExternalGroupAsSubgroup =
          groupOperations
              .newGroup()
              .name(nameOfGroupThatContainsExternalGroupAsSubgroup)
              .addSubgroup(externalGroup.getGroupUUID())
              .create();
      assertQuery("uploaderin:\"" + nameOfGroupThatContainsExternalGroupAsSubgroup + "\"", change1);

      String nameOfGroupThatContainsExternalGroupAsSubSubgroup = "test-group-2";
      groupOperations
          .newGroup()
          .name(nameOfGroupThatContainsExternalGroupAsSubSubgroup)
          .addSubgroup(uuidOfGroupThatContainsExternalGroupAsSubgroup)
          .create();
      assertQuery(
          "uploaderin:\"" + nameOfGroupThatContainsExternalGroupAsSubSubgroup + "\"", change1);

    } finally {
      testGroupBackend.removeMembershipsOf(user2);
      testGroupBackend.remove(externalGroup.getGroupUUID());
    }
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
  public void byProjectWithHidden() throws Exception {
    TestRepository<Repo> hiddenProject = createProject("hiddenProject");
    insert(hiddenProject, newChange(hiddenProject));
    projectOperations
        .project(Project.nameKey("hiddenProject"))
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    TestRepository<Repo> visibleProject = createProject("visibleProject");
    Change visibleChange = insert(visibleProject, newChange(visibleProject));
    assertQuery("project:visibleProject", visibleChange);
    assertQuery("project:hiddenProject");
    assertQuery("project:visibleProject OR project:hiddenProject", visibleChange);
  }

  @Test
  public void byParentOf() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    RevCommit commit1 = repo1.parseBody(repo1.commit().message("message").create());
    Change change1 = insert(repo1, newChangeForCommit(repo1, commit1));
    RevCommit commit2 = repo1.parseBody(repo1.commit(commit1));
    Change change2 = insert(repo1, newChangeForCommit(repo1, commit2));
    RevCommit commit3 = repo1.parseBody(repo1.commit(commit1, commit2));
    Change change3 = insert(repo1, newChangeForCommit(repo1, commit3));

    assertQuery("parentof:" + change1.getId().get());
    assertQuery("parentof:" + change1.getKey().get());
    assertQuery("parentof:" + change2.getId().get(), change1);
    assertQuery("parentof:" + change2.getKey().get(), change1);
    assertQuery("parentof:" + change3.getId().get(), change2, change1);
    assertQuery("parentof:" + change3.getKey().get(), change2, change1);
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
  public void byRepository() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("repository:foo");
    assertQuery("repository:repo");
    assertQuery("repository:repo1", change1);
    assertQuery("repository:repo2", change2);
  }

  @Test
  public void byParentRepository() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2", "repo1");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("parentrepository:repo1", change2, change1);
    assertQuery("parentrepository:repo2", change2);
  }

  @Test
  public void byRepositoryPrefix() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("repositories:foo");
    assertQuery("repositories:repo1", change1);
    assertQuery("repositories:repo2", change2);
    assertQuery("repositories:repo", change2, change1);
  }

  @Test
  public void byRepo() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("repo:foo");
    assertQuery("repo:repo");
    assertQuery("repo:repo1", change1);
    assertQuery("repo:repo2", change2);
  }

  @Test
  public void byParentRepo() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2", "repo1");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("parentrepo:repo1", change2, change1);
    assertQuery("parentrepo:repo2", change2);
  }

  @Test
  public void byRepoPrefix() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change1 = insert(repo1, newChange(repo1));
    Change change2 = insert(repo2, newChange(repo2));

    assertQuery("repos:foo");
    assertQuery("repos:repo1", change1);
    assertQuery("repos:repo2", change2);
    assertQuery("repos:repo", change2, change1);
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

    ChangeInserter ins5 = newChangeWithTopic(repo, "https://gerrit.local");
    Change change5 = insert(repo, ins5);

    ChangeInserter ins6 = newChangeWithTopic(repo, "git_gerrit_training");
    Change change6 = insert(repo, ins6);

    Change change_no_topic = insert(repo, newChange(repo));

    assertQuery("intopic:foo");
    assertQuery("intopic:feature1", change1);
    assertQuery("intopic:feature2", change4, change3, change2);
    assertQuery("topic:feature2", change2);
    assertQuery("intopic:feature2", change4, change3, change2);
    assertQuery("intopic:fixup", change4);
    assertQuery("intopic:gerrit", change6, change5);
    assertQuery("topic:\"\"", change_no_topic);
    assertQuery("intopic:\"\"", change_no_topic);

    assume().that(getSchema().hasField(ChangeField.PREFIX_TOPIC)).isTrue();
    assertQuery("prefixtopic:feature", change4, change2, change1);
    assertQuery("prefixtopic:Cher", change3);
    assertQuery("prefixtopic:feature22");
  }

  @Test
  public void byTopicRegex() throws Exception {
    TestRepository<Repo> repo = createProject("repo");

    ChangeInserter ins1 = newChangeWithTopic(repo, "feature1");
    Change change1 = insert(repo, ins1);

    ChangeInserter ins2 = newChangeWithTopic(repo, "Cherrypick-feature1");
    Change change2 = insert(repo, ins2);

    ChangeInserter ins3 = newChangeWithTopic(repo, "feature1-fixup");
    Change change3 = insert(repo, ins3);

    assertQuery("intopic:^feature1.*", change3, change1);
    assertQuery("intopic:{^.*feature1$}", change2, change1);
  }

  @Test
  public void byMessageExact() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("one").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("two").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));
    RevCommit commit3 = repo.parseBody(repo.commit().message("A great \"fix\" to my bug").create());
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));

    assertQuery("message:foo");
    assertQuery("message:one", change1);
    assertQuery("message:two", change2);
    assertQuery("message:\"great \\\"fix\\\" to\"", change3);
  }

  @Test
  public void byMessageRegEx() throws Exception {
    assume().that(getSchema().hasField(ChangeField.COMMIT_MESSAGE_EXACT)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("aaaabcc").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("aaaacc").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));
    RevCommit commit3 = repo.parseBody(repo.commit().message("Title\n\nHELLO WORLD").create());
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));
    RevCommit commit4 =
        repo.parseBody(repo.commit().message("Title\n\nfoobar hello WORLD").create());
    Change change4 = insert(repo, newChangeForCommit(repo, commit4));

    assertQuery("message:\"^aaaa(b|c)*\"", change2, change1);
    assertQuery("message:\"^aaaa(c)*c.*\"", change2);
    assertQuery("message:\"^.*HELLO WORLD.*\"", change3);
    assertQuery(
        "message:\"^.*(H|h)(E|e)(L|l)(L|l)(O|o) (W|w)(O|o)(R|r)(L|l)(D|d).*\"", change4, change3);
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
  public void fullTextMultipleTerms() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("Signed-off: owner").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("Signed by owner").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));
    RevCommit commit3 = repo.parseBody(repo.commit().message("This change is off").create());
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));

    assertQuery("message:\"Signed-off: owner\"", change1);
    assertQuery("message:\"Signed\"", change2, change1);
    assertQuery("message:\"off\"", change3, change1);
  }

  @Test
  public void byMessageMixedCase() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("Hello gerrit").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("Hello Gerrit").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("message:gerrit", change2, change1);
    assertQuery("message:Gerrit", change2, change1);
  }

  @Test
  public void byMessageSubstring() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("https://gerrit.local").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    assertQuery("message:gerrit", change1);
  }

  @Test
  public void byLabel() throws Exception {
    accountManager.authenticate(authRequestFactory.createForUser("anotheruser"));
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo);
    ChangeInserter ins2 = newChange(repo);
    ChangeInserter ins3 = newChange(repo);
    ChangeInserter ins4 = newChange(repo);
    ChangeInserter ins5 = newChange(repo);
    ChangeInserter ins6 = newChange(repo);

    Change reviewMinus2Change = insert(repo, ins);
    gApi.changes().id(reviewMinus2Change.getId().get()).current().review(ReviewInput.reject());

    Change reviewMinus1Change = insert(repo, ins2);
    gApi.changes().id(reviewMinus1Change.getId().get()).current().review(ReviewInput.dislike());

    Change noLabelChange = insert(repo, ins3);

    Change reviewPlus1Change = insert(repo, ins4);
    gApi.changes().id(reviewPlus1Change.getId().get()).current().review(ReviewInput.recommend());

    Change reviewTwoPlus1Change = insert(repo, ins5);
    gApi.changes().id(reviewTwoPlus1Change.getId().get()).current().review(ReviewInput.recommend());
    requestContext.setContext(newRequestContext(createAccount("user1")));
    gApi.changes().id(reviewTwoPlus1Change.getId().get()).current().review(ReviewInput.recommend());
    requestContext.setContext(newRequestContext(userId));

    Change reviewPlus2Change = insert(repo, ins6);
    gApi.changes().id(reviewPlus2Change.getId().get()).current().review(ReviewInput.approve());

    Map<String, Short> m =
        gApi.changes()
            .id(reviewPlus1Change.getId().get())
            .reviewer(user.getAccountId().toString())
            .votes();
    assertThat(m).hasSize(1);
    assertThat(m).containsEntry("Code-Review", Short.valueOf((short) 1));

    Multimap<Integer, Change> changes =
        Multimaps.newListMultimap(Maps.newLinkedHashMap(), () -> Lists.newArrayList());
    changes.put(2, reviewPlus2Change);
    changes.put(1, reviewTwoPlus1Change);
    changes.put(1, reviewPlus1Change);
    changes.put(0, noLabelChange);
    changes.put(-1, reviewMinus1Change);
    changes.put(-2, reviewMinus2Change);

    assertQuery("label:Code-Review=MIN", reviewMinus2Change);
    assertQuery("label:Code-Review=-2", reviewMinus2Change);
    assertQuery("label:Code-Review-2", reviewMinus2Change);
    assertQuery("label:Code-Review=-1", reviewMinus1Change);
    assertQuery("label:Code-Review-1", reviewMinus1Change);
    assertQuery("label:Code-Review=0", noLabelChange);
    assertQuery("label:Code-Review=+1", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=1", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review+1", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=+2", reviewPlus2Change);
    assertQuery("label:Code-Review=2", reviewPlus2Change);
    assertQuery("label:Code-Review+2", reviewPlus2Change);
    assertQuery("label:Code-Review=MAX", reviewPlus2Change);
    assertQuery(
        "label:Code-Review=ANY",
        reviewPlus2Change,
        reviewTwoPlus1Change,
        reviewPlus1Change,
        reviewMinus1Change,
        reviewMinus2Change);

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
    assertQuery("label:Code-Review=+1,user", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=+1,user=user", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=+1,Administrators", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery(
        "label:Code-Review=+1,group=Administrators", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=+1,user=owner", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=+1,owner", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=+2,owner", reviewPlus2Change);
    assertQuery("label:Code-Review=-2,owner", reviewMinus2Change);

    // count=0 is not allowed
    Exception thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("label:Code-Review=+2,count=0"));
    assertThat(thrown).hasMessageThat().isEqualTo("Argument count=0 is not allowed.");
    assertQuery("label:Code-Review=1,count=1", reviewPlus1Change);
    assertQuery("label:Code-Review=1,count=2", reviewTwoPlus1Change);
    assertQuery("label:Code-Review=1,count>=2", reviewTwoPlus1Change);
    assertQuery("label:Code-Review=1,count>1", reviewTwoPlus1Change);
    assertQuery("label:Code-Review=1,count>=1", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=1,count=3");
    thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("label:Code-Review=1,count=7"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("count=7 is not allowed. Maximum allowed value for count is 5.");

    // Less than operator does not match with changes having count=0 for a specific vote value (i.e.
    // no votes for that specific value). We do that deliberately since the computation of count=0
    // for label values is expensive when the change is re-indexed. This is because the operation
    // requires generating all formats for all {label-type, vote}=0 values that are non-voted for
    // the change and storing them with the 'count=0' format.
    assertQuery("label:Code-Review=1,count<5", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery("label:Code-Review=1,count<=5", reviewTwoPlus1Change, reviewPlus1Change);
    assertQuery(
        "label:Code-Review=1,count<=1", // reviewTwoPlus1Change is not matched since its count=2
        reviewPlus1Change);
    assertQuery(
        "label:Code-Review=1,count<5 label:Code-Review=1,count>=1",
        reviewTwoPlus1Change,
        reviewPlus1Change);
    assertQuery(
        "label:Code-Review=1,count<=5 label:Code-Review=1,count>=1",
        reviewTwoPlus1Change,
        reviewPlus1Change);
    assertQuery("label:Code-Review=1,count<=1 label:Code-Review=1,count>=1", reviewPlus1Change);

    assertQuery("label:Code-Review=MAX,count=1", reviewPlus2Change);
    assertQuery("label:Code-Review=MAX,count=2");
    assertQuery("label:Code-Review=MIN,count=1", reviewMinus2Change);
    assertQuery("label:Code-Review=MIN,count>1");
    assertQuery("label:Code-Review=MAX,count<2", reviewPlus2Change);
    assertQuery("label:Code-Review=MIN,count<1");
    assertQuery("label:Code-Review=MAX,count<2 label:Code-Review=MAX,count>=1", reviewPlus2Change);
    assertQuery("label:Code-Review=MIN,count<1 label:Code-Review=MIN,count>=1");
    assertQuery("label:Code-Review>=+1,count=2", reviewTwoPlus1Change);

    // "count" and "user" args cannot be used simultaneously.
    assertThrows(
        BadRequestException.class,
        () -> assertQuery("label:Code-Review=+1,user=non_uploader,count=2"));

    // "count" and "group" args cannot be used simultaneously.
    assertThrows(
        BadRequestException.class, () -> assertQuery("label:Code-Review=+1,group=gerrit,count=2"));
  }

  @Test
  public void byLabelMulti() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Project.NameKey project =
        Project.nameKey(repo.getRepository().getDescription().getRepositoryName());

    LabelType verified =
        label(LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      ProjectConfig cfg = projectConfigFactory.create(project);
      cfg.load(md);
      cfg.upsertLabelType(verified);
      cfg.commit(md);
    }
    projectCache.evictAndReindex(project);

    String heads = RefNames.REFS_HEADS + "*";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(verified.getName()).ref(heads).group(REGISTERED_USERS).range(-1, 1))
        .update();

    ReviewInput reviewVerified = new ReviewInput().label(LabelId.VERIFIED, 1);
    ChangeInserter ins = newChange(repo);
    ChangeInserter ins2 = newChange(repo);
    ChangeInserter ins3 = newChange(repo);
    ChangeInserter ins4 = newChange(repo);
    ChangeInserter ins5 = newChange(repo);

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
    ChangeInserter ins = newChange(repo);
    Account.Id user1 = createAccount("user1");

    Change reviewPlus1Change = insert(repo, ins);

    // post a review with user1
    requestContext.setContext(newRequestContext(user1));
    gApi.changes().id(reviewPlus1Change.getId().get()).current().review(ReviewInput.recommend());

    assertQuery("label:Code-Review=+1,user=user1", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,owner");
  }

  @Test
  public void byLabelNonUploader() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins = newChange(repo);
    Account.Id user1 = createAccount("user1");

    // create a change with "user"
    Change reviewPlus1Change = insert(repo, ins);

    // add a +1 vote with "user". Query doesn't match since voter is the uploader.
    gApi.changes().id(reviewPlus1Change.getId().get()).current().review(ReviewInput.recommend());
    assertQuery("label:Code-Review=+1,user=non_uploader");

    // add a +1 vote with "user1". Query will match since voter is a non-uploader.
    requestContext.setContext(newRequestContext(user1));
    gApi.changes().id(reviewPlus1Change.getId().get()).current().review(ReviewInput.recommend());
    assertQuery("label:Code-Review=+1,user=non_uploader", reviewPlus1Change);
    assertQuery("label:Code-Review=+1,non_uploader", reviewPlus1Change);
  }

  private Change[] codeReviewInRange(Multimap<Integer, Change> changes, int start, int end) {
    List<Change> range = new ArrayList<>();
    for (Map.Entry<Integer, Change> entry : changes.entries()) {
      int i = entry.getKey();
      if (i >= start && i <= end) {
        range.add(entry.getValue());
      }
    }
    return range.toArray(new Change[0]);
  }

  private String createGroup(String name, String owner) throws Exception {
    GroupInput in = new GroupInput();
    in.name = name;
    in.ownerId = owner;
    gApi.groups().create(in);
    return name;
  }

  private Account.Id createAccount(String name) throws Exception {
    return accountManager.authenticate(authRequestFactory.createForUser(name)).getAccountId();
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
  public void byLabelExternalGroup() throws Exception {
    Account.Id user1 = createAccount("user1");
    Account.Id user2 = createAccount("user2");
    TestRepository<InMemoryRepositoryManager.Repo> repo = createProject("repo");

    // create group and add users
    AccountGroup.UUID external_group1 = AccountGroup.uuid("testbackend:group1");
    AccountGroup.UUID external_group2 = AccountGroup.uuid("testbackend:group2");
    String nameOfGroupThatContainsExternalGroupAsSubgroup = "test-group-1";
    String nameOfGroupThatContainsExternalGroupAsSubSubgroup = "test-group-2";
    testGroupBackend.create(external_group1);
    testGroupBackend.create(external_group2);
    testGroupBackend.setMembershipsOf(
        user1, new ListGroupMembership(ImmutableList.of(external_group1)));
    testGroupBackend.setMembershipsOf(
        user2, new ListGroupMembership(ImmutableList.of(external_group2)));
    AccountGroup.UUID uuidOfGroupThatContainsExternalGroupAsSubgroup =
        groupOperations
            .newGroup()
            .name(nameOfGroupThatContainsExternalGroupAsSubgroup)
            .addSubgroup(external_group1)
            .create();
    groupOperations
        .newGroup()
        .name(nameOfGroupThatContainsExternalGroupAsSubSubgroup)
        .addSubgroup(uuidOfGroupThatContainsExternalGroupAsSubgroup)
        .create();

    Change change1 = insert(repo, newChange(repo), user1);
    Change change2 = insert(repo, newChange(repo), user1);

    // post a review with user1 and other_user
    requestContext.setContext(newRequestContext(user1));
    gApi.changes()
        .id(change1.getId().get())
        .current()
        .review(new ReviewInput().label("Code-Review", 1));
    requestContext.setContext(newRequestContext(userId));
    gApi.changes()
        .id(change2.getId().get())
        .current()
        .review(new ReviewInput().label("Code-Review", 1));

    assertQuery("label:Code-Review=+1," + external_group1.get(), change1);
    assertQuery("label:Code-Review=+1,group=" + external_group1.get(), change1);
    assertQuery(
        "label:Code-Review=+1,group=" + nameOfGroupThatContainsExternalGroupAsSubgroup, change1);
    assertQuery(
        "label:Code-Review=+1,group=" + nameOfGroupThatContainsExternalGroupAsSubSubgroup, change1);
    assertQuery("label:Code-Review=+1,user=user1", change1);
    assertQuery("label:Code-Review=+1,user=user2");
    assertQuery("label:Code-Review=+1,group=" + external_group2.get());

    // Negated operator tests
    assertQuery("-label:Code-Review=+1," + external_group1.get(), change2);
    assertQuery("-label:Code-Review=+1,group=" + external_group1.get(), change2);
    assertQuery(
        "-label:Code-Review=+1,group=" + nameOfGroupThatContainsExternalGroupAsSubgroup, change2);
    assertQuery(
        "-label:Code-Review=+1,group=" + nameOfGroupThatContainsExternalGroupAsSubSubgroup,
        change2);
    assertQuery("-label:Code-Review=+1,user=user1", change2);
    assertQuery("-label:Code-Review=+1,group=" + external_group2.get(), change2, change1);
    assertQuery("-label:Code-Review=+1,user=user2", change2, change1);
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
      assertWithMessage(q).that(results).hasSize(expectedSize);
      assertWithMessage(q)
          .that(results.get(results.size() - 1)._moreChanges)
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
  public void startCannotBeLessThanZero() throws Exception {
    assertFailingQuery(
        newQuery("owner:self").withStart(-1), "'start' parameter cannot be less than zero");
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
  public void updatedOrder() throws Exception {
    resetTimeWithClockStep(1, SECONDS);
    TestRepository<Repo> repo = createProject("repo");
    ChangeInserter ins1 = newChange(repo);
    Change change1 = insert(repo, ins1);
    Change change2 = insert(repo, newChange(repo));

    assertThat(lastUpdatedMs(change1)).isLessThan(lastUpdatedMs(change2));
    assertQuery("status:new", change2, change1);

    gApi.changes().id(change1.getId().get()).topic("new-topic");
    change1 = notesFactory.create(change1.getProject(), change1.getId()).getChange();

    assertThat(lastUpdatedMs(change1)).isGreaterThan(lastUpdatedMs(change2));
    assertThat(lastUpdatedMs(change1) - lastUpdatedMs(change2))
        .isAtLeast(MILLISECONDS.convert(1, SECONDS));

    // change1 moved to the top.
    assertQuery("status:new", change1, change2);
  }

  @Test
  public void filterOutMoreThanOnePageOfResults() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo), userId);
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
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
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    for (int i = 0; i < 5; i++) {
      insert(repo, newChange(repo), user2);
    }

    assertQuery("status:new ownerin:Administrators");
    assertQuery("status:new ownerin:Administrators limit:2");
  }

  @Test
  public void byFileExact() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChangeWithFiles(repo, "dir/file1", "dir/file2"));

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
    Change change = insert(repo, newChangeWithFiles(repo, "dir/file1", "dir/file2"));

    assertQuery("file:.*file.*");
    assertQuery("file:^file.*"); // Whole path only.
    assertQuery("file:^dir.file.*", change);
  }

  @Test
  public void byPathExact() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChangeWithFiles(repo, "dir/file1", "dir/file2"));

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
    Change change = insert(repo, newChangeWithFiles(repo, "dir/file1", "dir/file2"));

    assertQuery("path:.*file.*");
    assertQuery("path:^dir.file.*", change);
  }

  @Test
  public void byExtension() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWithFiles(repo, "foo.h", "foo.cc"));
    Change change2 = insert(repo, newChangeWithFiles(repo, "bar.H", "bar.CC"));
    Change change3 = insert(repo, newChangeWithFiles(repo, "dir/baz.h", "dir/baz.cc"));
    Change change4 = insert(repo, newChangeWithFiles(repo, "Quux.java", "foo"));
    Change change5 = insert(repo, newChangeWithFiles(repo, "foo"));

    assertQuery("extension:java", change4);
    assertQuery("ext:java", change4);
    assertQuery("ext:.java", change4);
    assertQuery("ext:jAvA", change4);
    assertQuery("ext:.jAvA", change4);
    assertQuery("ext:cc", change3, change2, change1);

    // matching changes with files that have no extension is possible
    assertQuery("ext:\"\"", change5, change4);
    assertFailingQuery("ext:");
  }

  @Test
  public void byOnlyExtensions() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWithFiles(repo, "foo.h", "foo.cc", "bar.cc"));
    Change change2 = insert(repo, newChangeWithFiles(repo, "bar.H", "bar.CC", "foo.H"));
    Change change3 = insert(repo, newChangeWithFiles(repo, "foo.CC", "bar.cc"));
    Change change4 = insert(repo, newChangeWithFiles(repo, "dir/baz.h", "dir/baz.cc"));
    Change change5 = insert(repo, newChangeWithFiles(repo, "Quux.java"));
    Change change6 = insert(repo, newChangeWithFiles(repo, "foo.txt", "foo"));
    Change change7 = insert(repo, newChangeWithFiles(repo, "foo"));

    // case doesn't matter
    assertQuery("onlyextensions:cc,h", change4, change2, change1);
    assertQuery("onlyextensions:CC,H", change4, change2, change1);
    assertQuery("onlyextensions:cc,H", change4, change2, change1);
    assertQuery("onlyextensions:cC,h", change4, change2, change1);
    assertQuery("onlyextensions:cc", change3);
    assertQuery("onlyextensions:CC", change3);
    assertQuery("onlyexts:java", change5);
    assertQuery("onlyexts:jAvA", change5);
    assertQuery("onlyexts:.jAvA", change5);

    // order doesn't matter
    assertQuery("onlyextensions:h,cc", change4, change2, change1);
    assertQuery("onlyextensions:H,CC", change4, change2, change1);

    // specifying extension with '.' is okay
    assertQuery("onlyextensions:.cc,.h", change4, change2, change1);
    assertQuery("onlyextensions:cc,.h", change4, change2, change1);
    assertQuery("onlyextensions:.cc,h", change4, change2, change1);
    assertQuery("onlyexts:.java", change5);

    // matching changes without extension is possible
    assertQuery("onlyexts:txt");
    assertQuery("onlyexts:txt,", change6);
    assertQuery("onlyexts:,txt", change6);
    assertQuery("onlyextensions:\"\"", change7);
    assertQuery("onlyexts:\"\"", change7);
    assertQuery("onlyextensions:,", change7);
    assertQuery("onlyexts:,", change7);
    assertFailingQuery("onlyextensions:");
    assertFailingQuery("onlyexts:");

    // inverse queries
    assertQuery("-onlyextensions:cc,h", change7, change6, change5, change3);
  }

  @Test
  public void byFooter() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("Test\n\nfoo: bar").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("Test\n\nfoo: baz").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));
    RevCommit commit3 = repo.parseBody(repo.commit().message("Test\n\nfoo: bar\nfoo:baz").create());
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));
    RevCommit commit4 = repo.parseBody(repo.commit().message("Test\n\nfoo: bar=baz").create());
    Change change4 = insert(repo, newChangeForCommit(repo, commit4));

    // create a changes with lines that look like footers, but which are not
    RevCommit commit5 =
        repo.parseBody(
            repo.commit().message("Test\n\nfoo: bar\n\nfoo=bar").insertChangeId().create());
    Change change5 = insert(repo, newChangeForCommit(repo, commit5));
    RevCommit commit6 = repo.parseBody(repo.commit().message("Test\n\na=b: c").create());
    insert(repo, newChangeForCommit(repo, commit6));

    // matching by 'key=value' works
    assertQuery("footer:foo=bar", change3, change1);
    assertQuery("footer:foo=baz", change3, change2);
    assertQuery("footer:Change-Id=" + change5.getKey(), change5);
    assertQuery("footer:foo=bar=baz", change4);

    // case doesn't matter
    assertQuery("footer:foo=BAR", change3, change1);
    assertQuery("footer:FOO=bar", change3, change1);
    assertQuery("footer:fOo=BaZ", change3, change2);

    // verbatim matching of footers works
    assertQuery("footer:\"foo: bar\"", change3, change1);
    assertQuery("footer:\"foo: baz\"", change3, change2);
    assertQuery("footer:\"Change-Id: " + change5.getKey() + "\"", change5);
    assertQuery("footer:\"foo: bar=baz\"", change4);

    // expect no match because 'a=b: c' of commit6 is not a valid footer (footer key cannot contain
    // '=')
    assertQuery("footer:a=b=c");
    assertQuery("footer:\"a=b: c\"");

    // expect empty result for invalid footers
    assertQuery("footer:foo");
    assertQuery("footer:foo=");
    assertQuery("footer:=foo");
    assertQuery("footer:=");
  }

  @Test
  public void byFooterName() throws Exception {
    assume().that(getSchema().hasField(ChangeField.FOOTER_NAME)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().message("Test\n\nfoo: bar").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 = repo.parseBody(repo.commit().message("Test\n\nBaR: baz").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    // create a changes with lines that look like footers, but which are not
    RevCommit commit6 = repo.parseBody(repo.commit().message("Test\n\na=b: c").create());
    insert(repo, newChangeForCommit(repo, commit6));

    // matching by 'key=value' works
    assertQuery("hasfooter:foo", change1);

    // case matters
    assertQuery("hasfooter:BaR", change2);
    assertQuery("hasfooter:Bar");
  }

  @Test
  public void byDirectory() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWithFiles(repo, "src/foo.h", "src/foo.cc"));
    Change change2 = insert(repo, newChangeWithFiles(repo, "src/java/foo.java", "src/js/bar.js"));
    Change change3 =
        insert(repo, newChangeWithFiles(repo, "documentation/training/slides/README.txt"));
    Change change4 = insert(repo, newChangeWithFiles(repo, "a.txt"));
    Change change5 = insert(repo, newChangeWithFiles(repo, "a/b/c/d/e/foo.txt"));
    Change change6 = insert(repo, newChangeWithFiles(repo, "all/caps/DIRECTORY/file.txt"));

    // matching by directory prefix works
    assertQuery("directory:src", change2, change1);
    assertQuery("directory:src/java", change2);
    assertQuery("directory:src/js", change2);
    assertQuery("directory:documentation/", change3);
    assertQuery("directory:documentation/training", change3);
    assertQuery("directory:documentation/training/slides", change3);

    // 'dir' alias works
    assertQuery("dir:src", change2, change1);
    assertQuery("dir:src/java", change2);

    // case doesn't matter
    assertQuery("directory:Documentation/TrAiNiNg/SLIDES", change3);
    assertQuery("directory:all/caps/directory", change6);

    // leading and trailing '/' doesn't matter
    assertQuery("directory:/documentation/training/slides", change3);
    assertQuery("directory:documentation/training/slides/", change3);
    assertQuery("directory:/documentation/training/slides/", change3);

    // files do not match as directory
    assertQuery("directory:src/foo.h");
    assertQuery("directory:documentation/training/slides/README.txt");

    // root directory matches all changes
    assertQuery("directory:/", change6, change5, change4, change3, change2, change1);
    assertQuery("directory:\"\"", change6, change5, change4, change3, change2, change1);
    assertFailingQuery("directory:");

    // matching single directory segments works
    assertQuery("directory:java", change2);
    assertQuery("directory:slides", change3);

    // files do not match as directory segment
    assertQuery("directory:foo.h");

    // matching any combination of intermediate directory segments works
    assertQuery("directory:training/slides", change3);
    assertQuery("directory:b/c", change5);
    assertQuery("directory:b/c/d", change5);
    assertQuery("directory:b/c/d/e", change5);
    assertQuery("directory:c/d", change5);
    assertQuery("directory:c/d/e", change5);
    assertQuery("directory:d/e", change5);

    // files do not match as directory segments
    assertQuery("directory:d/e/foo.txt");
    assertQuery("directory:e/foo.txt");

    // matching any combination of intermediate directory segments works with leading and trailing
    // '/'
    assertQuery("directory:/b/c", change5);
    assertQuery("directory:/b/c/", change5);
    assertQuery("directory:b/c/", change5);
  }

  @Test
  public void byDirectoryRegex() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWithFiles(repo, "src/java/foo.java", "src/js/bar.js"));
    Change change2 =
        insert(repo, newChangeWithFiles(repo, "documentation/training/slides/README.txt"));

    // match by regexp
    assertQuery("directory:^.*va.*", change1);
    assertQuery("directory:^documentation/.*/slides", change2);
    assertQuery("directory:^train.*", change2);
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
    input.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(commentInput));
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
    long startMs = TestTimeUtil.START.toEpochMilli();
    Change change1 = insert(repo, newChange(repo), null, Instant.ofEpochMilli(startMs));
    Change change2 =
        insert(repo, newChange(repo), null, Instant.ofEpochMilli(startMs + thirtyHoursInMs));

    // Stop time so age queries use the same endpoint.
    TestTimeUtil.setClockStep(0, MILLISECONDS);
    TestTimeUtil.setClock(new Timestamp(startMs + 2 * thirtyHoursInMs));
    long nowMs = TimeUtil.nowMs();

    assertThat(lastUpdatedMs(change2) - lastUpdatedMs(change1)).isEqualTo(thirtyHoursInMs);
    assertThat(nowMs - lastUpdatedMs(change2)).isEqualTo(thirtyHoursInMs);
    assertThat(TimeUtil.nowMs()).isEqualTo(nowMs);

    // Change1 was last updated on 2009-09-30 21:00:00 -0000
    // Change2 was last updated on 2009-10-02 03:00:00 -0000
    // The endpoint is 2009-10-03 09:00:00 -0000

    assertQuery("-age:1d");
    assertQuery("-age:" + (30 * 60 - 1) + "m");
    assertQuery("-age:2d", change2);
    assertQuery("-age:3d", change2, change1);
    assertQuery("age:3d");
    assertQuery("age:2d", change1);
    assertQuery("age:1d", change2, change1);

    // Same test as above, but using filter code path.
    assertQuery(makeIndexedPredicateFilterQuery("-age:1d"));
    assertQuery(makeIndexedPredicateFilterQuery("-age:" + (30 * 60 - 1) + "m"));
    assertQuery(makeIndexedPredicateFilterQuery("-age:2d"), change2);
    assertQuery(makeIndexedPredicateFilterQuery("-age:3d"), change2, change1);
    assertQuery(makeIndexedPredicateFilterQuery("age:3d"));
    assertQuery(makeIndexedPredicateFilterQuery("age:2d"), change1);
    assertQuery(makeIndexedPredicateFilterQuery("age:1d"), change2, change1);
  }

  @Test
  public void byBeforeUntil() throws Exception {
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);
    resetTimeWithClockStep(thirtyHoursInMs, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.toEpochMilli();
    Change change1 = insert(repo, newChange(repo), null, Instant.ofEpochMilli(startMs));
    Change change2 =
        insert(repo, newChange(repo), null, Instant.ofEpochMilli(startMs + thirtyHoursInMs));
    TestTimeUtil.setClockStep(0, MILLISECONDS);

    // Change1 was last updated on 2009-09-30 21:00:00 -0000
    // Change2 was last updated on 2009-10-02 03:00:00 -0000

    for (String predicate : Lists.newArrayList("before:", "until:")) {
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
      assertQuery(predicate + "\"2009-09-30 21:00:00 -0000\"", change1);
      assertQuery(predicate + "\"2009-10-02 03:00:00 -0000\"", change2, change1);
    }

    // Same test as above, but using filter code path.
    for (String predicate : Lists.newArrayList("before:", "until:")) {
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-09-29"));
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-09-30"));
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "\"2009-09-30 16:59:00 -0400\""));
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "\"2009-09-30 20:59:00 -0000\""));
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "\"2009-09-30 20:59:00\""));
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-09-30 17:02:00 -0400\""), change1);
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-10-01 21:02:00 -0000\""), change1);
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "\"2009-10-01 21:02:00\""), change1);
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-10-01"), change1);
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-10-03"), change2, change1);
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-09-30 21:00:00 -0000\""), change1);
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-10-02 03:00:00 -0000\""),
          change2,
          change1);
    }
  }

  @Test
  public void byAfterSince() throws Exception {
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);
    resetTimeWithClockStep(thirtyHoursInMs, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.toEpochMilli();
    Change change1 = insert(repo, newChange(repo), null, Instant.ofEpochMilli(startMs));
    Change change2 =
        insert(repo, newChange(repo), null, Instant.ofEpochMilli(startMs + thirtyHoursInMs));
    TestTimeUtil.setClockStep(0, MILLISECONDS);

    // Change1 was last updated on 2009-09-30 21:00:00 -0000
    // Change2 was last updated on 2009-10-02 03:00:00 -0000
    for (String predicate : Lists.newArrayList("after:", "since:")) {
      assertQuery(predicate + "2009-10-03");
      assertQuery(predicate + "\"2009-10-01 20:59:59 -0400\"", change2);
      assertQuery(predicate + "\"2009-10-01 20:59:59 -0000\"", change2);
      assertQuery(predicate + "2009-10-01", change2);
      assertQuery(predicate + "2009-09-30", change2, change1);
      assertQuery(predicate + "\"2009-09-30 21:00:00 -0000\"", change2, change1);
      assertQuery(predicate + "\"2009-10-02 03:00:00 -0000\"", change2);
    }

    // Same test as above, but using filter code path.
    for (String predicate : Lists.newArrayList("after:", "since:")) {
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-10-03"));
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-10-01 20:59:59 -0400\""), change2);
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-10-01 20:59:59 -0000\""), change2);
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-10-01"), change2);
      assertQuery(makeIndexedPredicateFilterQuery(predicate + "2009-09-30"), change2, change1);
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-09-30 21:00:00 -0000\""),
          change2,
          change1);
      assertQuery(
          makeIndexedPredicateFilterQuery(predicate + "\"2009-10-02 03:00:00 -0000\""), change2);
    }
  }

  @Test
  public void byMergedBefore() throws Exception {
    assume().that(getSchema().hasField(ChangeField.MERGED_ON)).isTrue();
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);

    // Stop the clock, will set time to specific test values.
    resetTimeWithClockStep(0, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.toEpochMilli();
    TestTimeUtil.setClock(new Timestamp(startMs));
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));

    TestTimeUtil.setClock(new Timestamp(startMs + thirtyHoursInMs));
    submit(change3);
    TestTimeUtil.setClock(new Timestamp(startMs + 2 * thirtyHoursInMs));
    submit(change2);
    TestTimeUtil.setClock(new Timestamp(startMs + 3 * thirtyHoursInMs));
    // Put another approval on the change, just to update it. This does not record an update in
    // NoteDb since this is a no/op.
    approve(change1);
    approve(change3);

    assertThat(TimeUtil.nowMs()).isEqualTo(startMs + 3 * thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change3)).isEqualTo(startMs + thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change2)).isEqualTo(startMs + 2 * thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change1)).isEqualTo(startMs + 3 * thirtyHoursInMs);

    // Verify that:
    // 1. Change1 was not submitted and should be never returned.
    // 2. Change2 was merged on 2009-10-02 03:00:00 -0000
    // 3. Change3 was merged on 2009-10-03 09:00:00.0 -0000
    assertQuery("mergedbefore:2009-10-01");
    // Changes excluded on the date submitted.
    assertQuery("mergedbefore:2009-10-02");
    assertQuery("mergedbefore:\"2009-10-01 22:59:00 -0400\"");
    assertQuery("mergedbefore:\"2009-10-01 02:59:00\"");
    assertQuery("mergedbefore:\"2009-10-01 23:02:00 -0400\"", change3);
    assertQuery("mergedbefore:\"2009-10-02 03:02:00 -0000\"", change3);
    assertQuery("mergedbefore:\"2009-10-02 03:02:00\"", change3);
    assertQuery("mergedbefore:2009-10-03", change3);
    // Changes are sorted by lastUpdatedOn first, then by mergedOn.
    // Even though Change2 was merged after Change3, Change3 is returned first.
    assertQuery("mergedbefore:2009-10-04", change2, change3);

    // Same test as above, but using filter code path.
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:2009-10-01"));
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:2009-10-02"));
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:\"2009-10-01 22:59:00 -0400\""));
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:\"2009-10-01 02:59:00\""));
    assertQuery(
        makeIndexedPredicateFilterQuery("mergedbefore:\"2009-10-01 23:02:00 -0400\""), change3);
    assertQuery(
        makeIndexedPredicateFilterQuery("mergedbefore:\"2009-10-02 03:02:00 -0000\""), change3);
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:\"2009-10-02 03:02:00\""), change3);
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:2009-10-03"), change3);
    assertQuery(makeIndexedPredicateFilterQuery("mergedbefore:2009-10-04"), change2, change3);
  }

  @Test
  public void byMergedAfter() throws Exception {
    assume().that(getSchema().hasField(ChangeField.MERGED_ON)).isTrue();
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);

    // Stop the clock, will set time to specific test values.
    resetTimeWithClockStep(0, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.toEpochMilli();
    TestTimeUtil.setClock(new Timestamp(startMs));
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));
    assertThat(TimeUtil.nowMs()).isEqualTo(startMs);

    TestTimeUtil.setClock(new Timestamp(startMs + thirtyHoursInMs));
    submit(change3);

    TestTimeUtil.setClock(new Timestamp(startMs + 2 * thirtyHoursInMs));
    submit(change2);

    TestTimeUtil.setClock(new Timestamp(startMs + 3 * thirtyHoursInMs));
    // Put another approval on the change, just to update it. This does not record an update
    // in NoteDb since this is a no/op.
    approve(change1);
    approve(change3);

    assertThat(TimeUtil.nowMs()).isEqualTo(startMs + 3 * thirtyHoursInMs);

    assertThat(lastUpdatedMsApi(change3)).isEqualTo(startMs + thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change2)).isEqualTo(startMs + 2 * thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change1)).isEqualTo(startMs + 3 * thirtyHoursInMs);

    // Verify that:
    // 1. Change1 was not submitted and should be never returned.
    // 2. Change2 was merged on 2009-10-02 03:00:00 -0000
    // 3. Change3 was merged on 2009-10-03 09:00:00.0 -0000
    assertQuery("mergedafter:2009-10-01", change2, change3);
    // Changes are sorted by lastUpdatedOn first, then by mergedOn.
    // Change 2 (which was updated last) is returned before change 3.
    assertQuery("mergedafter:\"2009-10-01 22:59:00 -0400\"", change2, change3);
    assertQuery("mergedafter:\"2009-10-02 02:59:00 -0000\"", change2, change3);
    assertQuery("mergedafter:\"2009-10-01 23:02:00 -0400\"", change2);
    assertQuery("mergedafter:\"2009-10-02 03:02:00 -0000\"", change2);
    // Changes included on the date submitted.
    assertQuery("mergedafter:2009-10-02", change2, change3);
    assertQuery("mergedafter:2009-10-03", change2);

    // Same test as above, but using filter code path.

    assertQuery(makeIndexedPredicateFilterQuery("mergedafter:2009-10-01"), change2, change3);
    // Changes are sorted by lastUpdatedOn first, then by mergedOn.
    // Even though Change2 was merged after Change3, Change3 is returned first.
    assertQuery(
        makeIndexedPredicateFilterQuery("mergedafter:\"2009-10-01 22:59:00 -0400\""),
        change2,
        change3);
    assertQuery(
        makeIndexedPredicateFilterQuery("mergedafter:\"2009-10-02 02:59:00 -0000\""),
        change2,
        change3);
    assertQuery(
        makeIndexedPredicateFilterQuery("mergedafter:\"2009-10-01 23:02:00 -0400\""), change2);
    assertQuery(
        makeIndexedPredicateFilterQuery("mergedafter:\"2009-10-02 03:02:00 -0000\""), change2);
    // Changes included on the date submitted.
    assertQuery(makeIndexedPredicateFilterQuery("mergedafter:2009-10-02"), change2, change3);
    assertQuery(makeIndexedPredicateFilterQuery("mergedafter:2009-10-03"), change2);
  }

  @Test
  public void updatedThenMergedOrder() throws Exception {
    assume().that(getSchema().hasField(ChangeField.MERGED_ON)).isTrue();
    long thirtyHoursInMs = MILLISECONDS.convert(30, HOURS);

    // Stop the clock, will set time to specific test values.
    resetTimeWithClockStep(0, MILLISECONDS);
    TestRepository<Repo> repo = createProject("repo");
    long startMs = TestTimeUtil.START.toEpochMilli();
    TestTimeUtil.setClock(new Timestamp(startMs));

    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));

    TestTimeUtil.setClock(new Timestamp(startMs + thirtyHoursInMs));
    submit(change2);
    submit(change3);
    TestTimeUtil.setClock(new Timestamp(startMs + 2 * thirtyHoursInMs));
    // Approve post submit just to update lastUpdatedOn. This does not record an update in NoteDb
    // since this is a No/op.
    approve(change3);
    approve(change2);
    submit(change1);

    // All Changes were last updated at the same time.
    assertThat(lastUpdatedMsApi(change3)).isEqualTo(startMs + thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change2)).isEqualTo(startMs + thirtyHoursInMs);
    assertThat(lastUpdatedMsApi(change1)).isEqualTo(startMs + 2 * thirtyHoursInMs);

    // Changes are sorted by lastUpdatedOn first, then by mergedOn, then by Id in reverse order.
    // 1. Change3 and Change2 were merged at the same time, but Change3 ID > Change2 ID.
    // 2. Change1 ID < Change3 ID & Change2 ID but it was merged last.
    assertQuery("mergedbefore:2009-10-06", change1, change3, change2);
    assertQuery("mergedafter:2009-09-30", change1, change3, change2);
    assertQuery("status:merged", change1, change3, change2);
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
    assertQuery("-(added:<3 OR added:>3)", change1);

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
    assertQuery("-(deleted:<2 OR deleted:>2)", change2);

    assertQuery("deleted:>1", change2);
    assertQuery("-deleted:<=1", change2);

    assertQuery("deleted:>=2", change2);
    assertQuery("-deleted:<2", change2);

    assertQuery("deleted:<1", change1);
    assertQuery("-deleted:>=1", change1);

    assertQuery("deleted:<=0", change1);

    for (String str : Lists.newArrayList("delta:", "size:")) {
      assertQuery(str + "<2");
      assertQuery(str + "3", change1);
      assertQuery(str + ">2", change1);
      assertQuery(str + ">=3", change1);
      assertQuery(str + "<3", change2);
      assertQuery(str + "<=2", change2);
    }
  }

  private List<Change> setUpHashtagChanges() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    addHashtags(change1.getId(), "foo", "aaa-bbb-ccc");
    addHashtags(change2.getId(), "foo", "bar", "a tag", "ACamelCaseTag");
    return ImmutableList.of(change1, change2);
  }

  private void addHashtags(Change.Id changeId, String... hashtags) throws Exception {
    HashtagsInput in = new HashtagsInput();
    in.add = ImmutableSet.copyOf(hashtags);
    gApi.changes().id(changeId.get()).setHashtags(in);
  }

  @Test
  public void byHashtag() throws Exception {
    List<Change> changes = setUpHashtagChanges();
    assertQuery("hashtag:foo", changes.get(1), changes.get(0));
    assertQuery("hashtag:bar", changes.get(1));
    assertQuery("hashtag:\"a tag\"", changes.get(1));
    assertQuery("hashtag:\"a tag \"", changes.get(1));
    assertQuery("hashtag:\" a tag \"", changes.get(1));
    assertQuery("hashtag:\"#a tag\"", changes.get(1));
    assertQuery("hashtag:\"# #a tag\"", changes.get(1));
    assertQuery("hashtag:acamelcasetag", changes.get(1));
    assertQuery("hashtag:ACamelCaseTAg", changes.get(1));
  }

  @Test
  public void byHashtagFullText() throws Exception {
    assume().that(getSchema().hasField(ChangeField.FUZZY_HASHTAG)).isTrue();
    List<Change> changes = setUpHashtagChanges();
    assertQuery("inhashtag:foo", changes.get(1), changes.get(0));
    assertQuery("inhashtag:bbb", changes.get(0));
    assertQuery("inhashtag:tag", changes.get(1));
  }

  @Test
  public void byHashtagPrefix() throws Exception {
    assume().that(getSchema().hasField(ChangeField.PREFIX_HASHTAG)).isTrue();
    List<Change> changes = setUpHashtagChanges();
    assertQuery("prefixhashtag:a", changes.get(1), changes.get(0));
    assertQuery("prefixhashtag:aa", changes.get(0));
    assertQuery("prefixhashtag:bar", changes.get(1));
  }

  @Test
  public void byHashtagRegex() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));
    addHashtags(change1.getId(), "feature1");
    addHashtags(change1.getId(), "trending");
    addHashtags(change2.getId(), "Cherrypick-feature1");
    addHashtags(change3.getId(), "feature1-fixup");

    assertQuery("inhashtag:^feature1.*", change3, change1);
    assertQuery("inhashtag:{^.*feature1$}", change2, change1);
    assertQuery("inhashtag:^trending.*", change1);
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
    ri4.labels = ImmutableMap.of("Code-Review", (short) 1);
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

    assertQuery("Code-Review=+1", change4);
  }

  @Test
  public void byDefaultWithCommitPrefix() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit = repo.parseBody(repo.commit().message("message").create());
    Change change = insert(repo, newChangeForCommit(repo, commit));

    assertQuery(commit.getId().getName().substring(0, 6), change);
  }

  @Test
  public void visible() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChangePrivate(repo));

    String q = "project:repo";

    // Bad request for query with non-existent user
    assertThatQueryException(q + " visibleto:notexisting");

    // Current user can see all changes
    assertQuery(q, change2, change1);
    assertQuery(q + " visibleto:self", change2, change1);

    // Second user cannot see first user's private change
    Account.Id user2 = createAccount("user2");
    assertQuery(q + " visibleto:" + user2.get(), change1);
    assertQuery(q + " visibleto:user2", change1);

    String g1 = createGroup("group1", "Administrators");
    gApi.groups().id(g1).addMembers("user2");

    // By default when a group is created without any permission granted,
    // nothing is visible to it; having members or not has nothing to do with it
    assertQuery(q + " visibleto:" + g1);

    // change is visible to group ONLY when access is granted
    grant(
        Project.nameKey("repo"),
        "refs/*",
        Permission.READ,
        false,
        AccountGroup.uuid(gApi.groups().id(g1).get().id));
    assertQuery(q + " visibleto:" + g1, change1);

    // Both changes are visible to InternalUser
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      assertQuery(q, change2, change1);
    }

    requestContext.setContext(newRequestContext(user2));
    assertQuery("is:visible", change1);

    Account.Id user3 = createAccount("user3");

    // Explicitly authenticate user2 and user3 so that display name gets set
    AuthRequest authRequest = authRequestFactory.createForUser("user2");
    authRequest.setDisplayName("Another User");
    authRequest.setEmailAddress("user2@example.com");
    accountManager.authenticate(authRequest);
    authRequest = authRequestFactory.createForUser("user3");
    authRequest.setDisplayName("Another User");
    authRequest.setEmailAddress("user3@example.com");
    accountManager.authenticate(authRequest);

    // Switch to user3
    requestContext.setContext(newRequestContext(user3));
    Change change3 = insert(repo, newChange(repo), user3);
    Change change4 = insert(repo, newChangePrivate(repo), user3);

    // User3 can see both their changes and the first user's change
    assertQuery(q + " visibleto:" + user3.get(), change4, change3, change1);

    // User2 cannot see user3's private change
    assertQuery(q + " visibleto:" + user2.get(), change3, change1);

    // Query as user3 by display name matching user2 and user3; bad request
    assertFailingQuery(
        q + " visibleto:\"Another User\"", "\"Another User\" resolves to multiple accounts");
  }

  protected void grant(
      Project.NameKey project,
      String ref,
      String permission,
      boolean force,
      AccountGroup.UUID groupUUID)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(project)) {
      md.setMessage(String.format("Grant %s on %s", permission, ref));
      ProjectConfig config = projectConfigFactory.read(md);
      config.upsertAccessSection(
          ref,
          s -> {
            Permission.Builder p = s.upsertPermission(permission);
            PermissionRule.Builder rule =
                PermissionRule.builder(GroupReference.create(groupUUID, groupUUID.get()))
                    .setForce(force);
            p.add(rule);
          });

      config.commit(md);
      projectCache.evictAndReindex(config.getProject());
    }
  }

  @Test
  public void visibleToSelf() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    gApi.changes().id(change2.getChangeId()).setPrivate(true, "private");

    String q = "project:repo";
    assertQuery(q + " visibleto:self", change2, change1);
    assertQuery(q + " visibleto:me", change2, change1);

    // Anonymous user cannot see first user's private change.
    requestContext.setContext(anonymousUserProvider::get);
    assertQuery(q + " visibleto:self", change1);
    assertQuery(q + " visibleto:me", change1);
  }

  @Test
  public void byCommentBy() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    int user2 =
        accountManager
            .authenticate(authRequestFactory.createForUser("anotheruser"))
            .getAccountId()
            .get();

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = "inline";
    input.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
    gApi.changes().id(change1.getId().get()).current().review(input);

    input = new ReviewInput();
    input.message = "toplevel";
    gApi.changes().id(change2.getId().get()).current().review(input);

    assertQuery("commentby:" + userId.get(), change2, change1);
    assertQuery("commentby:" + user2);
  }

  @Test
  public void bySubmitRuleResult() throws Exception {
    try (Registration registration =
        extensionRegistry.newRegistration().add(new FakeSubmitRule())) {
      TestRepository<Repo> repo = createProject("repo");
      Change change = insert(repo, newChange(repo));
      // The fake submit rule exports its ruleName as "FakeSubmitRule"
      assertQuery("rule:FakeSubmitRule");

      // FakeSubmitRule returns true if change has one or more hashtags.
      HashtagsInput hashtag = new HashtagsInput();
      hashtag.add = ImmutableSet.of("Tag1");
      gApi.changes().id(change.getId().get()).setHashtags(hashtag);

      assertQuery("rule:FakeSubmitRule", change);
      assertQuery("rule:FakeSubmitRule=OK", change);
      assertQuery("rule:FakeSubmitRule=NOT_READY");
    }
  }

  @Test
  public void byNonExistingSubmitRule_returnsEmpty() throws Exception {
    try (Registration registration =
        extensionRegistry.newRegistration().add(new FakeSubmitRule())) {
      TestRepository<Repo> repo = createProject("repo");
      insert(repo, newChange(repo));
      assertQuery("rule:non-existent-rule");
    }
  }

  @Test
  public void byHasDraft() throws Exception {
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

    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();

    assertQuery("has:draft", change2, change1);

    requestContext.setContext(newRequestContext(user2));
    assertQuery("has:draft");
  }

  /**
   * This test does not have a test about drafts computed from All-Users Repository because zombie
   * drafts can't be filtered when computing from All-Users repository. TODO(paiking): During
   * rollout, we should find a way to fix zombie drafts.
   */
  public void byHasDraftExcludesZombieDrafts() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    Change change = insert(repo, newChange(repo));
    Change.Id id = change.getId();

    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = Patch.COMMIT_MSG;
    gApi.changes().id(id.get()).current().createDraft(in);

    assertQuery("has:draft", change);
    assertQuery("commentby:" + userId);

    try (TestRepository<Repo> allUsers =
        new TestRepository<>(repoManager.openRepository(allUsersName))) {
      Ref draftsRef = allUsers.getRepository().exactRef(RefNames.refsDraftComments(id, userId));
      assertThat(draftsRef).isNotNull();

      ReviewInput rin = ReviewInput.dislike();
      rin.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
      gApi.changes().id(id.get()).current().review(rin);

      assertQuery("has:draft");
      assertQuery("commentby:" + userId, change);
      assertThat(allUsers.getRepository().exactRef(draftsRef.getName())).isNull();

      // Re-add drafts ref and ensure it gets filtered out during indexing.
      allUsers.update(draftsRef.getName(), draftsRef.getObjectId());
      assertThat(allUsers.getRepository().exactRef(draftsRef.getName())).isNotNull();
    }

    indexer.index(project, id);
    assertQuery("has:draft");
  }

  @Test
  public void byHasDraftWithManyDrafts() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change[] changesWithDrafts = new Change[30];

    // unrelated change not shown in the result.
    insert(repo, newChange(repo));

    for (int i = 0; i < changesWithDrafts.length; i++) {
      // put the changes in reverse order since this is the order we receive them from the index.
      changesWithDrafts[changesWithDrafts.length - 1 - i] = insert(repo, newChange(repo));
      DraftInput in = new DraftInput();
      in.line = 1;
      in.message = "nit: trailing whitespace";
      in.path = Patch.COMMIT_MSG;
      gApi.changes()
          .id(changesWithDrafts[changesWithDrafts.length - 1 - i].getId().get())
          .current()
          .createDraft(in);
    }
    assertQuery("has:draft", changesWithDrafts);

    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    requestContext.setContext(newRequestContext(user2));
    assertQuery("has:draft");
  }

  @Test
  public void byStarredBy() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    insert(repo, newChange(repo));

    gApi.accounts().self().starChange(change1.getId().toString());
    gApi.accounts().self().starChange(change2.getId().toString());

    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();

    assertQuery("has:star", change2, change1);

    requestContext.setContext(newRequestContext(user2));
    assertQuery("has:star");
  }

  @Test
  public void byStar() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChangeWithStatus(repo, Change.Status.MERGED));

    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    requestContext.setContext(newRequestContext(user2));

    gApi.accounts().self().starChange(change1.getId().toString());

    // check default star
    assertQuery("has:star", change1);
    assertQuery("is:starred", change1);
  }

  @Test
  public void byStarWithManyStars() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change[] changesWithDrafts = new Change[30];
    for (int i = 0; i < changesWithDrafts.length; i++) {
      // put the changes in reverse order since this is the order we receive them from the index.
      changesWithDrafts[changesWithDrafts.length - 1 - i] = insert(repo, newChange(repo));

      // star the change
      gApi.accounts()
          .self()
          .starChange(changesWithDrafts[changesWithDrafts.length - 1 - i].getId().toString());
    }

    // all changes are both starred and ignored.
    assertQuery("is:starred", changesWithDrafts);
  }

  @Test
  public void byFrom() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));

    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    Change change2 = insert(repo, newChange(repo), user2);

    ReviewInput input = new ReviewInput();
    input.message = "toplevel";
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = "inline";
    input.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
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
  @GerritConfig(
      name = "change.mergeabilityComputationBehavior",
      value = "API_REF_UPDATED_AND_CHANGE_REINDEX")
  public void mergeable() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().add("file1", "contents1").create());
    RevCommit commit2 = repo.parseBody(repo.commit().add("file1", "contents2").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    assertQuery("conflicts:" + change1.getId().get(), change2);
    assertQuery("conflicts:" + change2.getId().get(), change1);
    assertQuery("is:mergeable", change2, change1);

    gApi.changes().id(change1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(change1.getChangeId()).current().submit();

    // If a change gets submitted, the remaining open changes get reindexed asynchronously to update
    // their mergeability information. If the further assertions in this test are done before the
    // asynchronous reindex completed they fail because the mergeability information in the index
    // was not updated yet. To avoid this flakiness indexing mergeable is switched off for the
    // tests and we index change2 synchronously here.
    gApi.changes().id(change2.getChangeId()).index();

    assertQuery("status:open conflicts:" + change2.getId().get());
    assertQuery("status:open is:mergeable");
    assertQuery("status:open -is:mergeable", change2);
  }

  @Test
  public void cherrypick() throws Exception {
    assume().that(getSchema().hasField(ChangeField.CHERRY_PICK)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newCherryPickChange(repo, "foo", change1.currentPatchSetId()));

    assertQuery("is:cherrypick", change2);
    assertQuery("-is:cherrypick", change1);
  }

  @Test
  public void merge() throws Exception {
    assume().that(getSchema().hasField(ChangeField.MERGE)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().add("file1", "contents1").create());
    RevCommit commit2 = repo.parseBody(repo.commit().add("file1", "contents2").create());
    RevCommit commit3 =
        repo.parseBody(repo.commit().parent(commit2).add("file1", "contents3").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));
    RevCommit mergeCommit =
        repo.branch("master")
            .commit()
            .message("Merge commit")
            .parent(commit1)
            .parent(commit3)
            .insertChangeId()
            .create();
    Change mergeChange = insert(repo, newChangeForCommit(repo, mergeCommit));

    assertQuery("status:open is:merge", mergeChange);
    assertQuery("status:open -is:merge", change3, change2, change1);
    assertQuery("status:open", mergeChange, change3, change2, change1);
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
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    requestContext.setContext(newRequestContext(user2));

    gApi.changes().id(change2.getId().get()).current().review(new ReviewInput().message("comment"));

    PatchSet.Id ps3_1 = change3.currentPatchSetId();
    change3 = newPatchSet(repo, change3, user);
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

    assertQuery("reviewedby:" + userId.get());

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

    ReviewerInput rin = new ReviewerInput();
    rin.reviewer = user1.toString();
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change1.getId().get()).addReviewer(rin);

    rin = new ReviewerInput();
    rin.reviewer = user1.toString();
    rin.state = ReviewerState.CC;
    gApi.changes().id(change2.getId().get()).addReviewer(rin);

    assertQuery("is:reviewer");
    assertQuery("reviewer:self");
    gApi.changes().id(change3.getChangeId()).current().review(ReviewInput.recommend());
    assertQuery("is:reviewer", change3);
    assertQuery("reviewer:self", change3);

    requestContext.setContext(newRequestContext(user1));
    assertQuery("reviewer:" + user1, change1);
    assertQuery("cc:" + user1, change2);
    assertQuery("is:cc", change2);
    assertQuery("cc:self", change2);
  }

  @Test
  public void byReviewed() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherUser =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
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
    Account.Id user1 =
        accountManager.authenticate(authRequestFactory.createForUser("user1")).getAccountId();
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("user2")).getAccountId();
    Account.Id user3 =
        accountManager.authenticate(authRequestFactory.createForUser("user3")).getAccountId();
    TestRepository<Repo> repo = createProject("repo");

    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    Change change3 = insert(repo, newChange(repo));

    ReviewerInput rin = new ReviewerInput();
    rin.reviewer = user1.toString();
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change1.getId().get()).addReviewer(rin);

    rin = new ReviewerInput();
    rin.reviewer = user2.toString();
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change2.getId().get()).addReviewer(rin);

    rin = new ReviewerInput();
    rin.reviewer = user3.toString();
    rin.state = ReviewerState.CC;
    gApi.changes().id(change3.getId().get()).addReviewer(rin);

    String group = gApi.groups().create("foo").get().name;
    gApi.groups().id(group).addMembers(user2.toString(), user3.toString());

    List<String> members =
        gApi.groups().id(group).members().stream()
            .map(a -> a._accountId.toString())
            .collect(toList());
    assertThat(members).contains(user2.toString());

    assertQuery("reviewerin:\"Registered Users\"", change2, change1);
    assertQuery("reviewerin:" + group, change2);

    gApi.changes().id(change2.getId().get()).current().review(ReviewInput.approve());
    gApi.changes().id(change2.getId().get()).current().submit();

    assertQuery("reviewerin:" + group, change2);
    assertQuery("project:repo reviewerin:" + group, change2);
    assertQuery("status:merged reviewerin:" + group, change2);
  }

  @Test
  public void reviewerAndCcByEmail() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);

    String userByEmail = "un.registered@reviewer.com";
    String userByEmailWithName = "John Doe <" + userByEmail + ">";

    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    insert(repo, newChange(repo));

    ReviewerInput rin = new ReviewerInput();
    rin.reviewer = userByEmailWithName;
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change1.getId().get()).addReviewer(rin);

    rin = new ReviewerInput();
    rin.reviewer = userByEmailWithName;
    rin.state = ReviewerState.CC;
    gApi.changes().id(change2.getId().get()).addReviewer(rin);

    assertQuery("reviewer:\"" + userByEmailWithName + "\"", change1);
    assertQuery("cc:\"" + userByEmailWithName + "\"", change2);

    // Omitting the name:
    assertQuery("reviewer:\"" + userByEmail + "\"", change1);
    assertQuery("cc:\"" + userByEmail + "\"", change2);
  }

  @Test
  public void reviewerAndCcByEmailWithQueryForDifferentUser() throws Exception {
    Project.NameKey project = Project.nameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);

    String userByEmail = "John Doe <un.registered@reviewer.com>";

    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));
    insert(repo, newChange(repo));

    ReviewerInput rin = new ReviewerInput();
    rin.reviewer = userByEmail;
    rin.state = ReviewerState.REVIEWER;
    gApi.changes().id(change1.getId().get()).addReviewer(rin);

    rin = new ReviewerInput();
    rin.reviewer = userByEmail;
    rin.state = ReviewerState.CC;
    gApi.changes().id(change2.getId().get()).addReviewer(rin);

    assertQuery("reviewer:\"someone@example.com\"");
    assertQuery("cc:\"someone@example.com\"");
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

    gApi.changes().id(change1.getId().get()).current().submit();
    assertQuery("is:submittable");
    assertQuery("-is:submittable", change1, change2);
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
    TestRepository<Repo> tr = createProject("repo");
    testByCommitsOnBranchNotMerged(tr, ImmutableSet.of());
  }

  @Test
  public void byCommitsOnBranchNotMergedSkipsMissingChanges() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    ObjectId missing =
        repo.branch(PatchSet.id(Change.id(987654), 1).toRefName())
            .commit()
            .message("No change for this commit")
            .insertChangeId()
            .create()
            .copy();
    testByCommitsOnBranchNotMerged(repo, ImmutableSet.of(missing));
  }

  private void testByCommitsOnBranchNotMerged(TestRepository<Repo> repo, Collection<ObjectId> extra)
      throws Exception {
    int n = 10;
    List<String> shas = new ArrayList<>(n + extra.size());
    extra.forEach(i -> shas.add(i.name()));
    List<Integer> expectedIds = new ArrayList<>(n);
    BranchNameKey dest = null;
    for (int i = 0; i < n; i++) {
      ChangeInserter ins = newChange(repo);
      insert(repo, ins);
      if (dest == null) {
        dest = ins.getChange().getDest();
      }
      shas.add(ins.getCommitId().name());
      expectedIds.add(ins.getChange().getId().get());
    }

    for (int i = 1; i <= 11; i++) {
      Iterable<ChangeData> cds =
          queryProvider.get().byCommitsOnBranchNotMerged(repo.getRepository(), dest, shas, i);
      Iterable<Integer> ids = FluentIterable.from(cds).transform(in -> in.getId().get());
      String name = "limit " + i;
      assertWithMessage(name).that(ids).hasSize(n);
      assertWithMessage(name).that(ids).containsExactlyElementsIn(expectedIds);
    }
  }

  @Test
  public void reindexIfStale() throws Exception {
    Account.Id user = createAccount("user");
    Project.NameKey project = Project.nameKey("repo");
    TestRepository<Repo> repo = createProject(project.get());
    Change change = insert(repo, newChange(repo));
    String changeId = change.getKey().get();
    ChangeNotes notes = notesFactory.create(change.getProject(), change.getId());
    PatchSet ps = psUtil.get(notes, change.currentPatchSetId());

    requestContext.setContext(newRequestContext(user));
    gApi.changes().id(changeId).edit().create();
    assertQuery("has:edit", change);
    assertThat(indexer.reindexIfStale(project, change.getId()).get()).isFalse();

    // Delete edit ref behind index's back.
    RefUpdate ru = repo.getRepository().updateRef(RefNames.refsEdit(user, change.getId(), ps.id()));
    ru.setForceUpdate(true);
    assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);

    // Index is stale.
    assertQuery("has:edit", change);
    assertThat(indexer.reindexIfStale(project, change.getId()).get()).isTrue();
    assertQuery("has:edit");
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

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = "repo";
    pwi.filter = null;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);
    resetUser();

    assertQuery("is:watched", change1);
  }

  @Test
  public void trackingid() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 =
        repo.parseBody(repo.commit().message("Change one\n\nBug:QUERY123").create());
    Change change1 = insert(repo, newChangeForCommit(repo, commit1));
    RevCommit commit2 =
        repo.parseBody(repo.commit().message("Change two\n\nIssue: Issue 16038\n").create());
    Change change2 = insert(repo, newChangeForCommit(repo, commit2));

    RevCommit commit3 =
        repo.parseBody(repo.commit().message("Change two\n\nGoogle-Bug-Id: b/16039\n").create());
    Change change3 = insert(repo, newChangeForCommit(repo, commit3));

    assertQuery("tr:QUERY123", change1);
    assertQuery("bug:QUERY123", change1);
    assertQuery("tr:16038", change2);
    assertQuery("bug:16038", change2);
    assertQuery("tr:16039", change3);
    assertQuery("bug:16039", change3);
    assertQuery("tr:QUERY-123");
    assertQuery("bug:QUERY-123");
    assertQuery("tr:QUERY12");
    assertQuery("bug:QUERY12");
    assertQuery("tr:QUERY789");
    assertQuery("bug:QUERY789");
  }

  @Test
  public void defaultFieldWithManyUsers() throws Exception {
    for (int i = 0; i < ChangeQueryBuilder.MAX_ACCOUNTS_PER_DEFAULT_FIELD * 2; i++) {
      createAccount("user" + i, "User " + i, "user" + i + "@example.com", true);
    }
    assertQuery("us");
  }

  @Test
  public void revertOf() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    // Create two commits and revert second commit (initial commit can't be reverted)
    Change initial = insert(repo, newChange(repo));
    gApi.changes().id(initial.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(initial.getChangeId()).current().submit();

    ChangeInfo changeToRevert =
        gApi.changes().create(new ChangeInput("repo", "master", "commit to revert")).get();
    gApi.changes().id(changeToRevert.id).current().review(ReviewInput.approve());
    gApi.changes().id(changeToRevert.id).current().submit();

    ChangeInfo changeThatReverts = gApi.changes().id(changeToRevert.id).revert().get();
    assertQueryByIds("revertof:" + changeToRevert._number, Change.id(changeThatReverts._number));
  }

  @Test
  public void submissionId() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));
    // create irrelevant change
    insert(repo, newChange(repo));
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(change.getChangeId()).current().submit();
    String submissionId = gApi.changes().id(change.getChangeId()).get().submissionId;

    assertQueryByIds("submissionid:" + submissionId, change.getId());
  }

  /** Change builder for helping in tests for dashboard sections. */
  protected class DashboardChangeState {
    private final Account.Id ownerId;
    private final List<Account.Id> reviewedBy;
    private final List<Account.Id> cced;
    private final List<Account.Id> draftCommentBy;
    private final List<Account.Id> deleteDraftCommentBy;
    private boolean wip;
    private boolean abandoned;
    @Nullable private Account.Id mergedBy;
    @Nullable private Account.Id assigneeId;

    @Nullable Change.Id id;

    DashboardChangeState(Account.Id ownerId) {
      this.ownerId = ownerId;
      reviewedBy = new ArrayList<>();
      cced = new ArrayList<>();
      draftCommentBy = new ArrayList<>();
      deleteDraftCommentBy = new ArrayList<>();
    }

    DashboardChangeState assignTo(Account.Id assigneeId) {
      this.assigneeId = assigneeId;
      return this;
    }

    DashboardChangeState wip() {
      wip = true;
      return this;
    }

    DashboardChangeState abandon() {
      abandoned = true;
      return this;
    }

    DashboardChangeState mergeBy(Account.Id mergedBy) {
      this.mergedBy = mergedBy;
      return this;
    }

    DashboardChangeState addReviewer(Account.Id reviewerId) {
      reviewedBy.add(reviewerId);
      return this;
    }

    DashboardChangeState addCc(Account.Id ccId) {
      cced.add(ccId);
      return this;
    }

    DashboardChangeState draftCommentBy(Account.Id commenterId) {
      draftCommentBy.add(commenterId);
      return this;
    }

    DashboardChangeState draftAndDeleteCommentBy(Account.Id commenterId) {
      deleteDraftCommentBy.add(commenterId);
      return this;
    }

    DashboardChangeState create(TestRepository<Repo> repo) throws Exception {
      requestContext.setContext(newRequestContext(ownerId));
      Change change = insert(repo, newChange(repo), ownerId);
      id = change.getId();
      ChangeApi cApi = gApi.changes().id(change.getChangeId());
      if (assigneeId != null) {
        AssigneeInput in = new AssigneeInput();
        in.assignee = "" + assigneeId;
        cApi.setAssignee(in);
      }
      if (wip) {
        cApi.setWorkInProgress();
      }
      if (abandoned) {
        cApi.abandon();
      }
      for (Account.Id reviewerId : reviewedBy) {
        cApi.addReviewer("" + reviewerId);
      }
      for (Account.Id reviewerId : cced) {
        ReviewerInput in = new ReviewerInput();
        in.reviewer = reviewerId.toString();
        in.state = ReviewerState.CC;
        cApi.addReviewer(in);
      }
      DraftInput in = new DraftInput();
      in.path = Patch.COMMIT_MSG;
      in.message = "message";
      for (Account.Id commenterId : draftCommentBy) {
        requestContext.setContext(newRequestContext(commenterId));
        gApi.changes().id(change.getChangeId()).current().createDraft(in);
      }
      for (Account.Id commenterId : deleteDraftCommentBy) {
        requestContext.setContext(newRequestContext(commenterId));
        gApi.changes().id(change.getChangeId()).current().createDraft(in).delete();
      }
      if (mergedBy != null) {
        requestContext.setContext(newRequestContext(mergedBy));
        cApi = gApi.changes().id(change.getChangeId());
        cApi.current().review(ReviewInput.approve());
        cApi.current().submit();
      }
      requestContext.setContext(newRequestContext(user.getAccountId()));
      return this;
    }
  }

  protected List<ChangeInfo> assertDashboardQuery(
      String viewedUser, String query, DashboardChangeState... expected) throws Exception {
    Change.Id[] ids = new Change.Id[expected.length];
    for (int i = 0; i < expected.length; i++) {
      ids[i] = expected[i].id;
    }
    return assertQueryByIds(query.replaceAll("\\$\\{user}", viewedUser), ids);
  }

  protected List<ChangeInfo> assertDashboardQueryWithStart(
      String viewedUser, String query, int start, DashboardChangeState... expected)
      throws Exception {
    Change.Id[] ids = new Change.Id[expected.length];
    for (int i = 0; i < expected.length; i++) {
      ids[i] = expected[i].id;
    }
    QueryRequest queryRequest = newQuery(query.replaceAll("\\$\\{user}", viewedUser));
    queryRequest.withStart(start);
    return assertQueryByIds(queryRequest, ids);
  }

  @Test
  public void dashboardHasUnpublishedDrafts() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherAccountId = createAccount("other");
    DashboardChangeState hasUnpublishedDraft =
        new DashboardChangeState(otherAccountId).draftCommentBy(user.getAccountId()).create(repo);

    // Create changes that should not be returned by query.
    new DashboardChangeState(user.getAccountId()).create(repo);
    new DashboardChangeState(user.getAccountId()).draftCommentBy(otherAccountId).create(repo);
    new DashboardChangeState(user.getAccountId())
        .draftAndDeleteCommentBy(user.getAccountId())
        .create(repo);

    assertDashboardQuery(
        "self", IndexPreloadingUtil.DASHBOARD_HAS_UNPUBLISHED_DRAFTS_QUERY, hasUnpublishedDraft);
  }

  @Test
  public void dashboardAssignedReviews() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherAccountId = createAccount("other");
    DashboardChangeState otherOpenWip =
        new DashboardChangeState(otherAccountId).wip().assignTo(user.getAccountId()).create(repo);
    DashboardChangeState selfOpenWip =
        new DashboardChangeState(user.getAccountId())
            .wip()
            .assignTo(user.getAccountId())
            .create(repo);

    // Create changes that should not be returned by query.
    new DashboardChangeState(user.getAccountId()).assignTo(user.getAccountId()).abandon();
    new DashboardChangeState(user.getAccountId())
        .assignTo(user.getAccountId())
        .mergeBy(user.getAccountId());

    assertDashboardQuery(
        "self", IndexPreloadingUtil.DASHBOARD_ASSIGNED_QUERY, selfOpenWip, otherOpenWip);

    // Viewing another user's dashboard.
    requestContext.setContext(newRequestContext(otherAccountId));
    assertDashboardQuery(
        user.getUserName().get(), IndexPreloadingUtil.DASHBOARD_ASSIGNED_QUERY, otherOpenWip);
  }

  @Test
  public void dashboardWorkInProgressReviews() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    DashboardChangeState ownedOpenWip =
        new DashboardChangeState(user.getAccountId()).wip().create(repo);

    // Create changes that should not be returned by query.
    new DashboardChangeState(user.getAccountId()).wip().abandon().create(repo);
    new DashboardChangeState(user.getAccountId()).mergeBy(user.getAccountId()).create(repo);
    new DashboardChangeState(createAccount("other")).wip().create(repo);

    assertDashboardQuery(
        "self", IndexPreloadingUtil.DASHBOARD_WORK_IN_PROGRESS_QUERY, ownedOpenWip);
  }

  @Test
  public void dashboardOutgoingReviews() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherAccountId = createAccount("other");
    DashboardChangeState ownedOpenReviewable =
        new DashboardChangeState(user.getAccountId()).create(repo);

    // Create changes that should not be returned by any queries in this test.
    new DashboardChangeState(user.getAccountId()).wip().create(repo);
    new DashboardChangeState(otherAccountId).create(repo);

    // Viewing one's own dashboard.
    assertDashboardQuery("self", IndexPreloadingUtil.DASHBOARD_OUTGOING_QUERY, ownedOpenReviewable);

    // Viewing another user's dashboard.
    requestContext.setContext(newRequestContext(otherAccountId));
    assertDashboardQuery(
        user.getUserName().get(),
        IndexPreloadingUtil.DASHBOARD_OUTGOING_QUERY,
        ownedOpenReviewable);
  }

  @Test
  public void dashboardIncomingReviews() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherAccountId = createAccount("other");
    DashboardChangeState reviewingReviewable =
        new DashboardChangeState(otherAccountId).addReviewer(user.getAccountId()).create(repo);
    DashboardChangeState assignedReviewable =
        new DashboardChangeState(otherAccountId).assignTo(user.getAccountId()).create(repo);

    // Create changes that should not be returned by any queries in this test.
    new DashboardChangeState(otherAccountId).wip().addReviewer(user.getAccountId()).create(repo);
    new DashboardChangeState(otherAccountId).wip().assignTo(user.getAccountId()).create(repo);
    new DashboardChangeState(otherAccountId).addReviewer(otherAccountId).create(repo);
    new DashboardChangeState(otherAccountId)
        .addReviewer(user.getAccountId())
        .mergeBy(user.getAccountId())
        .create(repo);

    // Viewing one's own dashboard.
    assertDashboardQuery(
        "self",
        IndexPreloadingUtil.DASHBOARD_INCOMING_QUERY,
        assignedReviewable,
        reviewingReviewable);

    // Viewing another user's dashboard.
    requestContext.setContext(newRequestContext(otherAccountId));
    assertDashboardQuery(
        user.getUserName().get(),
        IndexPreloadingUtil.DASHBOARD_INCOMING_QUERY,
        assignedReviewable,
        reviewingReviewable);
  }

  @Test
  public void dashboardRecentlyClosedReviews() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Account.Id otherAccountId = createAccount("other");
    DashboardChangeState mergedOwned =
        new DashboardChangeState(user.getAccountId()).mergeBy(user.getAccountId()).create(repo);
    DashboardChangeState mergedReviewing =
        new DashboardChangeState(otherAccountId)
            .addReviewer(user.getAccountId())
            .mergeBy(user.getAccountId())
            .create(repo);
    DashboardChangeState mergedCced =
        new DashboardChangeState(otherAccountId)
            .addCc(user.getAccountId())
            .mergeBy(user.getAccountId())
            .create(repo);
    DashboardChangeState mergedAssigned =
        new DashboardChangeState(otherAccountId)
            .assignTo(user.getAccountId())
            .mergeBy(user.getAccountId())
            .create(repo);
    DashboardChangeState abandonedOwned =
        new DashboardChangeState(user.getAccountId()).abandon().create(repo);
    DashboardChangeState abandonedOwnedWip =
        new DashboardChangeState(user.getAccountId()).wip().abandon().create(repo);
    DashboardChangeState abandonedReviewing =
        new DashboardChangeState(otherAccountId)
            .addReviewer(user.getAccountId())
            .abandon()
            .create(repo);
    DashboardChangeState abandonedAssigned =
        new DashboardChangeState(otherAccountId)
            .assignTo(user.getAccountId())
            .abandon()
            .create(repo);
    DashboardChangeState abandonedAssignedWip =
        new DashboardChangeState(otherAccountId)
            .assignTo(user.getAccountId())
            .wip()
            .abandon()
            .create(repo);

    // Create changes that should not be returned by any queries in this test.
    new DashboardChangeState(otherAccountId)
        .addReviewer(user.getAccountId())
        .wip()
        .abandon()
        .create(repo);

    // Viewing one's own dashboard.
    assertDashboardQuery(
        "self",
        IndexPreloadingUtil.DASHBOARD_RECENTLY_CLOSED_QUERY,
        abandonedAssigned,
        abandonedReviewing,
        abandonedOwnedWip,
        abandonedOwned,
        mergedAssigned,
        mergedCced,
        mergedReviewing,
        mergedOwned);

    // Viewing another user's dashboard.
    requestContext.setContext(newRequestContext(otherAccountId));
    assertDashboardQuery(
        user.getUserName().get(),
        IndexPreloadingUtil.DASHBOARD_RECENTLY_CLOSED_QUERY,
        abandonedAssignedWip,
        abandonedAssigned,
        abandonedReviewing,
        abandonedOwned,
        mergedAssigned,
        mergedCced,
        mergedReviewing,
        mergedOwned);
  }

  @Test
  public void attentionSetIndexed() throws Exception {
    assume().that(getSchema().hasField(ChangeField.ATTENTION_SET_USERS)).isTrue();
    assume().that(getSchema().hasField(ChangeField.ATTENTION_SET_USERS_COUNT)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    AttentionSetInput input = new AttentionSetInput(userId.toString(), "some reason");
    gApi.changes().id(change1.getChangeId()).addToAttentionSet(input);

    assertQuery("is:attention", change1);
    assertQuery("-is:attention", change2);
    assertQuery("has:attention", change1);
    assertQuery("-has:attention", change2);
    assertQuery("attention:" + user.getUserName().get(), change1);
    assertQuery("-attention:" + userId.toString(), change2);

    gApi.changes()
        .id(change1.getChangeId())
        .attention(userId.toString())
        .remove(new AttentionSetInput("removed again"));
    assertQuery("-is:attention", change1, change2);
  }

  @Test
  public void attentionSetStored() throws Exception {
    assume().that(getSchema().hasField(ChangeField.ATTENTION_SET_USERS)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));

    AttentionSetInput input = new AttentionSetInput(userId.toString(), "reason 1");
    gApi.changes().id(change.getChangeId()).addToAttentionSet(input);
    Account.Id user2Id =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();

    // Add the second user as cc to ensure that user took part of the change and can be added to the
    // attention set.
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.reviewer = user2Id.toString();
    reviewerInput.state = ReviewerState.CC;
    gApi.changes().id(change.getChangeId()).addReviewer(reviewerInput);

    input = new AttentionSetInput(user2Id.toString(), "reason 2");
    gApi.changes().id(change.getChangeId()).addToAttentionSet(input);

    List<ChangeInfo> result = newQuery("attention:" + user2Id.toString()).get();
    assertThat(result).hasSize(1);
    ChangeInfo changeInfo = Iterables.getOnlyElement(result);
    assertThat(changeInfo.attentionSet).isNotNull();
    assertThat(changeInfo.attentionSet.keySet()).containsExactly(userId.get(), user2Id.get());
    assertThat(changeInfo.attentionSet.get(userId.get()).reason).isEqualTo("reason 1");
    assertThat(changeInfo.attentionSet.get(user2Id.get()).reason).isEqualTo("reason 2");
  }

  @Test
  public void assignee() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChange(repo));

    AssigneeInput input = new AssigneeInput();
    input.assignee = user.getUserName().get();
    gApi.changes().id(change1.getChangeId()).setAssignee(input);

    assertQuery("is:assigned", change1);
    assertQuery("-is:assigned", change2);
    assertQuery("is:unassigned", change2);
    assertQuery("-is:unassigned", change1);
    assertQuery("assignee:" + user.getUserName().get(), change1);
    assertQuery("-assignee:" + user.getUserName().get(), change2);
  }

  @GerritConfig(name = "accounts.visibility", value = "NONE")
  @Test
  public void userDestination() throws Exception {
    TestRepository<Repo> repo1 = createProject("repo1");
    Change change1 = insert(repo1, newChange(repo1));
    TestRepository<Repo> repo2 = createProject("repo2");
    Change change2 = insert(repo2, newChange(repo2));

    assertThatQueryException("destination:foo")
        .hasMessageThat()
        .isEqualTo("Unknown named destination: foo");

    Account.Id anotherUserId =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    String destination1 = "refs/heads/master\trepo1";
    String destination2 = "refs/heads/master\trepo2";
    String destination3 = "refs/heads/master\trepo1\nrefs/heads/master\trepo2";
    String destination4 = "refs/heads/master\trepo3";
    String destination5 = "refs/heads/other\trepo1";

    try (TestRepository<Repo> allUsers =
        new TestRepository<>(repoManager.openRepository(allUsersName))) {
      String refsUsers = RefNames.refsUsers(userId);
      allUsers.branch(refsUsers).commit().add("destinations/destination1", destination1).create();
      allUsers.branch(refsUsers).commit().add("destinations/destination2", destination2).create();
      allUsers.branch(refsUsers).commit().add("destinations/destination3", destination3).create();
      allUsers.branch(refsUsers).commit().add("destinations/destination4", destination4).create();
      allUsers.branch(refsUsers).commit().add("destinations/destination5", destination5).create();

      String anotherRefsUsers = RefNames.refsUsers(anotherUserId);
      allUsers
          .branch(anotherRefsUsers)
          .commit()
          .add("destinations/destination6", destination1)
          .create();
      allUsers
          .branch(anotherRefsUsers)
          .commit()
          .add("destinations/destination7", destination2)
          .create();
      allUsers
          .branch(anotherRefsUsers)
          .commit()
          .add("destinations/destination8", destination3)
          .create();
      allUsers
          .branch(anotherRefsUsers)
          .commit()
          .add("destinations/destination9", destination4)
          .create();

      Ref userRef = allUsers.getRepository().exactRef(refsUsers);
      Ref anotherUserRef = allUsers.getRepository().exactRef(anotherRefsUsers);
      assertThat(userRef).isNotNull();
      assertThat(anotherUserRef).isNotNull();
    }

    assertQuery("destination:destination1", change1);
    assertQuery("destination:destination2", change2);
    assertQuery("destination:destination3", change2, change1);
    assertQuery("destination:destination4");
    assertQuery("destination:destination5");
    assertQuery("destination:destination6,user=" + anotherUserId, change1);
    assertQuery("destination:name=destination6,user=" + anotherUserId, change1);
    assertQuery("destination:user=" + anotherUserId + ",destination7", change2);
    assertQuery("destination:user=" + anotherUserId + ",name=destination8", change2, change1);
    assertQuery("destination:destination9,user=" + anotherUserId);

    assertThatQueryException("destination:destination3,user=" + anotherUserId)
        .hasMessageThat()
        .isEqualTo("Unknown named destination: destination3");
    assertThatQueryException("destination:destination3,user=test")
        .hasMessageThat()
        .isEqualTo("Account 'test' not found");

    requestContext.setContext(newRequestContext(anotherUserId));
    // account 1000000 is not visible to 'anotheruser' as they are not an admin
    assertThatQueryException("destination:destination3,user=" + userId)
        .hasMessageThat()
        .isEqualTo("Account '1000000' not found");
  }

  @GerritConfig(name = "accounts.visibility", value = "NONE")
  @Test
  public void userQuery() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change1 = insert(repo, newChange(repo));
    Change change2 = insert(repo, newChangeForBranch(repo, "stable"));

    Account.Id anotherUserId =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();
    String queryListText =
        "query1\tproject:repo\n"
            + "query2\tproject:repo status:open\n"
            + "query3\tproject:repo branch:stable\n"
            + "query4\tproject:repo branch:other";
    String anotherQueryListText =
        "query5\tproject:repo\n"
            + "query6\tproject:repo status:merged\n"
            + "query7\tproject:repo branch:stable\n"
            + "query8\tproject:repo branch:other";

    try (TestRepository<Repo> allUsers =
            new TestRepository<>(repoManager.openRepository(allUsersName));
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsersName);
        MetaDataUpdate anotherMd = metaDataUpdateFactory.create(allUsersName)) {
      VersionedAccountQueries queries = VersionedAccountQueries.forUser(userId);
      queries.load(md);
      queries.setQueryList(queryListText);
      queries.commit(md);
      VersionedAccountQueries anotherQueries = VersionedAccountQueries.forUser(anotherUserId);
      anotherQueries.load(anotherMd);
      anotherQueries.setQueryList(anotherQueryListText);
      anotherQueries.commit(anotherMd);
    }

    assertThatQueryException("query:foo").hasMessageThat().isEqualTo("Unknown named query: foo");
    assertThatQueryException("query:query1,user=" + anotherUserId)
        .hasMessageThat()
        .isEqualTo("Unknown named query: query1");
    assertThatQueryException("query:query1,user=test")
        .hasMessageThat()
        .isEqualTo("Account 'test' not found");

    requestContext.setContext(newRequestContext(anotherUserId));
    // account 1000000 is not visible to 'anotheruser' as they are not an admin
    assertThatQueryException("query:query1,user=" + userId)
        .hasMessageThat()
        .isEqualTo("Account '1000000' not found");
    requestContext.setContext(newRequestContext(userId));

    assertQuery("query:query1", change2, change1);
    assertQuery("query:query2", change2, change1);
    assertQuery("query:name=query5,user=" + anotherUserId, change2, change1);
    assertQuery("query:user=" + anotherUserId + ",name=query6");
    gApi.changes().id(change1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(change1.getChangeId()).current().submit();
    assertQuery("query:query2", change2);
    assertQuery("query:query3", change2);
    assertQuery("query:query4");
    assertQuery("query:query6,user=" + anotherUserId, change1);
    assertQuery("query:user=" + anotherUserId + ",query7", change2);
    assertQuery("query:query8,user=" + anotherUserId);
  }

  @Test
  public void byOwnerInvalidQuery() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    insert(repo, newChange(repo), userId);
    String nameEmail = user.asIdentifiedUser().getNameEmail();
    assertQuery("owner: \"" + nameEmail + "\"\\");
  }

  @Test
  public void byDeletedChange() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));

    String query = "change:" + change.getId();
    assertQuery(query, change);

    gApi.changes().id(change.getChangeId()).delete();
    assertQuery(query);
  }

  @Test
  public void byUrlEncodedProject() throws Exception {
    TestRepository<Repo> repo = createProject("repo+foo");
    Change change = insert(repo, newChange(repo));
    assertQuery("project:repo+foo", change);
  }

  @Test
  public void bySubmitRequirement_notAllowed() throws Exception {
    Exception thrown =
        assertThrows(
            QueryParseException.class,
            () ->
                queryProcessorProvider
                    .get()
                    .query(
                        new SubmitRequirementPredicate("submit-requirement", "value") {
                          @Override
                          public boolean match(ChangeData object) {
                            return false;
                          }

                          @Override
                          public int getCost() {
                            return 0;
                          }
                        }));

    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Operator 'submit-requirement:value' cannot be used in queries");
  }

  @Test
  public void isPureRevert() throws Exception {
    assume().that(getSchema().hasField(ChangeField.IS_PURE_REVERT)).isTrue();
    TestRepository<Repo> repo = createProject("repo");
    // Create two commits and revert second commit (initial commit can't be reverted)
    Change initial = insert(repo, newChange(repo));
    gApi.changes().id(initial.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(initial.getChangeId()).current().submit();

    ChangeInfo changeToRevert =
        gApi.changes().create(new ChangeInput("repo", "master", "commit to revert")).get();
    gApi.changes().id(changeToRevert.id).current().review(ReviewInput.approve());
    gApi.changes().id(changeToRevert.id).current().submit();

    ChangeInfo changeThatReverts = gApi.changes().id(changeToRevert.id).revert().get();
    Change.Id changeThatRevertsId = Change.id(changeThatReverts._number);
    assertQueryByIds("is:pure-revert", changeThatRevertsId);

    // Update the change that reverts such that it's not a pure revert
    gApi.changes()
        .id(changeThatReverts.id)
        .edit()
        .modifyFile("some-file.txt", RawInputUtil.create("newcontent".getBytes(UTF_8)));
    gApi.changes().id(changeThatReverts.id).edit().publish();
    assertQueryByIds("is:pure-revert");
  }

  @Test
  public void selfFailsForAnonymousUser() throws Exception {
    for (String query : ImmutableList.of("assignee:self", "has:star", "is:starred")) {
      assertQuery(query);
      RequestContext oldContext = requestContext.setContext(anonymousUserProvider::get);

      try {
        requestContext.setContext(anonymousUserProvider::get);
        assertThatAuthException(query)
            .hasMessageThat()
            .isEqualTo("Must be signed-in to use this operator");
      } finally {
        requestContext.setContext(oldContext);
      }
    }
  }

  @Test
  public void selfSucceedsForInactiveAccount() throws Exception {
    Account.Id user2 =
        accountManager.authenticate(authRequestFactory.createForUser("anotheruser")).getAccountId();

    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));
    gApi.changes().id(change.getId().get()).addReviewer(user2.toString());

    RequestContext adminContext = requestContext.setContext(newRequestContext(user2));
    assertQuery("reviewer:self", change);

    requestContext.setContext(adminContext);
    gApi.accounts().id(user2.get()).setActive(false);

    requestContext.setContext(newRequestContext(user2));
    assertQuery("reviewer:self", change);
  }

  @Test
  public void none() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    Change change = insert(repo, newChange(repo));

    assertQuery(ChangeIndexPredicate.none());

    for (Predicate<ChangeData> matchingOneChange :
        ImmutableList.of(
            // One index query, one post-filtering query.
            queryBuilder.parse(change.getId().toString()),
            queryBuilder.parse("ownerin:Administrators"))) {
      assertQuery(matchingOneChange, change);
      assertQuery(Predicate.or(ChangeIndexPredicate.none(), matchingOneChange), change);
      assertQuery(Predicate.and(ChangeIndexPredicate.none(), matchingOneChange));
      assertQuery(
          Predicate.and(Predicate.not(ChangeIndexPredicate.none()), matchingOneChange), change);
    }
  }

  @Test
  @GerritConfig(name = "change.mergeabilityComputationBehavior", value = "NEVER")
  public void mergeableFailsWhenNotIndexed() throws Exception {
    TestRepository<Repo> repo = createProject("repo");
    RevCommit commit1 = repo.parseBody(repo.commit().add("file1", "contents1").create());
    insert(repo, newChangeForCommit(repo, commit1));

    Throwable thrown = assertThrows(Throwable.class, () -> assertQuery("status:open is:mergeable"));
    assertThat(thrown.getCause()).isInstanceOf(QueryParseException.class);
    assertThat(thrown)
        .hasMessageThat()
        .contains("'is:mergeable' operator is not supported by server");
  }

  protected ChangeInserter newChange(TestRepository<Repo> repo) throws Exception {
    return newChange(repo, null, null, null, null, null, false, false);
  }

  protected ChangeInserter newChangeForCommit(TestRepository<Repo> repo, RevCommit commit)
      throws Exception {
    return newChange(repo, commit, null, null, null, null, false, false);
  }

  protected ChangeInserter newChangeWithFiles(TestRepository<Repo> repo, String... paths)
      throws Exception {
    TestRepository<?>.CommitBuilder b = repo.commit().message("Change with files");
    for (String path : paths) {
      b.add(path, "contents of " + path);
    }
    return newChangeForCommit(repo, repo.parseBody(b.create()));
  }

  protected ChangeInserter newChangeForBranch(TestRepository<Repo> repo, String branch)
      throws Exception {
    return newChange(repo, null, branch, null, null, null, false, false);
  }

  protected ChangeInserter newChangeWithStatus(TestRepository<Repo> repo, Change.Status status)
      throws Exception {
    return newChange(repo, null, null, status, null, null, false, false);
  }

  protected ChangeInserter newChangeWithTopic(TestRepository<Repo> repo, String topic)
      throws Exception {
    return newChange(repo, null, null, null, topic, null, false, false);
  }

  protected ChangeInserter newChangeWorkInProgress(TestRepository<Repo> repo) throws Exception {
    return newChange(repo, null, null, null, null, null, true, false);
  }

  protected ChangeInserter newChangePrivate(TestRepository<Repo> repo) throws Exception {
    return newChange(repo, null, null, null, null, null, false, true);
  }

  protected ChangeInserter newCherryPickChange(
      TestRepository<Repo> repo, String branch, PatchSet.Id cherryPickOf) throws Exception {
    return newChange(repo, null, branch, null, null, cherryPickOf, false, true);
  }

  protected ChangeInserter newChange(
      TestRepository<Repo> repo,
      @Nullable RevCommit commit,
      @Nullable String branch,
      @Nullable Change.Status status,
      @Nullable String topic,
      @Nullable PatchSet.Id cherryPickOf,
      boolean workInProgress,
      boolean isPrivate)
      throws Exception {
    if (commit == null) {
      commit = repo.parseBody(repo.commit().message("message").create());
    }

    branch = MoreObjects.firstNonNull(branch, "refs/heads/master");
    if (!branch.startsWith("refs/heads/")) {
      branch = "refs/heads/" + branch;
    }

    Change.Id id = Change.id(seq.nextChangeId());
    ChangeInserter ins =
        changeFactory
            .create(id, commit, branch)
            .setValidate(false)
            .setStatus(status)
            .setTopic(topic)
            .setWorkInProgress(workInProgress)
            .setPrivate(isPrivate)
            .setCherryPickOf(cherryPickOf);
    return ins;
  }

  protected Change insert(TestRepository<Repo> repo, ChangeInserter ins) throws Exception {
    return insert(repo, ins, null, TimeUtil.now());
  }

  protected Change insert(TestRepository<Repo> repo, ChangeInserter ins, @Nullable Account.Id owner)
      throws Exception {
    return insert(repo, ins, owner, TimeUtil.now());
  }

  protected Change insert(
      TestRepository<Repo> repo, ChangeInserter ins, @Nullable Account.Id owner, Instant createdOn)
      throws Exception {
    Project.NameKey project =
        Project.nameKey(repo.getRepository().getDescription().getRepositoryName());
    Account.Id ownerId = owner != null ? owner : userId;
    IdentifiedUser user = userFactory.create(ownerId);
    try (BatchUpdate bu = updateFactory.create(project, user, createdOn)) {
      bu.insertChange(ins);
      bu.execute();
      return ins.getChange();
    }
  }

  protected Change newPatchSet(TestRepository<Repo> repo, Change c, CurrentUser user)
      throws Exception {
    // Add a new file so the patch set is not a trivial rebase, to avoid default
    // Code-Review label copying.
    int n = c.currentPatchSetId().get() + 1;
    RevCommit commit =
        repo.parseBody(repo.commit().message("message").add("file" + n, "contents " + n).create());

    PatchSetInserter inserter =
        patchSetFactory
            .create(changeNotesFactory.createChecked(c), PatchSet.id(c.getId(), n), commit)
            .setFireRevisionCreated(false)
            .setValidate(false);
    try (BatchUpdate bu = updateFactory.create(c.getProject(), user, TimeUtil.now());
        ObjectInserter oi = repo.getRepository().newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      bu.setRepository(repo.getRepository(), rw, oi);
      bu.setNotify(NotifyResolver.Result.none());
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

  protected ThrowableSubject assertThatAuthException(Object query) throws Exception {
    try {
      newQuery(query).get();
      throw new AssertionError("expected AuthException for query: " + query);
    } catch (AuthException e) {
      return assertThat(e);
    }
  }

  protected TestRepository<Repo> createProject(String name) throws Exception {
    gApi.projects().create(name).get();
    return new TestRepository<>(repoManager.openRepository(Project.nameKey(name)));
  }

  protected TestRepository<Repo> createProject(String name, String parent) throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = name;
    input.parent = parent;
    gApi.projects().create(input).get();
    return new TestRepository<>(repoManager.openRepository(Project.nameKey(name)));
  }

  protected QueryRequest newQuery(Object query) {
    return gApi.changes().query(query.toString());
  }

  protected List<ChangeInfo> assertQuery(Object query, Change... changes) throws Exception {
    return assertQuery(newQuery(query), changes);
  }

  protected List<ChangeInfo> assertQueryByIds(Object query, Change.Id... changes) throws Exception {
    return assertQueryByIds(newQuery(query), changes);
  }

  protected List<ChangeInfo> assertQuery(QueryRequest query, Change... changes) throws Exception {
    return assertQueryByIds(
        query, Arrays.stream(changes).map(Change::getId).toArray(Change.Id[]::new));
  }

  protected List<ChangeInfo> assertQueryByIds(QueryRequest query, Change.Id... changes)
      throws Exception {
    List<ChangeInfo> result = query.get();
    Iterable<Change.Id> ids = ids(result);
    assertWithMessage(format(query.getQuery(), ids, changes))
        .that(ids)
        .containsExactlyElementsIn(Arrays.asList(changes))
        .inOrder();
    return result;
  }

  protected void assertQuery(Predicate<ChangeData> predicate, Change... changes) throws Exception {
    ImmutableList<Change.Id> actualIds =
        queryProvider.get().query(predicate).stream()
            .map(ChangeData::getId)
            .collect(toImmutableList());
    Change.Id[] expectedIds = Arrays.stream(changes).map(Change::getId).toArray(Change.Id[]::new);
    assertWithMessage(format(predicate.toString(), actualIds, expectedIds))
        .that(actualIds)
        .containsExactlyElementsIn(expectedIds)
        .inOrder();
  }

  private String format(String query, Iterable<Change.Id> actualIds, Change.Id... expectedChanges)
      throws RestApiException {
    return "query '"
        + query
        + "' with expected changes "
        + format(Arrays.asList(expectedChanges))
        + " and result "
        + format(actualIds);
  }

  private String format(Iterable<Change.Id> changeIds) throws RestApiException {
    return format(changeIds.iterator());
  }

  private String format(Iterator<Change.Id> changeIds) throws RestApiException {
    StringBuilder b = new StringBuilder();
    b.append("[");
    while (changeIds.hasNext()) {
      Change.Id id = changeIds.next();
      ChangeInfo c = gApi.changes().id(id.get()).get();
      b.append("{")
          .append(id)
          .append(" (")
          .append(c.changeId)
          .append("), ")
          .append("dest=")
          .append(BranchNameKey.create(Project.nameKey(c.project), c.branch))
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

  protected static Iterable<Change.Id> ids(Change... changes) {
    return Arrays.stream(changes).map(Change::getId).collect(toList());
  }

  protected static Iterable<Change.Id> ids(Iterable<ChangeInfo> changes) {
    return Streams.stream(changes).map(c -> Change.id(c._number)).collect(toList());
  }

  protected static long lastUpdatedMs(Change c) {
    return c.getLastUpdatedOn().toEpochMilli();
  }

  // Get the last  updated time from ChangeApi
  protected long lastUpdatedMsApi(Change c) throws Exception {
    return gApi.changes().id(c.getChangeId()).get().updated.getTime();
  }

  protected void approve(Change change) throws Exception {
    gApi.changes().id(change.getChangeId()).current().review(ReviewInput.approve());
  }

  protected void submit(Change change) throws Exception {
    approve(change);
    gApi.changes().id(change.getChangeId()).current().submit();
  }

  /**
   * Generates a search query to test {@link com.google.gerrit.index.query.Matchable} implementation
   * of change {@link IndexPredicate}
   *
   * <p>This code path requires triggering the condition, when
   *
   * <ol>
   *   <li>The query is rewritten into multiple {@link IndexedChangeQuery} by {@link
   *       com.google.gerrit.server.index.change.ChangeIndexRewriter#rewrite}
   *   <li>The changes are returned from the index by the first {@link IndexedChangeQuery}
   *   <li>Then constrained in {@link com.google.gerrit.index.query.AndSource#match} by applying all
   *       parsed predicates from the search query
   *   <li>Thus, the rest of {@link IndexedChangeQuery} work as filters on the index results, see
   *       {@link IndexedChangeQuery#match}
   * </ol>
   *
   * The constructed query only constrains by the passed searchTerm for the operator that is being
   * tested (for all changes without a reviewer):
   *
   * <ul>
   *   <li>The search term 'status:new OR status:merged OR status:abandoned' is used to return all
   *       changes from the search index.
   *   <li>The non-indexed search term 'reviewerin:"Empty Group"' is only used to make the right AND
   *       operand work as a filter (not a data source).
   *   <li>See how it is rewritten in {@link
   *       com.google.gerrit.server.index.change.ChangeIndexRewriterTest#threeLevelTreeWithMultipleSources}
   * </ul>
   *
   * @param searchTerm change search term that maps to {@link IndexPredicate} and needs to be tested
   *     as filter
   * @return a search query that allows to test the {@code searchTerm} as a filter.
   */
  protected String makeIndexedPredicateFilterQuery(String searchTerm) throws Exception {
    String emptyGroupName = "Empty Group";
    if (gApi.groups().query(emptyGroupName).get().isEmpty()) {
      createGroup(emptyGroupName, "Administrators");
    }
    String queryPattern =
        "(status:new OR status:merged OR status:abandoned) AND (reviewerin:\"%s\" OR %s)";
    return String.format(queryPattern, emptyGroupName, searchTerm);
  }

  private void addComment(int changeId, String message, Boolean unresolved) throws Exception {
    ReviewInput input = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.line = 1;
    comment.message = message;
    comment.unresolved = unresolved;
    input.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
    gApi.changes().id(changeId).current().review(input);
  }

  private Account.Id createAccount(String username, String fullName, String email, boolean active)
      throws Exception {
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      Account.Id id =
          accountManager.authenticate(authRequestFactory.createForUser(username)).getAccountId();
      if (email != null) {
        accountManager.link(id, authRequestFactory.createForEmail(email));
      }
      accountsUpdate
          .get()
          .update(
              "Update Test Account",
              id,
              u -> {
                u.setFullName(fullName).setPreferredEmail(email).setActive(active);
              });
      return id;
    }
  }

  protected void assertFailingQuery(String query) throws Exception {
    assertFailingQuery(query, null);
  }

  protected void assertFailingQuery(QueryRequest query, String expectedMessage) throws Exception {
    try {
      assertQuery(query);
      fail("expected BadRequestException for query '" + query + "'");
    } catch (BadRequestException e) {
      assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
  }

  protected void assertFailingQuery(String query, @Nullable String expectedMessage)
      throws Exception {
    try {
      assertQuery(query);
      fail("expected BadRequestException for query '" + query + "'");
    } catch (BadRequestException e) {
      if (expectedMessage != null) {
        assertThat(e.getMessage()).isEqualTo(expectedMessage);
      }
    }
  }

  protected Schema<ChangeData> getSchema() {
    return indexes.getSearchIndex().getSchema();
  }

  PaginationType getCurrentPaginationType() {
    return config.getEnum("index", null, "paginationType", PaginationType.OFFSET);
  }
}
