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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.gpg.PublicKeyStore.REFS_GPG_KEYS;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.gpg.testutil.TestKeys.allValidKeys;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithSecondUserId;
import static com.google.gerrit.gpg.testutil.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.server.StarredChangesUtil.DEFAULT_LABEL;
import static com.google.gerrit.server.StarredChangesUtil.IGNORE_LABEL;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInput.CheckAccountsInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.testutil.TestKey;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectReader;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AccountIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config enableSignedPushConfig() {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "enableSignedPush", true);
    return cfg;
  }

  @Inject private Provider<PublicKeyStore> publicKeyStoreProvider;

  @Inject private AllUsersName allUsers;

  @Inject private AccountsUpdate.Server accountsUpdate;

  @Inject private ExternalIds externalIds;

  @Inject private ExternalIdsUpdate.User externalIdsUpdateFactory;

  @Inject private DynamicSet<AccountIndexedListener> accountIndexedListeners;

  @Inject private DynamicSet<GitReferenceUpdatedListener> refUpdateListeners;

  @Inject private Sequences seq;

  @Inject private Provider<InternalAccountQuery> accountQueryProvider;

  @Inject protected Emails emails;

  @Inject private AccountManager accountManager;

  private AccountIndexedCounter accountIndexedCounter;
  private RegistrationHandle accountIndexEventCounterHandle;
  private RefUpdateCounter refUpdateCounter;
  private RegistrationHandle refUpdateCounterHandle;
  private ExternalIdsUpdate externalIdsUpdate;
  private List<ExternalId> savedExternalIds;

  @Before
  public void addAccountIndexEventCounter() {
    accountIndexedCounter = new AccountIndexedCounter();
    accountIndexEventCounterHandle = accountIndexedListeners.add(accountIndexedCounter);
  }

  @After
  public void removeAccountIndexEventCounter() {
    if (accountIndexEventCounterHandle != null) {
      accountIndexEventCounterHandle.remove();
    }
  }

  @Before
  public void addRefUpdateCounter() {
    refUpdateCounter = new RefUpdateCounter();
    refUpdateCounterHandle = refUpdateListeners.add(refUpdateCounter);
  }

  @After
  public void removeRefUpdateCounter() {
    if (refUpdateCounterHandle != null) {
      refUpdateCounterHandle.remove();
    }
  }

  @Before
  public void saveExternalIds() throws Exception {
    externalIdsUpdate = externalIdsUpdateFactory.create();

    savedExternalIds = new ArrayList<>();
    savedExternalIds.addAll(externalIds.byAccount(admin.id));
    savedExternalIds.addAll(externalIds.byAccount(user.id));
  }

  @After
  public void restoreExternalIds() throws Exception {
    if (savedExternalIds != null) {
      // savedExternalIds is null when we don't run SSH tests and the assume in
      // @Before in AbstractDaemonTest prevents this class' @Before method from
      // being executed.
      externalIdsUpdate.delete(externalIds.byAccount(admin.id));
      externalIdsUpdate.delete(externalIds.byAccount(user.id));
      externalIdsUpdate.insert(savedExternalIds);
    }
  }

  @After
  public void clearPublicKeyStore() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(REFS_GPG_KEYS);
      if (ref != null) {
        RefUpdate ru = repo.updateRef(REFS_GPG_KEYS);
        ru.setForceUpdate(true);
        assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }

  @After
  public void deleteGpgKeys() throws Exception {
    String ref = REFS_GPG_KEYS;
    try (Repository repo = repoManager.openRepository(allUsers)) {
      if (repo.getRefDatabase().exactRef(ref) != null) {
        RefUpdate ru = repo.updateRef(ref);
        ru.setForceUpdate(true);
        assertWithMessage("Failed to delete " + ref)
            .that(ru.delete())
            .isEqualTo(RefUpdate.Result.FORCED);
      }
    }
  }

  @Test
  public void create() throws Exception {
    Account.Id accountId = create(2); // account creation + external ID creation
    refUpdateCounter.assertRefUpdateFor(
        RefUpdateCounter.projectRef(allUsers, RefNames.refsUsers(accountId)),
        RefUpdateCounter.projectRef(allUsers, RefNames.REFS_EXTERNAL_IDS),
        RefUpdateCounter.projectRef(allUsers, RefNames.REFS_SEQUENCES + Sequences.NAME_ACCOUNTS));
  }

  @Test
  @UseSsh
  public void createWithSshKeys() throws Exception {
    Account.Id accountId = create(3); // account creation + external ID creation + adding SSH keys
    refUpdateCounter.assertRefUpdateFor(
        ImmutableMap.of(
            RefUpdateCounter.projectRef(allUsers, RefNames.refsUsers(accountId)),
            2,
            RefUpdateCounter.projectRef(allUsers, RefNames.REFS_EXTERNAL_IDS),
            1,
            RefUpdateCounter.projectRef(
                allUsers, RefNames.REFS_SEQUENCES + Sequences.NAME_ACCOUNTS),
            1));
  }

  private Account.Id create(int expectedAccountReindexCalls) throws Exception {
    String name = "foo";
    TestAccount foo = accountCreator.create(name);
    AccountInfo info = gApi.accounts().id(foo.id.get()).get();
    assertThat(info.username).isEqualTo(name);
    assertThat(info.name).isEqualTo(name);
    accountIndexedCounter.assertReindexOf(foo, expectedAccountReindexCalls);
    assertUserBranch(foo.getId(), name, null);
    return foo.getId();
  }

  @Test
  public void createAnonymousCoward() throws Exception {
    TestAccount anonymousCoward = accountCreator.create();
    accountIndexedCounter.assertReindexOf(anonymousCoward);
    assertUserBranchWithoutAccountConfig(anonymousCoward.getId());
  }

  @Test
  public void updateNonExistingAccount() throws Exception {
    Account.Id nonExistingAccountId = new Account.Id(999999);
    AtomicBoolean consumerCalled = new AtomicBoolean();
    Account account =
        accountsUpdate.create().update(nonExistingAccountId, a -> consumerCalled.set(true));
    assertThat(account).isNull();
    assertThat(consumerCalled.get()).isFalse();
  }

  @Test
  public void updateAccountWithoutAccountConfigNoteDb() throws Exception {
    TestAccount anonymousCoward = accountCreator.create();
    assertUserBranchWithoutAccountConfig(anonymousCoward.getId());

    String status = "OOO";
    Account account =
        accountsUpdate.create().update(anonymousCoward.getId(), a -> a.setStatus(status));
    assertThat(account).isNotNull();
    assertThat(account.getFullName()).isNull();
    assertThat(account.getStatus()).isEqualTo(status);
    assertUserBranch(anonymousCoward.getId(), null, status);
  }

  private void assertUserBranchWithoutAccountConfig(Account.Id accountId) throws Exception {
    assertUserBranch(accountId, null, null);
  }

  private void assertUserBranch(
      Account.Id accountId, @Nullable String name, @Nullable String status) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.refsUsers(accountId));
      assertThat(ref).isNotNull();
      RevCommit c = rw.parseCommit(ref.getObjectId());
      long timestampDiffMs =
          Math.abs(
              c.getCommitTime() * 1000L
                  - accountCache.get(accountId).getAccount().getRegisteredOn().getTime());
      assertThat(timestampDiffMs).isAtMost(ChangeRebuilderImpl.MAX_WINDOW_MS);

      // Check the 'account.config' file.
      try (TreeWalk tw = TreeWalk.forPath(or, AccountConfig.ACCOUNT_CONFIG, c.getTree())) {
        if (name != null || status != null) {
          assertThat(tw).isNotNull();
          Config cfg = new Config();
          cfg.fromText(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8));
          assertThat(cfg.getString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_FULL_NAME))
              .isEqualTo(name);
          assertThat(cfg.getString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_STATUS))
              .isEqualTo(status);
        } else {
          // No account properties were set, hence an 'account.config' file was not created.
          assertThat(tw).isNull();
        }
      }
    }
  }

  @Test
  public void get() throws Exception {
    AccountInfo info = gApi.accounts().id("admin").get();
    assertThat(info.name).isEqualTo("Administrator");
    assertThat(info.email).isEqualTo("admin@example.com");
    assertThat(info.username).isEqualTo("admin");
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void getByIntId() throws Exception {
    AccountInfo info = gApi.accounts().id("admin").get();
    AccountInfo infoByIntId = gApi.accounts().id(info._accountId).get();
    assertThat(info.name).isEqualTo(infoByIntId.name);
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void self() throws Exception {
    AccountInfo info = gApi.accounts().self().get();
    assertUser(info, admin);

    info = gApi.accounts().id("self").get();
    assertUser(info, admin);
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void active() throws Exception {
    assertThat(gApi.accounts().id("user").getActive()).isTrue();
    gApi.accounts().id("user").setActive(false);
    assertThat(gApi.accounts().id("user").getActive()).isFalse();
    accountIndexedCounter.assertReindexOf(user);

    gApi.accounts().id("user").setActive(true);
    assertThat(gApi.accounts().id("user").getActive()).isTrue();
    accountIndexedCounter.assertReindexOf(user);
  }

  @Test
  public void deactivateSelf() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot deactivate own account");
    gApi.accounts().self().setActive(false);
  }

  @Test
  public void deactivateNotActive() throws Exception {
    assertThat(gApi.accounts().id("user").getActive()).isTrue();
    gApi.accounts().id("user").setActive(false);
    assertThat(gApi.accounts().id("user").getActive()).isFalse();
    try {
      gApi.accounts().id("user").setActive(false);
      fail("Expected exception");
    } catch (ResourceConflictException e) {
      assertThat(e.getMessage()).isEqualTo("account not active");
    }
    gApi.accounts().id("user").setActive(true);
  }

  @Test
  public void starUnstarChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    refUpdateCounter.clear();

    gApi.accounts().self().starChange(triplet);
    ChangeInfo change = info(triplet);
    assertThat(change.starred).isTrue();
    assertThat(change.stars).contains(DEFAULT_LABEL);
    refUpdateCounter.assertRefUpdateFor(
        RefUpdateCounter.projectRef(
            allUsers, RefNames.refsStarredChanges(new Change.Id(change._number), admin.id)));

    gApi.accounts().self().unstarChange(triplet);
    change = info(triplet);
    assertThat(change.starred).isNull();
    assertThat(change.stars).isNull();
    refUpdateCounter.assertRefUpdateFor(
        RefUpdateCounter.projectRef(
            allUsers, RefNames.refsStarredChanges(new Change.Id(change._number), admin.id)));

    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void starUnstarChangeWithLabels() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    refUpdateCounter.clear();

    assertThat(gApi.accounts().self().getStars(triplet)).isEmpty();
    assertThat(gApi.accounts().self().getStarredChanges()).isEmpty();

    gApi.accounts()
        .self()
        .setStars(triplet, new StarsInput(ImmutableSet.of(DEFAULT_LABEL, "red", "blue")));
    ChangeInfo change = info(triplet);
    assertThat(change.starred).isTrue();
    assertThat(change.stars).containsExactly("blue", "red", DEFAULT_LABEL).inOrder();
    assertThat(gApi.accounts().self().getStars(triplet))
        .containsExactly("blue", "red", DEFAULT_LABEL)
        .inOrder();
    List<ChangeInfo> starredChanges = gApi.accounts().self().getStarredChanges();
    assertThat(starredChanges).hasSize(1);
    ChangeInfo starredChange = starredChanges.get(0);
    assertThat(starredChange._number).isEqualTo(r.getChange().getId().get());
    assertThat(starredChange.starred).isTrue();
    assertThat(starredChange.stars).containsExactly("blue", "red", DEFAULT_LABEL).inOrder();
    refUpdateCounter.assertRefUpdateFor(
        RefUpdateCounter.projectRef(
            allUsers, RefNames.refsStarredChanges(new Change.Id(change._number), admin.id)));

    gApi.accounts()
        .self()
        .setStars(
            triplet,
            new StarsInput(ImmutableSet.of("yellow"), ImmutableSet.of(DEFAULT_LABEL, "blue")));
    change = info(triplet);
    assertThat(change.starred).isNull();
    assertThat(change.stars).containsExactly("red", "yellow").inOrder();
    assertThat(gApi.accounts().self().getStars(triplet)).containsExactly("red", "yellow").inOrder();
    starredChanges = gApi.accounts().self().getStarredChanges();
    assertThat(starredChanges).hasSize(1);
    starredChange = starredChanges.get(0);
    assertThat(starredChange._number).isEqualTo(r.getChange().getId().get());
    assertThat(starredChange.starred).isNull();
    assertThat(starredChange.stars).containsExactly("red", "yellow").inOrder();
    refUpdateCounter.assertRefUpdateFor(
        RefUpdateCounter.projectRef(
            allUsers, RefNames.refsStarredChanges(new Change.Id(change._number), admin.id)));

    accountIndexedCounter.assertNoReindex();

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to get stars of another account");
    gApi.accounts().id(Integer.toString((admin.id.get()))).getStars(triplet);
  }

  @Test
  public void starWithInvalidLabels() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid labels: another invalid label, invalid label");
    gApi.accounts()
        .self()
        .setStars(
            triplet,
            new StarsInput(
                ImmutableSet.of(DEFAULT_LABEL, "invalid label", "blue", "another invalid label")));
  }

  @Test
  public void deleteStarLabelsFromChangeWithoutStarLabels() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    assertThat(gApi.accounts().self().getStars(triplet)).isEmpty();

    gApi.accounts().self().setStars(triplet, new StarsInput());

    assertThat(gApi.accounts().self().getStars(triplet)).isEmpty();
  }

  @Test
  public void starWithDefaultAndIgnoreLabel() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    exception.expect(BadRequestException.class);
    exception.expectMessage(
        "The labels "
            + DEFAULT_LABEL
            + " and "
            + IGNORE_LABEL
            + " are mutually exclusive."
            + " Only one of them can be set.");
    gApi.accounts()
        .self()
        .setStars(triplet, new StarsInput(ImmutableSet.of(DEFAULT_LABEL, "blue", IGNORE_LABEL)));
  }

  @Test
  public void ignoreChangeBySetStars() throws Exception {
    TestAccount user2 = accountCreator.user2();
    accountIndexedCounter.clear();

    PushOneCommit.Result r = createChange();

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    in = new AddReviewerInput();
    in.reviewer = user2.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    setApiUser(user);
    gApi.accounts().self().setStars(r.getChangeId(), new StarsInput(ImmutableSet.of(IGNORE_LABEL)));

    sender.clear();
    setApiUser(admin);
    gApi.changes().id(r.getChangeId()).abandon();
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).rcpt()).containsExactly(user2.emailAddress);
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void addReviewerToIgnoredChange() throws Exception {
    PushOneCommit.Result r = createChange();

    setApiUser(user);
    gApi.accounts().self().setStars(r.getChangeId(), new StarsInput(ImmutableSet.of(IGNORE_LABEL)));

    sender.clear();
    setApiUser(admin);

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message message = messages.get(0);
    assertThat(message.rcpt()).containsExactly(user.emailAddress);
    assertMailReplyTo(message, admin.email);
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void suggestAccounts() throws Exception {
    String adminUsername = "admin";
    List<AccountInfo> result = gApi.accounts().suggestAccounts().withQuery(adminUsername).get();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).username).isEqualTo(adminUsername);

    List<AccountInfo> resultShortcutApi = gApi.accounts().suggestAccounts(adminUsername).get();
    assertThat(resultShortcutApi).hasSize(result.size());

    List<AccountInfo> emptyResult = gApi.accounts().suggestAccounts("unknown").get();
    assertThat(emptyResult).isEmpty();
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void addEmail() throws Exception {
    List<String> emails = ImmutableList.of("new.email@example.com", "new.email@example.systems");
    Set<String> currentEmails = getEmails();
    for (String email : emails) {
      assertThat(currentEmails).doesNotContain(email);
      EmailInput input = newEmailInput(email);
      gApi.accounts().self().addEmail(input);
      accountIndexedCounter.assertReindexOf(admin);
    }

    resetCurrentApiUser();
    assertThat(getEmails()).containsAllIn(emails);
  }

  @Test
  public void addInvalidEmail() throws Exception {
    List<String> emails =
        ImmutableList.of(
            // Missing domain part
            "new.email",

            // Missing domain part
            "new.email@",

            // Missing user part
            "@example.com",

            // Non-supported TLD  (see tlds-alpha-by-domain.txt)
            "new.email@example.africa");
    for (String email : emails) {
      EmailInput input = newEmailInput(email);
      try {
        gApi.accounts().self().addEmail(input);
        fail("Expected BadRequestException for invalid email address: " + email);
      } catch (BadRequestException e) {
        assertThat(e).hasMessageThat().isEqualTo("invalid email address");
      }
    }
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void cannotAddNonConfirmedEmailWithoutModifyAccountPermission() throws Exception {
    TestAccount account = accountCreator.create(name("user"));
    EmailInput input = newEmailInput("test@test.com");
    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.accounts().id(account.username).addEmail(input);
  }

  @Test
  public void cannotAddEmailAddressUsedByAnotherAccount() throws Exception {
    String email = "new.email@example.com";
    EmailInput input = newEmailInput(email);
    gApi.accounts().self().addEmail(input);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Identity 'mailto:" + email + "' in use by another account");
    gApi.accounts().id(user.username).addEmail(input);
  }

  @Test
  @GerritConfig(
      name = "auth.registerEmailPrivateKey",
      value = "HsOc6l+2lhS9G7sE/RsnS7Z6GJjdRDX14co=")
  public void addEmailSendsConfirmationEmail() throws Exception {
    String email = "new.email@example.com";
    EmailInput input = newEmailInput(email, false);
    gApi.accounts().self().addEmail(input);

    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(new Address(email));
  }

  @Test
  public void deleteEmail() throws Exception {
    String email = "foo.bar@example.com";
    EmailInput input = newEmailInput(email);
    gApi.accounts().self().addEmail(input);

    resetCurrentApiUser();
    assertThat(getEmails()).contains(email);

    accountIndexedCounter.clear();
    gApi.accounts().self().deleteEmail(input.email);
    accountIndexedCounter.assertReindexOf(admin);

    resetCurrentApiUser();
    assertThat(getEmails()).doesNotContain(email);
  }

  @Test
  public void deleteEmailFromCustomExternalIdSchemes() throws Exception {
    String email = "foo.bar@example.com";
    String extId1 = "foo:bar";
    String extId2 = "foo:baz";
    List<ExternalId> extIds =
        ImmutableList.of(
            ExternalId.createWithEmail(ExternalId.Key.parse(extId1), admin.id, email),
            ExternalId.createWithEmail(ExternalId.Key.parse(extId2), admin.id, email));
    externalIdsUpdateFactory.create().insert(extIds);
    accountIndexedCounter.assertReindexOf(admin);
    assertThat(
            gApi.accounts().self().getExternalIds().stream().map(e -> e.identity).collect(toSet()))
        .containsAllOf(extId1, extId2);

    resetCurrentApiUser();
    assertThat(getEmails()).contains(email);

    gApi.accounts().self().deleteEmail(email);
    accountIndexedCounter.assertReindexOf(admin);

    resetCurrentApiUser();
    assertThat(getEmails()).doesNotContain(email);
    assertThat(
            gApi.accounts().self().getExternalIds().stream().map(e -> e.identity).collect(toSet()))
        .containsNoneOf(extId1, extId2);
  }

  @Test
  public void deleteEmailOfOtherUser() throws Exception {
    String email = "foo.bar@example.com";
    EmailInput input = new EmailInput();
    input.email = email;
    input.noConfirmation = true;
    gApi.accounts().id(user.id.get()).addEmail(input);
    accountIndexedCounter.assertReindexOf(user);

    setApiUser(user);
    assertThat(getEmails()).contains(email);

    // admin can delete email of user
    setApiUser(admin);
    gApi.accounts().id(user.id.get()).deleteEmail(email);
    accountIndexedCounter.assertReindexOf(user);

    setApiUser(user);
    assertThat(getEmails()).doesNotContain(email);

    // user cannot delete email of admin
    exception.expect(AuthException.class);
    exception.expectMessage("modify account not permitted");
    gApi.accounts().id(admin.id.get()).deleteEmail(admin.email);
  }

  @Test
  public void lookUpByEmail() throws Exception {
    // exact match with scheme "mailto:"
    assertEmail(emails.getAccountFor(admin.email), admin);

    // exact match with other scheme
    String email = "foo.bar@example.com";
    externalIdsUpdateFactory
        .create()
        .insert(ExternalId.createWithEmail(ExternalId.Key.parse("foo:bar"), admin.id, email));
    assertEmail(emails.getAccountFor(email), admin);

    // wrong case doesn't match
    assertThat(emails.getAccountFor(admin.email.toUpperCase(Locale.US))).isEmpty();

    // prefix doesn't match
    assertThat(emails.getAccountFor(admin.email.substring(0, admin.email.indexOf('@')))).isEmpty();

    // non-existing doesn't match
    assertThat(emails.getAccountFor("non-existing@example.com")).isEmpty();

    // lookup several accounts by email at once
    ImmutableSetMultimap<String, Account.Id> byEmails =
        emails.getAccountsFor(admin.email, user.email);
    assertEmail(byEmails.get(admin.email), admin);
    assertEmail(byEmails.get(user.email), user);
  }

  @Test
  public void lookUpByPreferredEmail() throws Exception {
    // create an inconsistent account that has a preferred email without external ID
    String prefix = "foo.preferred";
    String prefEmail = prefix + "@example.com";
    TestAccount foo = accountCreator.create(name("foo"));
    accountsUpdate.create().update(foo.id, a -> a.setPreferredEmail(prefEmail));

    // verify that the account is still found when using the preferred email to lookup the account
    ImmutableSet<Account.Id> accountsByPrefEmail = emails.getAccountFor(prefEmail);
    assertThat(accountsByPrefEmail).hasSize(1);
    assertThat(Iterables.getOnlyElement(accountsByPrefEmail)).isEqualTo(foo.id);

    // look up by email prefix doesn't find the account
    accountsByPrefEmail = emails.getAccountFor(prefix);
    assertThat(accountsByPrefEmail).isEmpty();

    // look up by other case doesn't find the account
    accountsByPrefEmail = emails.getAccountFor(prefEmail.toUpperCase(Locale.US));
    assertThat(accountsByPrefEmail).isEmpty();
  }

  @Test
  public void putStatus() throws Exception {
    List<String> statuses = ImmutableList.of("OOO", "Busy");
    AccountInfo info;
    for (String status : statuses) {
      gApi.accounts().self().setStatus(status);
      admin.status = status;
      info = gApi.accounts().self().get();
      assertUser(info, admin);
      accountIndexedCounter.assertReindexOf(admin);
    }
  }

  @Test
  public void setName() throws Exception {
    gApi.accounts().self().setName("Admin McAdminface");
    assertThat(gApi.accounts().self().get().name).isEqualTo("Admin McAdminface");
  }

  @Test
  public void adminCanSetNameOfOtherUser() throws Exception {
    gApi.accounts().id(user.username).setName("User McUserface");
    assertThat(gApi.accounts().id(user.username).get().name).isEqualTo("User McUserface");
  }

  @Test
  public void userCannotSetNameOfOtherUser() throws Exception {
    setApiUser(user);
    exception.expect(AuthException.class);
    gApi.accounts().id(admin.username).setName("Admin McAdminface");
  }

  @Test
  @Sandboxed
  public void userCanSetNameOfOtherUserWithModifyAccountPermission() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.MODIFY_ACCOUNT);
    gApi.accounts().id(admin.username).setName("Admin McAdminface");
    assertThat(gApi.accounts().id(admin.username).get().name).isEqualTo("Admin McAdminface");
  }

  @Test
  @Sandboxed
  public void fetchUserBranch() throws Exception {
    setApiUser(user);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, user);
    String userRefName = RefNames.refsUsers(user.id);

    // remove default READ permissions
    ProjectConfig cfg = projectCache.checkedGet(allUsers).getConfig();
    cfg.getAccessSection(RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}", true)
        .remove(new Permission(Permission.READ));
    saveProjectConfig(allUsers, cfg);

    // deny READ permission that is inherited from All-Projects
    deny(allUsers, RefNames.REFS + "*", Permission.READ, ANONYMOUS_USERS);

    // fetching user branch without READ permission fails
    try {
      fetch(allUsersRepo, userRefName + ":userRef");
      Assert.fail("user branch is visible although no READ permission is granted");
    } catch (TransportException e) {
      // expected because no READ granted on user branch
    }

    // allow each user to read its own user branch
    grant(
        allUsers,
        RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}",
        Permission.READ,
        false,
        REGISTERED_USERS);

    // fetch user branch using refs/users/YY/XXXXXXX
    fetch(allUsersRepo, userRefName + ":userRef");
    Ref userRef = allUsersRepo.getRepository().exactRef("userRef");
    assertThat(userRef).isNotNull();

    // fetch user branch using refs/users/self
    fetch(allUsersRepo, RefNames.REFS_USERS_SELF + ":userSelfRef");
    Ref userSelfRef = allUsersRepo.getRepository().getRefDatabase().exactRef("userSelfRef");
    assertThat(userSelfRef).isNotNull();
    assertThat(userSelfRef.getObjectId()).isEqualTo(userRef.getObjectId());

    accountIndexedCounter.assertNoReindex();

    // fetching user branch of another user fails
    String otherUserRefName = RefNames.refsUsers(admin.id);
    exception.expect(TransportException.class);
    exception.expectMessage("Remote does not have " + otherUserRefName + " available for fetch.");
    fetch(allUsersRepo, otherUserRefName + ":otherUserRef");
  }

  @Test
  public void pushToUserBranch() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    push.to(RefNames.refsUsers(admin.id)).assertOkStatus();
    accountIndexedCounter.assertReindexOf(admin);

    push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    push.to(RefNames.REFS_USERS_SELF).assertOkStatus();
    accountIndexedCounter.assertReindexOf(admin);
  }

  @Test
  public void pushToUserBranchForReview() throws Exception {
    String userRefName = RefNames.refsUsers(admin.id);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRefName + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    PushOneCommit.Result r = push.to(MagicBranch.NEW_CHANGE + userRefName);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRefName);
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();
    accountIndexedCounter.assertReindexOf(admin);

    push = pushFactory.create(db, admin.getIdent(), allUsersRepo);
    r = push.to(MagicBranch.NEW_CHANGE + RefNames.REFS_USERS_SELF);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRefName);
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();
    accountIndexedCounter.assertReindexOf(admin);
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewAndSubmit() throws Exception {
    String userRef = RefNames.refsUsers(admin.id);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_STATUS, "out-of-office");

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(MagicBranch.NEW_CHANGE + userRef);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRef);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();
    accountIndexedCounter.assertReindexOf(admin);

    AccountInfo info = gApi.accounts().self().get();
    assertThat(info.email).isEqualTo(admin.email);
    assertThat(info.name).isEqualTo(admin.fullName);
    assertThat(info.status).isEqualTo("out-of-office");
  }

  @Test
  public void pushAccountConfigWithPrefEmailThatDoesNotExistAsExtIdToUserBranchForReviewAndSubmit()
      throws Exception {
    TestAccount foo = accountCreator.create(name("foo"), name("foo") + "@example.com", "Foo");
    String userRef = RefNames.refsUsers(foo.id);
    accountIndexedCounter.clear();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, foo);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    String email = "some.email@example.com";
    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_PREFERRED_EMAIL, email);

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                foo.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(MagicBranch.NEW_CHANGE + userRef);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRef);

    setApiUser(foo);
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    accountIndexedCounter.assertReindexOf(foo);

    AccountInfo info = gApi.accounts().self().get();
    assertThat(info.email).isEqualTo(email);
    assertThat(info.name).isEqualTo(foo.fullName);
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewIsRejectedOnSubmitIfConfigIsInvalid()
      throws Exception {
    String userRef = RefNames.refsUsers(admin.id);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                "invalid config")
            .to(MagicBranch.NEW_CHANGE + userRef);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRef);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format(
            "invalid account configuration: commit '%s' has an invalid '%s' file for account '%s':"
                + " Invalid config file %s in commit %s",
            r.getCommit().name(),
            AccountConfig.ACCOUNT_CONFIG,
            admin.id,
            AccountConfig.ACCOUNT_CONFIG,
            r.getCommit().name()));
    gApi.changes().id(r.getChangeId()).current().submit();
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewIsRejectedOnSubmitIfPreferredEmailIsInvalid()
      throws Exception {
    String userRef = RefNames.refsUsers(admin.id);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    String noEmail = "no.email";
    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_PREFERRED_EMAIL, noEmail);

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(MagicBranch.NEW_CHANGE + userRef);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRef);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format(
            "invalid account configuration: invalid preferred email '%s' for account '%s'",
            noEmail, admin.id));
    gApi.changes().id(r.getChangeId()).current().submit();
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewIsRejectedOnSubmitIfOwnAccountIsDeactivated()
      throws Exception {
    String userRef = RefNames.refsUsers(admin.id);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    Config ac = getAccountConfig(allUsersRepo);
    ac.setBoolean(AccountConfig.ACCOUNT, null, AccountConfig.KEY_ACTIVE, false);

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(MagicBranch.NEW_CHANGE + userRef);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRef);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("invalid account configuration: cannot deactivate own account");
    gApi.changes().id(r.getChangeId()).current().submit();
  }

  @Test
  @Sandboxed
  public void pushAccountConfigToUserBranchForReviewDeactivateOtherAccount() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    TestAccount foo = accountCreator.create(name("foo"));
    assertThat(gApi.accounts().id(foo.id.get()).getActive()).isTrue();
    String userRef = RefNames.refsUsers(foo.id);
    accountIndexedCounter.clear();

    InternalGroup adminGroup =
        groupCache.get(new AccountGroup.NameKey("Administrators")).orElse(null);
    grant(allUsers, userRef, Permission.PUSH, false, adminGroup.getGroupUUID());
    grantLabel("Code-Review", -2, 2, allUsers, userRef, false, adminGroup.getGroupUUID(), false);
    grant(allUsers, userRef, Permission.SUBMIT, false, adminGroup.getGroupUUID());

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    Config ac = getAccountConfig(allUsersRepo);
    ac.setBoolean(AccountConfig.ACCOUNT, null, AccountConfig.KEY_ACTIVE, false);

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(MagicBranch.NEW_CHANGE + userRef);
    r.assertOkStatus();
    accountIndexedCounter.assertNoReindex();
    assertThat(r.getChange().change().getDest().get()).isEqualTo(userRef);

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();
    accountIndexedCounter.assertReindexOf(foo);

    assertThat(gApi.accounts().id(foo.id.get()).getActive()).isFalse();
  }

  @Test
  public void pushWatchConfigToUserBranch() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");

    Config wc = new Config();
    wc.setString(
        WatchConfig.PROJECT,
        project.get(),
        WatchConfig.KEY_NOTIFY,
        WatchConfig.NotifyValue.create(null, EnumSet.of(NotifyType.ALL_COMMENTS)).toString());
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Add project watch",
            WatchConfig.WATCH_CONFIG,
            wc.toText());
    push.to(RefNames.REFS_USERS_SELF).assertOkStatus();
    accountIndexedCounter.assertReindexOf(admin);

    String invalidNotifyValue = "]invalid[";
    wc.setString(WatchConfig.PROJECT, project.get(), WatchConfig.KEY_NOTIFY, invalidNotifyValue);
    push =
        pushFactory.create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Add invalid project watch",
            WatchConfig.WATCH_CONFIG,
            wc.toText());
    PushOneCommit.Result r = push.to(RefNames.REFS_USERS_SELF);
    r.assertErrorStatus("invalid watch configuration");
    r.assertMessage(
        String.format(
            "%s: Invalid project watch of account %d for project %s: %s",
            WatchConfig.WATCH_CONFIG, admin.getId().get(), project.get(), invalidNotifyValue));
  }

  @Test
  public void pushAccountConfigToUserBranch() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");

    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_STATUS, "out-of-office");

    pushFactory
        .create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Update account config",
            AccountConfig.ACCOUNT_CONFIG,
            ac.toText())
        .to(RefNames.REFS_USERS_SELF)
        .assertOkStatus();
    accountIndexedCounter.assertReindexOf(admin);

    AccountInfo info = gApi.accounts().self().get();
    assertThat(info.email).isEqualTo(admin.email);
    assertThat(info.name).isEqualTo(admin.fullName);
    assertThat(info.status).isEqualTo("out-of-office");
  }

  @Test
  public void pushAccountConfigToUserBranchIsRejectedIfConfigIsInvalid() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                "invalid config")
            .to(RefNames.REFS_USERS_SELF);
    r.assertErrorStatus("invalid account configuration");
    r.assertMessage(
        String.format(
            "commit '%s' has an invalid '%s' file for account '%s':"
                + " Invalid config file %s in commit %s",
            r.getCommit().name(),
            AccountConfig.ACCOUNT_CONFIG,
            admin.id,
            AccountConfig.ACCOUNT_CONFIG,
            r.getCommit().name()));
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void pushAccountConfigToUserBranchIsRejectedIfPreferredEmailIsInvalid() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");

    String noEmail = "no.email";
    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_PREFERRED_EMAIL, noEmail);

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(RefNames.REFS_USERS_SELF);
    r.assertErrorStatus("invalid account configuration");
    r.assertMessage(
        String.format("invalid preferred email '%s' for account '%s'", noEmail, admin.id));
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  public void pushAccountConfigToUserBranchInvalidPreferredEmailButNotChanged() throws Exception {
    TestAccount foo = accountCreator.create(name("foo"), name("foo") + "@example.com", "Foo");
    String userRef = RefNames.refsUsers(foo.id);

    String noEmail = "no.email";
    accountsUpdate.create().update(foo.id, a -> a.setPreferredEmail(noEmail));
    accountIndexedCounter.clear();

    grant(allUsers, userRef, Permission.PUSH, false, REGISTERED_USERS);
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, foo);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    String status = "in vacation";
    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_STATUS, status);

    pushFactory
        .create(
            db,
            foo.getIdent(),
            allUsersRepo,
            "Update account config",
            AccountConfig.ACCOUNT_CONFIG,
            ac.toText())
        .to(userRef)
        .assertOkStatus();
    accountIndexedCounter.assertReindexOf(foo);

    AccountInfo info = gApi.accounts().id(foo.id.get()).get();
    assertThat(info.email).isEqualTo(noEmail);
    assertThat(info.name).isEqualTo(foo.fullName);
    assertThat(info.status).isEqualTo(status);
  }

  @Test
  public void pushAccountConfigToUserBranchIfPreferredEmailDoesNotExistAsExtId() throws Exception {
    TestAccount foo = accountCreator.create(name("foo"), name("foo") + "@example.com", "Foo");
    String userRef = RefNames.refsUsers(foo.id);
    accountIndexedCounter.clear();

    InternalGroup adminGroup =
        groupCache.get(new AccountGroup.NameKey("Administrators")).orElse(null);
    grant(allUsers, userRef, Permission.PUSH, false, adminGroup.getGroupUUID());

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, foo);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    String email = "some.email@example.com";
    Config ac = getAccountConfig(allUsersRepo);
    ac.setString(AccountConfig.ACCOUNT, null, AccountConfig.KEY_PREFERRED_EMAIL, email);

    pushFactory
        .create(
            db,
            foo.getIdent(),
            allUsersRepo,
            "Update account config",
            AccountConfig.ACCOUNT_CONFIG,
            ac.toText())
        .to(userRef)
        .assertOkStatus();
    accountIndexedCounter.assertReindexOf(foo);

    AccountInfo info = gApi.accounts().id(foo.id.get()).get();
    assertThat(info.email).isEqualTo(email);
    assertThat(info.name).isEqualTo(foo.fullName);
  }

  @Test
  public void pushAccountConfigToUserBranchIsRejectedIfOwnAccountIsDeactivated() throws Exception {
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.refsUsers(admin.id) + ":userRef");
    allUsersRepo.reset("userRef");

    Config ac = getAccountConfig(allUsersRepo);
    ac.setBoolean(AccountConfig.ACCOUNT, null, AccountConfig.KEY_ACTIVE, false);

    PushOneCommit.Result r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                allUsersRepo,
                "Update account config",
                AccountConfig.ACCOUNT_CONFIG,
                ac.toText())
            .to(RefNames.REFS_USERS_SELF);
    r.assertErrorStatus("invalid account configuration");
    r.assertMessage("cannot deactivate own account");
    accountIndexedCounter.assertNoReindex();
  }

  @Test
  @Sandboxed
  public void pushAccountConfigToUserBranchDeactivateOtherAccount() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    TestAccount foo = accountCreator.create(name("foo"));
    assertThat(gApi.accounts().id(foo.id.get()).getActive()).isTrue();
    String userRef = RefNames.refsUsers(foo.id);
    accountIndexedCounter.clear();

    InternalGroup adminGroup =
        groupCache.get(new AccountGroup.NameKey("Administrators")).orElse(null);
    grant(allUsers, userRef, Permission.PUSH, false, adminGroup.getGroupUUID());

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, userRef + ":userRef");
    allUsersRepo.reset("userRef");

    Config ac = getAccountConfig(allUsersRepo);
    ac.setBoolean(AccountConfig.ACCOUNT, null, AccountConfig.KEY_ACTIVE, false);

    pushFactory
        .create(
            db,
            admin.getIdent(),
            allUsersRepo,
            "Update account config",
            AccountConfig.ACCOUNT_CONFIG,
            ac.toText())
        .to(userRef)
        .assertOkStatus();
    accountIndexedCounter.assertReindexOf(foo);

    assertThat(gApi.accounts().id(foo.id.get()).getActive()).isFalse();
  }

  @Test
  @Sandboxed
  public void cannotCreateUserBranch() throws Exception {
    grant(allUsers, RefNames.REFS_USERS + "*", Permission.CREATE);
    grant(allUsers, RefNames.REFS_USERS + "*", Permission.PUSH);

    String userRef = RefNames.refsUsers(new Account.Id(seq.nextAccountId()));
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), allUsersRepo).to(userRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create user branch.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNull();
    }
  }

  @Test
  @Sandboxed
  public void createUserBranchWithAccessDatabaseCapability() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    grant(allUsers, RefNames.REFS_USERS + "*", Permission.CREATE);
    grant(allUsers, RefNames.REFS_USERS + "*", Permission.PUSH);

    String userRef = RefNames.refsUsers(new Account.Id(seq.nextAccountId()));
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    pushFactory.create(db, admin.getIdent(), allUsersRepo).to(userRef).assertOkStatus();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNotNull();
    }
  }

  @Test
  @Sandboxed
  public void cannotCreateNonUserBranchUnderRefsUsersWithAccessDatabaseCapability()
      throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    grant(allUsers, RefNames.REFS_USERS + "*", Permission.CREATE);
    grant(allUsers, RefNames.REFS_USERS + "*", Permission.PUSH);

    String userRef = RefNames.REFS_USERS + "foo";
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), allUsersRepo).to(userRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create non-user branch under refs/users/.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNull();
    }
  }

  @Test
  @Sandboxed
  public void createDefaultUserBranch() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(RefNames.REFS_USERS_DEFAULT)).isNull();
    }

    grant(allUsers, RefNames.REFS_USERS_DEFAULT, Permission.CREATE);
    grant(allUsers, RefNames.REFS_USERS_DEFAULT, Permission.PUSH);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    pushFactory
        .create(db, admin.getIdent(), allUsersRepo)
        .to(RefNames.REFS_USERS_DEFAULT)
        .assertOkStatus();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(RefNames.REFS_USERS_DEFAULT)).isNotNull();
    }
  }

  @Test
  @Sandboxed
  public void cannotDeleteUserBranch() throws Exception {
    grant(
        allUsers,
        RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}",
        Permission.DELETE,
        true,
        REGISTERED_USERS);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    String userRef = RefNames.refsUsers(admin.id);
    PushResult r = deleteRef(allUsersRepo, userRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(userRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(refUpdate.getMessage()).contains("Not allowed to delete user branch.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNotNull();
    }
  }

  @Test
  @Sandboxed
  public void deleteUserBranchWithAccessDatabaseCapability() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    grant(
        allUsers,
        RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}",
        Permission.DELETE,
        true,
        REGISTERED_USERS);

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    String userRef = RefNames.refsUsers(admin.id);
    PushResult r = deleteRef(allUsersRepo, userRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(userRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNull();
    }

    assertThat(accountCache.getOrNull(admin.id)).isNull();
    assertThat(accountQueryProvider.get().byDefault(admin.id.toString())).isEmpty();
  }

  @Test
  public void addGpgKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    String id = key.getKeyIdString();
    addExternalIdEmail(admin, "test1@example.com");

    assertKeyMapContains(key, addGpgKey(key.getPublicKeyArmored()));
    assertKeys(key);

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(id);
    gApi.accounts().self().gpgKey(id).get();
  }

  @Test
  public void reAddExistingGpgKey() throws Exception {
    addExternalIdEmail(admin, "test5@example.com");
    TestKey key = validKeyWithSecondUserId();
    String id = key.getKeyIdString();
    PGPPublicKey pk = key.getPublicKey();

    GpgKeyInfo info = addGpgKey(armor(pk)).get(id);
    assertThat(info.userIds).hasSize(2);
    assertIteratorSize(2, getOnlyKeyFromStore(key).getUserIDs());

    pk = PGPPublicKey.removeCertification(pk, "foo:myId");
    info = addGpgKey(armor(pk)).get(id);
    assertThat(info.userIds).hasSize(1);
    assertIteratorSize(1, getOnlyKeyFromStore(key).getUserIDs());
  }

  @Test
  public void addOtherUsersGpgKey_Conflict() throws Exception {
    // Both users have a matching external ID for this key.
    addExternalIdEmail(admin, "test5@example.com");
    externalIdsUpdate.insert(ExternalId.create("foo", "myId", user.getId()));
    accountIndexedCounter.assertReindexOf(user);

    TestKey key = validKeyWithSecondUserId();
    addGpgKey(key.getPublicKeyArmored());
    setApiUser(user);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("GPG key already associated with another account");
    addGpgKey(key.getPublicKeyArmored());
  }

  @Test
  public void listGpgKeys() throws Exception {
    List<TestKey> keys = allValidKeys();
    List<String> toAdd = new ArrayList<>(keys.size());
    for (TestKey key : keys) {
      addExternalIdEmail(admin, PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
      toAdd.add(key.getPublicKeyArmored());
    }
    gApi.accounts().self().putGpgKeys(toAdd, ImmutableList.<String>of());
    assertKeys(keys);
    accountIndexedCounter.assertReindexOf(admin);
  }

  @Test
  public void deleteGpgKey() throws Exception {
    TestKey key = validKeyWithoutExpiration();
    String id = key.getKeyIdString();
    addExternalIdEmail(admin, "test1@example.com");
    addGpgKey(key.getPublicKeyArmored());
    assertKeys(key);

    gApi.accounts().self().gpgKey(id).delete();
    accountIndexedCounter.assertReindexOf(admin);
    assertKeys();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(id);
    gApi.accounts().self().gpgKey(id).get();
  }

  @Test
  public void addAndRemoveGpgKeys() throws Exception {
    for (TestKey key : allValidKeys()) {
      addExternalIdEmail(admin, PushCertificateIdent.parse(key.getFirstUserId()).getEmailAddress());
    }
    TestKey key1 = validKeyWithoutExpiration();
    TestKey key2 = validKeyWithExpiration();
    TestKey key5 = validKeyWithSecondUserId();

    Map<String, GpgKeyInfo> infos =
        gApi.accounts()
            .self()
            .putGpgKeys(
                ImmutableList.of(key1.getPublicKeyArmored(), key2.getPublicKeyArmored()),
                ImmutableList.of(key5.getKeyIdString()));
    assertThat(infos.keySet()).containsExactly(key1.getKeyIdString(), key2.getKeyIdString());
    assertKeys(key1, key2);
    accountIndexedCounter.assertReindexOf(admin);

    infos =
        gApi.accounts()
            .self()
            .putGpgKeys(
                ImmutableList.of(key5.getPublicKeyArmored()),
                ImmutableList.of(key1.getKeyIdString()));
    assertThat(infos.keySet()).containsExactly(key1.getKeyIdString(), key5.getKeyIdString());
    assertKeyMapContains(key5, infos);
    assertThat(infos.get(key1.getKeyIdString()).key).isNull();
    assertKeys(key2, key5);
    accountIndexedCounter.assertReindexOf(admin);

    exception.expect(BadRequestException.class);
    exception.expectMessage("Cannot both add and delete key: " + keyToString(key2.getPublicKey()));
    infos =
        gApi.accounts()
            .self()
            .putGpgKeys(
                ImmutableList.of(key2.getPublicKeyArmored()),
                ImmutableList.of(key2.getKeyIdString()));
  }

  @Test
  public void addMalformedGpgKey() throws Exception {
    String key = "-----BEGIN PGP PUBLIC KEY BLOCK-----\n\ntest\n-----END PGP PUBLIC KEY BLOCK-----";
    exception.expect(BadRequestException.class);
    exception.expectMessage("Failed to parse GPG keys");
    addGpgKey(key);
  }

  @Test
  @UseSsh
  public void sshKeys() throws Exception {
    //
    // The test account should initially have exactly one ssh key
    List<SshKeyInfo> info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(1);
    assertSequenceNumbers(info);
    SshKeyInfo key = info.get(0);
    String inital = AccountCreator.publicKey(admin.sshKey, admin.email);
    assertThat(key.sshPublicKey).isEqualTo(inital);
    accountIndexedCounter.assertNoReindex();

    // Add a new key
    String newKey = AccountCreator.publicKey(AccountCreator.genSshKey(), admin.email);
    gApi.accounts().self().addSshKey(newKey);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(2);
    assertSequenceNumbers(info);
    accountIndexedCounter.assertReindexOf(admin);

    // Add an existing key (the request succeeds, but the key isn't added again)
    gApi.accounts().self().addSshKey(inital);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(2);
    assertSequenceNumbers(info);
    accountIndexedCounter.assertNoReindex();

    // Add another new key
    String newKey2 = AccountCreator.publicKey(AccountCreator.genSshKey(), admin.email);
    gApi.accounts().self().addSshKey(newKey2);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(3);
    assertSequenceNumbers(info);
    accountIndexedCounter.assertReindexOf(admin);

    // Delete second key
    gApi.accounts().self().deleteSshKey(2);
    info = gApi.accounts().self().listSshKeys();
    assertThat(info).hasSize(2);
    assertThat(info.get(0).seq).isEqualTo(1);
    assertThat(info.get(1).seq).isEqualTo(3);
    accountIndexedCounter.assertReindexOf(admin);
  }

  // reindex is tested by {@link AbstractQueryAccountsTest#reindex}
  @Test
  public void reindexPermissions() throws Exception {
    // admin can reindex any account
    setApiUser(admin);
    gApi.accounts().id(user.username).index();
    accountIndexedCounter.assertReindexOf(user);

    // user can reindex own account
    setApiUser(user);
    gApi.accounts().self().index();
    accountIndexedCounter.assertReindexOf(user);

    // user cannot reindex any account
    exception.expect(AuthException.class);
    exception.expectMessage("modify account not permitted");
    gApi.accounts().id(admin.username).index();
  }

  @Test
  @Sandboxed
  public void checkConsistency() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    resetCurrentApiUser();

    // Create an account with a preferred email.
    String username = name("foo");
    String email = username + "@example.com";
    TestAccount account = accountCreator.create(username, email, "Foo Bar");

    ConsistencyCheckInput input = new ConsistencyCheckInput();
    input.checkAccounts = new CheckAccountsInput();
    ConsistencyCheckInfo checkInfo = gApi.config().server().checkConsistency(input);
    assertThat(checkInfo.checkAccountsResult.problems).isEmpty();

    Set<ConsistencyProblemInfo> expectedProblems = new HashSet<>();

    // Delete the external ID for the preferred email. This makes the account inconsistent since it
    // now doesn't have an external ID for its preferred email.
    externalIdsUpdate.delete(ExternalId.createEmail(account.getId(), email));
    expectedProblems.add(
        new ConsistencyProblemInfo(
            ConsistencyProblemInfo.Status.ERROR,
            "Account '"
                + account.getId().get()
                + "' has no external ID for its preferred email '"
                + email
                + "'"));

    checkInfo = gApi.config().server().checkConsistency(input);
    assertThat(checkInfo.checkAccountsResult.problems).hasSize(expectedProblems.size());
    assertThat(checkInfo.checkAccountsResult.problems).containsExactlyElementsIn(expectedProblems);
  }

  @Test
  public void internalQueryFindActiveAndInactiveAccounts() throws Exception {
    String name = name("foo");
    assertThat(accountQueryProvider.get().byDefault(name)).isEmpty();

    TestAccount foo1 = accountCreator.create(name + "-1");
    assertThat(gApi.accounts().id(foo1.username).getActive()).isTrue();

    TestAccount foo2 = accountCreator.create(name + "-2");
    gApi.accounts().id(foo2.username).setActive(false);
    assertThat(gApi.accounts().id(foo2.username).getActive()).isFalse();

    assertThat(accountQueryProvider.get().byDefault(name)).hasSize(2);
  }

  @Test
  public void checkMetaId() throws Exception {
    // metaId is set when account is loaded
    assertThat(accounts.get(admin.getId()).getMetaId()).isEqualTo(getMetaId(admin.getId()));

    // metaId is set when account is created
    AccountsUpdate au = accountsUpdate.create();
    Account.Id accountId = new Account.Id(seq.nextAccountId());
    Account account = au.insert(accountId, a -> {});
    assertThat(account.getMetaId()).isEqualTo(getMetaId(accountId));

    // metaId is set when account is updated
    Account updatedAccount = au.update(accountId, a -> a.setFullName("foo"));
    assertThat(account.getMetaId()).isNotEqualTo(updatedAccount.getMetaId());
    assertThat(updatedAccount.getMetaId()).isEqualTo(getMetaId(accountId));

    // metaId is set when account is replaced
    Account newAccount = new Account(accountId, TimeUtil.nowTs());
    au.replace(newAccount);
    assertThat(updatedAccount.getMetaId()).isNotEqualTo(newAccount.getMetaId());
    assertThat(newAccount.getMetaId()).isEqualTo(getMetaId(accountId));
  }

  private EmailInput newEmailInput(String email, boolean noConfirmation) {
    EmailInput input = new EmailInput();
    input.email = email;
    input.noConfirmation = noConfirmation;
    return input;
  }

  private EmailInput newEmailInput(String email) {
    return newEmailInput(email, true);
  }

  private String getMetaId(Account.Id accountId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.refsUsers(accountId));
      return ref != null ? ref.getObjectId().name() : null;
    }
  }

  @Test
  public void createUserWithValidUsername() throws Exception {
    ImmutableList<String> names =
        ImmutableList.of(
            "user@domain",
            "user-name",
            "user_name",
            "1234",
            "user1234",
            "1234@domain",
            "user!+alias{*}#$%&’^=~|@domain");
    for (String name : names) {
      gApi.accounts().create(name);
    }
  }

  @Test
  public void createUserWithInvalidUsername() throws Exception {
    ImmutableList<String> invalidNames =
        ImmutableList.of(
            "@", "@foo", "-", "-foo", "_", "_foo", "!", "+", "{", "}", "*", "%", "#", "$", "&", "’",
            "^", "=", "~");
    for (String name : invalidNames) {
      try {
        gApi.accounts().create(name);
        fail(String.format("Expected BadRequestException for username [%s]", name));
      } catch (BadRequestException e) {
        assertThat(e).hasMessageThat().isEqualTo(String.format("Invalid username '%s'", name));
      }
    }
  }

  @Test
  public void groups() throws Exception {
    assertGroups(
        admin.username, ImmutableList.of("Anonymous Users", "Registered Users", "Administrators"));

    // TODO: update when test user is fixed to be included in "Anonymous Users" and
    //      "Registered Users" groups
    assertGroups(user.username, ImmutableList.of());

    String group = createGroup("group");
    String newUser = createAccount("user1", group);
    assertGroups(newUser, ImmutableList.of(group));
  }

  @Test
  public void updateDisplayName() throws Exception {
    String name = name("test");
    gApi.accounts().create(name);
    AuthRequest who = AuthRequest.forUser(name);
    accountManager.authenticate(who);
    assertThat(gApi.accounts().id(name).get().name).isEqualTo(name);
    who.setDisplayName("Something Else");
    accountManager.authenticate(who);
    assertThat(gApi.accounts().id(name).get().name).isEqualTo("Something Else");
  }

  private void assertGroups(String user, List<String> expected) throws Exception {
    List<String> actual =
        gApi.accounts().id(user).getGroups().stream().map(g -> g.name).collect(toList());
    assertThat(actual).containsExactlyElementsIn(expected);
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
    List<?> lst = ImmutableList.copyOf(it);
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
    assertThat(keyMap.keySet())
        .named("keys returned by listGpgKeys()")
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
    Account.Id currAccountId = atrScope.get().getUser().getAccountId();
    Iterable<String> expectedFps =
        expected.transform(k -> BaseEncoding.base16().encode(k.getPublicKey().getFingerprint()));
    Iterable<String> actualFps =
        externalIds
            .byAccount(currAccountId, SCHEME_GPGKEY)
            .stream()
            .map(e -> e.key().id())
            .collect(toSet());
    assertThat(actualFps).named("external IDs in database").containsExactlyElementsIn(expectedFps);

    // Check raw stored keys.
    for (TestKey key : expected) {
      getOnlyKeyFromStore(key);
    }
  }

  private static void assertKeyEquals(TestKey expected, GpgKeyInfo actual) {
    String id = expected.getKeyIdString();
    assertThat(actual.id).named(id).isEqualTo(id);
    assertThat(actual.fingerprint)
        .named(id)
        .isEqualTo(Fingerprint.toString(expected.getPublicKey().getFingerprint()));
    List<String> userIds = ImmutableList.copyOf(expected.getPublicKey().getUserIDs());
    assertThat(actual.userIds).named(id).containsExactlyElementsIn(userIds);
    assertThat(actual.key).named(id).startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----\n");
    assertThat(actual.status).isEqualTo(GpgKeyInfo.Status.TRUSTED);
    assertThat(actual.problems).isEmpty();
  }

  private void addExternalIdEmail(TestAccount account, String email) throws Exception {
    checkNotNull(email);
    externalIdsUpdate.insert(
        ExternalId.createWithEmail(name("test"), email, account.getId(), email));
    accountIndexedCounter.assertReindexOf(account);
    setApiUser(account);
  }

  private Map<String, GpgKeyInfo> addGpgKey(String armored) throws Exception {
    Map<String, GpgKeyInfo> gpgKeys =
        gApi.accounts().self().putGpgKeys(ImmutableList.of(armored), ImmutableList.<String>of());
    accountIndexedCounter.assertReindexOf(gApi.accounts().self().get());
    return gpgKeys;
  }

  private void assertUser(AccountInfo info, TestAccount account) throws Exception {
    assertThat(info.name).isEqualTo(account.fullName);
    assertThat(info.email).isEqualTo(account.email);
    assertThat(info.username).isEqualTo(account.username);
    assertThat(info.status).isEqualTo(account.status);
  }

  private Set<String> getEmails() throws RestApiException {
    return gApi.accounts().self().getEmails().stream().map(e -> e.email).collect(toSet());
  }

  private void assertEmail(Set<Account.Id> accounts, TestAccount expectedAccount) {
    assertThat(accounts).hasSize(1);
    assertThat(Iterables.getOnlyElement(accounts)).isEqualTo(expectedAccount.getId());
  }

  private Config getAccountConfig(TestRepository<?> allUsersRepo) throws Exception {
    Config ac = new Config();
    try (TreeWalk tw =
        TreeWalk.forPath(
            allUsersRepo.getRepository(),
            AccountConfig.ACCOUNT_CONFIG,
            getHead(allUsersRepo.getRepository()).getTree())) {
      assertThat(tw).isNotNull();
      ac.fromText(
          new String(
              allUsersRepo
                  .getRevWalk()
                  .getObjectReader()
                  .open(tw.getObjectId(0), OBJ_BLOB)
                  .getBytes(),
              UTF_8));
    }
    return ac;
  }

  private static class AccountIndexedCounter implements AccountIndexedListener {
    private final AtomicLongMap<Integer> countsByAccount = AtomicLongMap.create();

    @Override
    public void onAccountIndexed(int id) {
      countsByAccount.incrementAndGet(id);
    }

    void clear() {
      countsByAccount.clear();
    }

    long getCount(Account.Id accountId) {
      return countsByAccount.get(accountId.get());
    }

    void assertReindexOf(TestAccount testAccount) {
      assertReindexOf(testAccount, 1);
    }

    void assertReindexOf(AccountInfo accountInfo) {
      assertReindexOf(new Account.Id(accountInfo._accountId), 1);
    }

    void assertReindexOf(TestAccount testAccount, int expectedCount) {
      assertThat(getCount(testAccount.id)).isEqualTo(expectedCount);
      assertThat(countsByAccount).hasSize(1);
      clear();
    }

    void assertReindexOf(Account.Id accountId, int expectedCount) {
      assertThat(getCount(accountId)).isEqualTo(expectedCount);
      countsByAccount.remove(accountId.get());
    }

    void assertNoReindex() {
      assertThat(countsByAccount).isEmpty();
    }
  }

  private static class RefUpdateCounter implements GitReferenceUpdatedListener {
    private final AtomicLongMap<String> countsByProjectRefs = AtomicLongMap.create();

    static String projectRef(Project.NameKey project, String ref) {
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

    long getCount(String projectRef) {
      return countsByProjectRefs.get(projectRef);
    }

    void assertRefUpdateFor(String... projectRefs) {
      Map<String, Integer> expectedRefUpdateCounts = new HashMap<>();
      for (String projectRef : projectRefs) {
        expectedRefUpdateCounts.put(projectRef, 1);
      }
      assertRefUpdateFor(expectedRefUpdateCounts);
    }

    void assertRefUpdateFor(Map<String, Integer> expectedProjectRefUpdateCounts) {
      for (Map.Entry<String, Integer> e : expectedProjectRefUpdateCounts.entrySet()) {
        assertThat(getCount(e.getKey())).isEqualTo(e.getValue());
      }
      assertThat(countsByProjectRefs).hasSize(expectedProjectRefUpdateCounts.size());
      clear();
    }
  }
}
