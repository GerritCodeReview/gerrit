// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.gpg.PublicKeyStore.REFS_GPG_KEYS;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testing.TestKeys.allValidKeys;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithSecondUserId;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.server.account.AccountProperties.ACCOUNT;
import static com.google.gerrit.server.account.AccountProperties.ACCOUNT_CONFIG;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static com.google.gerrit.truth.ConfigSubject.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.StopStrategies;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.io.BaseEncoding;
import com.google.common.truth.Correspondence;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.common.util.concurrent.Runnables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.account.TestSshKeys;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.acceptance.testsuite.request.SshSessionFactory;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.accounts.DeleteDraftCommentsInput;
import com.google.gerrit.extensions.api.accounts.DeletedDraftCommentInfo;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput.CheckAccountsInput;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.AccountDetailInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AccountStateInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.MetadataInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.events.AccountActivationListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.testing.TestKey;
import com.google.gerrit.httpd.BasicCookieStoreProvider;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.server.AccountControl;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountStateProvider;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdFactoryNoteDbImpl;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdsNoteDbImpl;
import com.google.gerrit.server.account.storage.notedb.AccountsUpdateNoteDbImpl;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.testing.TestGroupBackend;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.account.StalenessChecker;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.restapi.account.GetCapabilities;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryListener;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AccountIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config enableSignedPushConfig() {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "enableSignedPush", true);

    // Disable the staleness checker so that tests that verify the number of expected index events
    // are stable.
    cfg.setBoolean("index", null, "autoReindexIfStale", false);

    return cfg;
  }

  @Inject protected ProjectOperations projectOperations;
  @Inject protected Emails emails;
  @Inject protected ExtensionRegistry extensionRegistry;
  @Inject protected RequestScopeOperations requestScopeOperations;

  @Inject protected GroupOperations groupOperations;

  @Inject private @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private AccountIndexer accountIndexer;
  @Inject private ExternalIdNotes.Factory extIdNotesFactory;
  @Inject private ExternalIdsNoteDbImpl externalIdsNoteDbImpl;
  @Inject private GitReferenceUpdated gitReferenceUpdated;
  @Inject private Provider<InternalAccountQuery> accountQueryProvider;
  @Inject private Provider<MetaDataUpdate.InternalFactory> metaDataUpdateInternalFactory;
  @Inject private Provider<PublicKeyStore> publicKeyStoreProvider;
  @Inject private RetryHelper.Metrics retryMetrics;
  @Inject private Sequences seq;
  @Inject private StalenessChecker stalenessChecker;
  @Inject private VersionedAuthorizedKeys.Accessor authorizedKeys;
  @Inject private PluginSetContext<ExceptionHook> exceptionHooks;
  @Inject private PluginSetContext<RetryListener> retryListeners;
  @Inject private ExternalIdKeyFactory externalIdKeyFactory;
  @Inject private ExternalIdFactoryNoteDbImpl externalIdFactoryNoteDbImpl;
  @Inject private AuthConfig authConfig;
  @Inject private AccountControl.Factory accountControlFactory;
  @Inject private AccountOperations accountOperations;
  @Inject private AccountLimits.Factory limitsFactory;
  @Inject private AccountPatchReviewStore accountPatchReviewStore;
  @Inject private IdentifiedUser.GenericFactory identifiedUserFactory;
  @Inject private PermissionBackend permissionBackend;

  private BasicCookieStore httpCookieStore;
  private CloseableHttpClient httpclient;

  @After
  public void closeClient() throws Exception {
    if (httpclient != null) {
      httpclient.close();
    }
  }

  @Before
  public void createHttpClient() {
    httpCookieStore = new BasicCookieStore();
    httpclient = HttpClientBuilder.create().setDefaultCookieStore(httpCookieStore).build();
  }

  @Test
  public void get() throws Exception {
    AccountInfo info = gApi.accounts().id(admin.id().get()).get();
    assertUser(info, admin);
  }

  @Test
  public void getByEmail() throws Exception {
    AccountInfo info = gApi.accounts().id(admin.email()).get();
    assertUser(info, admin);
  }

  @Test
  public void getByUsername() throws Exception {
    AccountInfo info = gApi.accounts().id(admin.username()).get();
    assertUser(info, admin);
  }

  @Test
  public void getSelf() throws Exception {
    AccountInfo info = gApi.accounts().self().get();
    assertUser(info, admin);
  }

  @Test
  public void getByIntId() throws Exception {
    AccountInfo info = gApi.accounts().id(admin.id().get()).get();
    assertUser(info, admin);
  }

  @Test
  public void getAmbiguous() throws Exception {
    String name = "ambiguous";
    TestAccount user1 = accountCreator.create(name("u1"), "u1@example.com", name, null);
    TestAccount user2 = accountCreator.create(name("u2"), "u2@example.com", name, null);
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.accounts().id(name).get());
    assertThat(thrown).hasMessageThat().contains("is ambiguous");

    AccountInfo info = gApi.accounts().id(user1.id().get()).get();
    assertUser(info, user1);

    info = gApi.accounts().id(user2.id().get()).get();
    assertUser(info, user2);
  }

  @Test
  public void validateExistingPermission() throws Exception {
    String ref = "refs/heads/*";
    String labelName = "Code-Review";
    GroupReference groupReference = groupOperations.group(REGISTERED_USERS).get().groupReference();
    Integer min = -1;
    Integer max = 1;
    boolean exclusive = false;

    projectOperations
        .project(project)
        .forUpdate()
        .add(
            TestProjectUpdate.allowLabel(labelName)
                .range(min, max)
                .ref(ref)
                .group(groupReference.getUUID())
                .exclusive(exclusive)
                .build())
        .update();

    Optional<AccessSection> accessSection =
        projectCache
            .get(project)
            .orElseThrow(illegalState(project))
            .getConfig()
            .getAccessSection(ref);
    assertThat(accessSection).isPresent();

    String permissionName = Permission.LABEL + labelName;
    Permission permission = accessSection.get().getPermission(permissionName);
    assertPermission(permission, permissionName, exclusive, labelName);
    assertPermissionRule(
        permission.getRule(groupReference), groupReference, Action.ALLOW, false, min, max);
  }

  @Test
  public void randomNIds() throws Exception {
    List<Account.Id> allIds = new ArrayList<>(accounts.allIds());
    assertThat(allIds.size()).isAtLeast(2);

    List<Account.Id> randomIds1 = accounts.randomNIds(1, 12345L);
    assertThat(randomIds1).hasSize(1);
    assertThat(allIds).containsAtLeastElementsIn(randomIds1);

    List<Account.Id> randomIds2 = accounts.randomNIds(1, 12345L);
    assertThat(randomIds2).isEqualTo(randomIds1);

    List<Account.Id> randomIds3 = accounts.randomNIds(1, 54321L);
    assertThat(randomIds3).isNotEqualTo(randomIds1);
  }

  @Test
  public void createByAccountCreator() throws Exception {
    RefUpdateCounter refUpdateCounter = createRefUpdateCounter();
    try (Registration registration = extensionRegistry.newRegistration().add(refUpdateCounter)) {
      Account.Id accountId = createByAccountCreator(1);
      refUpdateCounter.assertRefUpdateFor(
          RefUpdateCounter.projectRef(allUsers, RefNames.refsUsers(accountId)),
          RefUpdateCounter.projectRef(allUsers, RefNames.REFS_EXTERNAL_IDS),
          RefUpdateCounter.projectRef(allUsers, RefNames.REFS_SEQUENCES + "accounts"));
    }
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected int getUsersWithDraftsCount(Change.Id changeId) throws Exception {
    // The getStarredChangesCount and getUsersWithDraftsCount should be 2 distinct methods,
    // because in google they can query data from a different storage (i.e. not from noteDb).
    return getRefCount(RefNames.refsDraftCommentsPrefix(changeId));
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected int getStarredChangesCount(Change.Id changeId) throws Exception {
    // The getStarredChangesCount and getDraftsCommentsCount should be 2 distinct methods,
    // because in google they can query data from a different storage (i.e. not from noteDb).
    return getRefCount(RefNames.refsStarredChangesPrefix(changeId));
  }

  private int getRefCount(String refPrefix) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.getRefDatabase().getRefsByPrefix(refPrefix).size();
    }
  }

  @Test
  @SuppressWarnings("unused")
  public void deleteAccount_deletesReviewedFlags() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount deleted = accountCreator.createValid(name("deleted"));
    ReviewerInput in = new ReviewerInput();
    in.reviewer = deleted.email();
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    requestScopeOperations.setApiUser(deleted.id());

    var unused =
        accountPatchReviewStore.markReviewed(
            r.getPatchSetId(), deleted.id(), PushOneCommit.FILE_NAME);
    assertThat(accountPatchReviewStore.findReviewed(r.getPatchSetId(), deleted.id())).isPresent();

    gApi.accounts().self().delete();

    assertThat(accountPatchReviewStore.findReviewed(r.getPatchSetId(), deleted.id())).isEmpty();

    // Clean up the test framework
    accountCreator.evict(deleted.id());
  }

  @Test
  public void deleteAccount_appliesForSelfById() throws Exception {
    TestAccount deleted = accountCreator.createValid(name("deleted"));
    requestScopeOperations.setApiUser(deleted.id());
    gApi.accounts().id(deleted.id().get()).delete();

    // Clean up the test framework
    accountCreator.evict(deleted.id());
  }

  @Test
  public void deleteAccount_throwsForOtherUsers() throws Exception {
    TestAccount deleted = accountCreator.createValid(name("deleted"));
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.accounts().id(deleted.id().get()).delete());
    assertThat(thrown).hasMessageThat().isEqualTo("Delete account is only permitted for self");
  }

  @Test
  @GerritConfig(name = "accounts.enableDelete", value = "false")
  public void deleteAccount_throwsForSelfIfConfigenableDeleteIsDisabled() throws Exception {
    TestAccount deleted = accountCreator.createValid(name("deleted"));
    requestScopeOperations.setApiUser(deleted.id());
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class, () -> gApi.accounts().id(deleted.id().get()).delete());
    assertThat(thrown).hasMessageThat().isEqualTo("Delete account is not enabled");
  }

  @Test
  @GerritConfig(name = "accounts.enableDelete", value = "false")
  public void deleteAccount_throwsForOthersIfConfigenableDeleteIsDisabled() throws Exception {
    TestAccount deleted = accountCreator.createValid(name("deleted"));
    requestScopeOperations.setApiUser(user.id());
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class, () -> gApi.accounts().id(deleted.id().get()).delete());
    assertThat(thrown).hasMessageThat().isEqualTo("Delete account is not enabled");
  }

  @Test
  public void getOwnAccountState() throws Exception {
    String email = "preferred@example.com";
    String name = "Foo";
    String username = name("foo");
    TestAccount foo = accountCreator.create(username, email, name, null);
    String secondaryEmail = "secondary@non.google";
    EmailInput input = newEmailInput(secondaryEmail);
    gApi.accounts().id(foo.id().get()).addEmail(input);

    String status = "OOO";
    gApi.accounts().id(foo.id().get()).setStatus(status);

    String groupName = "SomeGroup";
    groupOperations.newGroup().name(groupName).addMember(foo.id()).create();

    TestAccountStateProvider testAccountStateProvider = new TestAccountStateProvider();
    MetadataInfo metadata1 = testAccountStateProvider.addMetadata("employee_id", "123456", null);
    MetadataInfo metadata2 = testAccountStateProvider.addMetadata("role", null, "role name");
    MetadataInfo metadata3 = testAccountStateProvider.addMetadata("team", "Bar", "team name");
    MetadataInfo metadata4 = testAccountStateProvider.addMetadata("team", "Foo", "team name");
    try (Registration registration =
        extensionRegistry.newRegistration().add(testAccountStateProvider)) {
      requestScopeOperations.setApiUser(foo.id());
      AccountStateInfo state = gApi.accounts().id(foo.id().get()).state();

      AccountDetailInfo detail = state.account;
      assertThat(detail._accountId).isEqualTo(foo.id().get());
      assertThat(detail.name).isEqualTo(name);
      if (server.isUsernameSupported()) {
        assertThat(detail.username).isEqualTo(username);
      }
      assertThat(detail.email).isEqualTo(email);
      assertThat(detail.secondaryEmails).containsExactly(secondaryEmail);
      assertThat(detail.status).isEqualTo(status);
      assertThat(detail.registeredOn.getTime())
          .isEqualTo(getAccount(foo.id()).registeredOn().toEpochMilli());
      assertThat(detail.inactive).isNull();
      assertThat(detail._moreAccounts).isNull();

      if (permissionBackend.usesDefaultCapabilities()) {
        AccountLimits limits = limitsFactory.create(genericUserFactory.create(foo.id()));
        GetCapabilities.Range queryLimitRange =
            new GetCapabilities.Range(limits.getRange("queryLimit"));
        assertThat(state.capabilities)
            .containsExactly("emailReviewers", true, "queryLimit", queryLimitRange);
      } else {
        assertThat(state.capabilities).isNull();
      }

      assertThat(state.groups)
          .comparingElementsUsing(getGroupToNameCorrespondence())
          .containsAtLeast("Anonymous Users", "Registered Users", groupName);

      assertExternalIds(
          state.externalIds.stream().map(e -> e.identity).collect(toImmutableSet()),
          ImmutableSet.of("mailto:" + email, "username:" + username, "mailto:" + secondaryEmail));

      // Using containsAtLeast instead of containsExcatly because when the test is run internally at
      // Google additional metadata is returned.
      assertThat(state.metadata)
          .containsAtLeast(metadata1, metadata2, metadata3, metadata4)
          .inOrder();
    }
  }

  @Test
  public void nonAdminCannotGetAccountStateOfOtherUser() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.accounts().id(admin.id().get()).state());
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("cannot get account state of other user: administrate server not permitted");
  }

  @Test
  public void adminCanGetAccountStateOfOtherUser() throws Exception {
    AccountStateInfo state = gApi.accounts().id(user.id().get()).state();
    assertThat(state.account._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void getAccountStateRequiresAuthentication() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.accounts().id(user.id().get()).state());
    assertThat(thrown).hasMessageThat().isEqualTo("Authentication required");
  }

  private TestGroupBackend createTestGroupBackendWithAllUsersGroup(String nameOfAllUsersGroup)
      throws IOException {
    TestGroupBackend testGroupBackend = new TestGroupBackend();

    AccountGroup.UUID allUsersGroupUuid =
        testGroupBackend.create(nameOfAllUsersGroup).getGroupUUID();

    GroupMembership testGroupMembership =
        new GroupMembership() {
          @Override
          public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupUuids) {
            return StreamSupport.stream(groupUuids.spliterator(), /* parallel= */ false)
                .filter(this::contains)
                .collect(toSet());
          }

          @Override
          public ImmutableSet<AccountGroup.UUID> getKnownGroups() {
            // Typically for external group backends it's too expensive to query all groups that the
            // user is a member of. Instead limit the group membership check to groups that are
            // guessed to be relevant.
            return projectCache.guessRelevantGroupUUIDs().stream()
                // filter out groups of other group backends and groups of this group backend that
                // don't exist
                .filter(
                    uuid -> testGroupBackend.handles(uuid) && testGroupBackend.get(uuid) != null)
                .collect(toImmutableSet());
          }

          @Override
          public boolean containsAnyOf(Iterable<AccountGroup.UUID> groupUuids) {
            return StreamSupport.stream(groupUuids.spliterator(), /* parallel= */ false)
                .anyMatch(this::contains);
          }

          @Override
          public boolean contains(AccountGroup.UUID groupUuid) {
            return allUsersGroupUuid.equals(groupUuid);
          }
        };

    accounts
        .allIds()
        .forEach(accountId -> testGroupBackend.setMembershipsOf(accountId, testGroupMembership));

    return testGroupBackend;
  }

  private void assertExternalIds(Account.Id accountId, ImmutableSet<String> extIds)
      throws Exception {
    assertExternalIds(
        gApi.accounts().id(accountId.get()).getExternalIds().stream()
            .map(e -> e.identity)
            .collect(toImmutableSet()),
        extIds);
  }

  protected void assertExternalIds(
      ImmutableSet<String> actualExternalIds, ImmutableSet<String> expectedExternalIds)
      throws Exception {
    assertThat(actualExternalIds).isEqualTo(expectedExternalIds);
  }

  private void assertExternalEmails(Account.Id accountId, ImmutableSet<String> extIds)
      throws Exception {
    assertThat(
            gApi.accounts().id(accountId.get()).getExternalIds().stream()
                .map(e -> e.emailAddress)
                .filter(Objects::nonNull)
                .collect(toImmutableSet()))
        .isEqualTo(extIds);
  }

  private static Correspondence<GroupInfo, String> getGroupToNameCorrespondence() {
    return NullAwareCorrespondence.transforming(groupInfo -> groupInfo.name, "has name");
  }

  private void assertSequenceNumbers(List<SshKeyInfo> sshKeys) {
    int seq = 1;
    for (SshKeyInfo key : sshKeys) {
      assertThat(key.seq).isEqualTo(seq++);
    }
  }

  private PGPPublicKey getOnlyKeyFromStore(TestKey key) throws Exception {
    try (PublicKeyStore store = publicKeyStoreProvider.get()) {
      Iterable<PGPPublicKeyRing> keys = store.get(key.getKeyId());
      assertThat(keys).hasSize(1);
      return keys.iterator().next().getPublicKey();
    }
  }

  private static String armor(PGPPublicKey key) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
    try (ArmoredOutputStream aout = new ArmoredOutputStream(out)) {
      key.encode(aout);
    }
    return new String(out.toByteArray(), UTF_8);
  }

  private static void assertIteratorSize(int size, Iterator<?> it) {
    ImmutableList<?> lst = ImmutableList.copyOf(it);
    assertThat(lst).hasSize(size);
  }

  private static void assertKeyMapContains(TestKey expected, Map<String, GpgKeyInfo> actualMap) {
    GpgKeyInfo actual = actualMap.get(expected.getKeyIdString());
    assertThat(actual).isNotNull();
    assertThat(actual.id).isNull();
    actual.id = expected.getKeyIdString();
    assertKeyEquals(expected, actual);
  }

  private void assertKeys(TestKey... expectedKeys) throws Exception {
    assertKeys(Arrays.asList(expectedKeys));
  }

  private void assertKeys(Iterable<TestKey> expectedKeys) throws Exception {
    // Check via API.
    FluentIterable<TestKey> expected = FluentIterable.from(expectedKeys);
    Map<String, GpgKeyInfo> keyMap = gApi.accounts().self().listGpgKeys();
    assertWithMessage("keys returned by listGpgKeys()")
        .that(keyMap.keySet())
        .containsExactlyElementsIn(expected.transform(TestKey::getKeyIdString));

    for (TestKey key : expected) {
      assertKeyEquals(key, gApi.accounts().self().gpgKey(key.getKeyIdString()).get());
      assertKeyEquals(
          key,
          gApi.accounts()
              .self()
              .gpgKey(Fingerprint.toString(key.getPublicKey().getFingerprint()))
              .get());
      assertKeyMapContains(key, keyMap);
    }

    // Check raw external IDs.
    Account.Id currAccountId = localCtx.getContext().getUser().getAccountId();
    Iterable<String> expectedFps =
        expected.transform(k -> BaseEncoding.base16().encode(k.getPublicKey().getFingerprint()));
    Set<String> actualFps =
        getExternalIdsReader().byAccount(currAccountId, SCHEME_GPGKEY).stream()
            .map(e -> e.key().id())
            .collect(toSet());
    assertWithMessage("external IDs in database")
        .that(actualFps)
        .containsExactlyElementsIn(expectedFps);

    // Check raw stored keys.
    for (TestKey key : expected) {
      try (PublicKeyStore store = publicKeyStoreProvider.get()) {
        assertThat(store.get(key.getKeyId())).hasSize(1);
      }
    }
  }

  private static void assertKeyEquals(TestKey expected, GpgKeyInfo actual) {
    String id = expected.getKeyIdString();
    assertWithMessage(id).that(actual.id).isEqualTo(id);
    assertWithMessage(id)
        .that(actual.fingerprint)
        .isEqualTo(Fingerprint.toString(expected.getPublicKey().getFingerprint()));
    ImmutableList<String> userIds = ImmutableList.copyOf(expected.getPublicKey().getUserIDs());
    assertWithMessage(id).that(actual.userIds).containsExactlyElementsIn(userIds);
    String key = actual.key;
    assertWithMessage(id).that(key).startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----\n");
    assertWithMessage(id).that(key).endsWith("-----END PGP PUBLIC KEY BLOCK-----\n");
    assertThat(actual.status).isEqualTo(GpgKeyInfo.Status.TRUSTED);
    assertThat(actual.problems).isEmpty();
  }

  private void addExternalIdEmail(TestAccount account, String email) throws Exception {
    AccountIndexedCounter accountIndexedCounter = getAccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      requireNonNull(email);
      accountsUpdateProvider
          .get()
          .update(
              "Add Email",
              account.id(),
              u ->
                  u.addExternalId(
                      getExternalIdFactory()
                          .createWithEmail(name("test"), email, account.id(), email)));
      accountIndexedCounter.assertReindexOf(account);
      requestScopeOperations.setApiUser(account.id());
    }
  }

  @CanIgnoreReturnValue
  private Map<String, GpgKeyInfo> addGpgKey(String armored) throws Exception {
    return addGpgKey(admin, armored);
  }

  @CanIgnoreReturnValue
  private Map<String, GpgKeyInfo> addGpgKey(TestAccount account, String armored) throws Exception {
    return testRefAction(
        () -> {
          AccountIndexedCounter accountIndexedCounter = getAccountIndexedCounter();
          try (Registration registration =
              extensionRegistry.newRegistration().add(accountIndexedCounter)) {
            Map<String, GpgKeyInfo> gpgKeys =
                gApi.accounts()
                    .id(account.id().get())
                    .putGpgKeys(ImmutableList.of(armored), ImmutableList.<String>of());
            accountIndexedCounter.assertReindexOf(gApi.accounts().id(account.id().get()).get());
            return gpgKeys;
          }
        });
  }

  private Map<String, GpgKeyInfo> addGpgKeyNoReindex(String armored) throws Exception {
    return gApi.accounts().self().putGpgKeys(ImmutableList.of(armored), ImmutableList.of());
  }

  private void assertUser(AccountInfo info, TestAccount account) throws Exception {
    assertUser(info, account, null);
  }

  private void assertUser(AccountInfo info, TestAccount account, @Nullable String expectedStatus)
      throws Exception {
    assertThat(info.name).isEqualTo(account.fullName());
    assertThat(info.email).isEqualTo(account.email());
    if (server.isUsernameSupported()) {
      assertThat(info.username).isEqualTo(account.username());
    }
    assertThat(info.status).isEqualTo(expectedStatus);
  }

  private ImmutableSet<String> getEmails() throws RestApiException {
    return gApi.accounts().self().getEmails().stream().map(e -> e.email).collect(toImmutableSet());
  }

  private ImmutableSet<String> getExtIdsEmail() throws RestApiException {
    return gApi.accounts().self().getExternalIds().stream()
        .map(e -> e.emailAddress)
        .filter(Objects::nonNull)
        .collect(toImmutableSet());
  }

  private void assertEmail(Set<Account.Id> accounts, TestAccount expectedAccount) {
    assertThat(accounts).hasSize(1);
    assertThat(Iterables.getOnlyElement(accounts)).isEqualTo(expectedAccount.id());
  }

  private AccountApi accountIdApi() throws RestApiException {
    return gApi.accounts().id(user.id().get());
  }

  private Set<String> getCookiesNames() {
    Set<String> cookieNames =
        httpCookieStore.getCookies().stream()
            .map(cookie -> cookie.getName())
            .collect(Collectors.toSet());
    return cookieNames;
  }

  private void webLogin(Integer accountId) throws IOException, ClientProtocolException {
    httpGetAndAssertStatus(
        "login?account_id=" + accountId, HttpServletResponse.SC_MOVED_TEMPORARILY);
  }

  private AccountsUpdate getAccountsUpdateWithRunnables(
      Runnable afterReadRevision, Runnable beforeCommit) {
    return getAccountsUpdateWithRunnables(
        afterReadRevision,
        beforeCommit,
        new RetryHelper(
            cfg,
            retryMetrics,
            null,
            null,
            null,
            null,
            exceptionHooks,
            retryListeners,
            r -> r.withBlockStrategy(noSleepBlockStrategy)));
  }

  private ExternalIdNotes getExternalIdNotes(Repository allUsersRepo)
      throws ConfigInvalidException, IOException {
    return ExternalIdNotes.load(
        allUsers,
        allUsersRepo,
        externalIdFactoryNoteDbImpl,
        authConfig.isUserNameCaseInsensitiveMigrationMode());
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected ExternalIdFactory getExternalIdFactory() {
    return externalIdFactoryNoteDbImpl;
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected ExternalIds getExternalIdsReader() {
    return externalIdsNoteDbImpl;
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected AccountsUpdate getAccountsUpdateWithRunnables(
      Runnable afterReadRevision, Runnable beforeCommit, RetryHelper retryHelper) {
    return getAccountsUpdateNoteDbImplWithRunnables(afterReadRevision, beforeCommit, retryHelper);
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected final AccountsUpdateNoteDbImpl getAccountsUpdateNoteDbImplWithRunnables(
      Runnable afterReadRevision, Runnable beforeCommit, RetryHelper retryHelper) {
    return new AccountsUpdateNoteDbImpl(
        repoManager,
        gitReferenceUpdated,
        Optional.empty(),
        allUsers,
        externalIdsNoteDbImpl,
        extIdNotesFactory,
        metaDataUpdateInternalFactory,
        retryHelper,
        serverIdent.get(),
        afterReadRevision,
        beforeCommit);
  }

  private void httpGetAndAssertStatus(String urlPath, int expectedHttpStatus)
      throws ClientProtocolException, IOException {
    HttpGet httpGet = new HttpGet(canonicalWebUrl.get() + urlPath);
    HttpResponse loginResponse = httpclient.execute(httpGet);
    assertThat(loginResponse.getStatusLine().getStatusCode()).isEqualTo(expectedHttpStatus);
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected RefUpdateCounter createRefUpdateCounter() {
    return new RefUpdateCounter();
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public static class RefUpdateCounter implements GitReferenceUpdatedListener {
    private final AtomicLongMap<String> countsByProjectRefs = AtomicLongMap.create();

    @UsedAt(UsedAt.Project.GOOGLE)
    public static String projectRef(Project.NameKey project, String ref) {
      return projectRef(project.get(), ref);
    }

    static String projectRef(String project, String ref) {
      return project + ":" + ref;
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
      countsByProjectRefs.incrementAndGet(projectRef(event.getProjectName(), event.getRefName()));
    }

    void clear() {
      countsByProjectRefs.clear();
    }

    @UsedAt(UsedAt.Project.GOOGLE)
    public void assertRefUpdateFor(String... projectRefs) {
      Map<String, Long> expectedRefUpdateCounts = new HashMap<>();
      for (String projectRef : projectRefs) {
        expectedRefUpdateCounts.put(projectRef, 1L);
      }
      assertRefUpdateFor(expectedRefUpdateCounts);
    }

    protected void assertRefUpdateFor(Map<String, Long> expectedProjectRefUpdateCounts) {
      ImmutableMap<String, Long> exprectedFiltered =
          expectedProjectRefUpdateCounts.entrySet().stream()
              .filter(entry -> isRefSupported(entry.getKey()))
              .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
      assertThat(countsByProjectRefs.asMap()).containsExactlyEntriesIn(exprectedFiltered);
      clear();
    }

    @UsedAt(UsedAt.Project.GOOGLE)
    protected boolean isRefSupported(String expectedRefEntryKey) {
      return true;
    }
  }

  public static class TestAccountStateProvider implements AccountStateProvider {
    private ArrayList<MetadataInfo> metadataList = new ArrayList<>();

    public MetadataInfo addMetadata(
        String name, @Nullable String value, @Nullable String description) {
      MetadataInfo metadata = new MetadataInfo();
      metadata.name = name;
      metadata.value = value;
      metadata.description = description;
      metadataList.add(metadata);
      return metadata;
    }

    @Override
    public ImmutableList<MetadataInfo> getMetadata(Account.Id accountId) {
      return ImmutableList.copyOf(metadataList);
    }
  }
}
