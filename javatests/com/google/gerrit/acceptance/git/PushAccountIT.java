// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.ProjectWatches;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.EnumSet;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

/** Tests account behavior when users push to accounts refs. */
public class PushAccountIT extends AbstractDaemonTest {

  @ConfigSuite.Default
  public static Config enableSignedPushConfig() {
    return defaultConfig();
  }

  @ConfigSuite.Config
  public static Config disableInMemoryRefCache() {
    // Run these tests for both enabled and disabled in-memory ref caches. This is an implementation
    // detail of ReceiveCommits that makes the logic either base it's computation on previously
    // advertised refs or a make it query a ref database.
    Config cfg = defaultConfig();
    cfg.setBoolean("receive", null, "enableInMemoryRefCache", false);
    return cfg;
  }

  private static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("receive", null, "enableSignedPush", true);

    // Disable the staleness checker so that tests that verify the number of expected index events
    // are stable.
    cfg.setBoolean("index", null, "autoReindexIfStale", false);

    return cfg;
  }

  @Inject private @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Sequences seq;

  @Test
  public void pushToUserBranch() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, RefNames.refsUsers(admin.id()) + ":userRef");
      allUsersRepo.reset("userRef");
      PushOneCommit push = pushFactory.create(admin.newIdent(), allUsersRepo);
      push.to(RefNames.refsUsers(admin.id())).assertOkStatus();
      accountIndexedCounter.assertReindexOf(admin);

      push = pushFactory.create(admin.newIdent(), allUsersRepo);
      push.to(RefNames.REFS_USERS_SELF).assertOkStatus();
      accountIndexedCounter.assertReindexOf(admin);
    }
  }

  @Test
  public void pushToUserBranchForReview() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      String userRefName = RefNames.refsUsers(admin.id());
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRefName + ":userRef");
      allUsersRepo.reset("userRef");
      PushOneCommit push = pushFactory.create(admin.newIdent(), allUsersRepo);
      PushOneCommit.Result r = push.to(MagicBranch.NEW_CHANGE + userRefName);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRefName);
      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(r.getChangeId()).current().submit();
      accountIndexedCounter.assertReindexOf(admin);

      push = pushFactory.create(admin.newIdent(), allUsersRepo);
      r = push.to(MagicBranch.NEW_CHANGE + RefNames.REFS_USERS_SELF);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRefName);
      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(r.getChangeId()).current().submit();
      accountIndexedCounter.assertReindexOf(admin);
    }
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewAndSubmit() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      String userRef = RefNames.refsUsers(admin.id());
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_STATUS, "out-of-office");

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(MagicBranch.NEW_CHANGE + userRef);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRef);

      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(r.getChangeId()).current().submit();
      accountIndexedCounter.assertReindexOf(admin);

      AccountInfo info = gApi.accounts().self().get();
      assertThat(info.email).isEqualTo(admin.email());
      assertThat(info.name).isEqualTo(admin.fullName());
      assertThat(info.status).isEqualTo("out-of-office");
    }
  }

  @Test
  public void pushAccountConfigWithPrefEmailThatDoesNotExistAsExtIdToUserBranchForReviewAndSubmit()
      throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestAccount foo =
          accountCreator.create(name("foo"), name("foo") + "@example.com", "Foo", null);
      String userRef = RefNames.refsUsers(foo.id());
      accountIndexedCounter.clear();

      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, foo);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      String email = "some.email@example.com";
      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_PREFERRED_EMAIL, email);

      PushOneCommit.Result r =
          pushFactory
              .create(
                  foo.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(MagicBranch.NEW_CHANGE + userRef);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRef);

      requestScopeOperations.setApiUser(foo.id());
      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(r.getChangeId()).current().submit();

      accountIndexedCounter.assertReindexOf(foo);

      AccountInfo info = gApi.accounts().self().get();
      assertThat(info.email).isEqualTo(email);
      assertThat(info.name).isEqualTo(foo.fullName());
    }
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewIsRejectedOnSubmitIfConfigIsInvalid()
      throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      String userRef = RefNames.refsUsers(admin.id());
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  "invalid config")
              .to(MagicBranch.NEW_CHANGE + userRef);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRef);

      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.changes().id(r.getChangeId()).current().submit());
      assertThat(thrown)
          .hasMessageThat()
          .contains(
              String.format(
                  "invalid account configuration: commit '%s' has an invalid '%s' file for account"
                      + " '%s': Invalid config file %s in project %s in branch %s in commit %s",
                  r.getCommit().name(),
                  AccountProperties.ACCOUNT_CONFIG,
                  admin.id(),
                  AccountProperties.ACCOUNT_CONFIG,
                  allUsers.get(),
                  userRef,
                  r.getCommit().name()));
    }
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewIsRejectedOnSubmitIfPreferredEmailIsInvalid()
      throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      String userRef = RefNames.refsUsers(admin.id());
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      String noEmail = "no.email";
      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_PREFERRED_EMAIL, noEmail);

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(MagicBranch.NEW_CHANGE + userRef);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRef);

      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.changes().id(r.getChangeId()).current().submit());
      assertThat(thrown)
          .hasMessageThat()
          .contains(
              String.format(
                  "invalid account configuration: invalid preferred email '%s' for account '%s'",
                  noEmail, admin.id()));
    }
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewIsRejectedOnSubmitIfOwnAccountIsDeactivated()
      throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      String userRef = RefNames.refsUsers(admin.id());
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      Config ac = getAccountConfig(allUsersRepo);
      ac.setBoolean(AccountProperties.ACCOUNT, null, AccountProperties.KEY_ACTIVE, false);

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(MagicBranch.NEW_CHANGE + userRef);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRef);

      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      ResourceConflictException thrown =
          assertThrows(
              ResourceConflictException.class,
              () -> gApi.changes().id(r.getChangeId()).current().submit());
      assertThat(thrown)
          .hasMessageThat()
          .contains("invalid account configuration: cannot deactivate own account");
    }
  }

  @Test
  public void pushAccountConfigToUserBranchForReviewDeactivateOtherAccount() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      projectOperations
          .allProjectsForUpdate()
          .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
          .update();

      TestAccount foo = accountCreator.create(name("foo"));
      assertThat(gApi.accounts().id(foo.id().get()).getActive()).isTrue();
      String userRef = RefNames.refsUsers(foo.id());
      accountIndexedCounter.clear();

      projectOperations
          .project(allUsers)
          .forUpdate()
          .add(allow(Permission.PUSH).ref(userRef).group(adminGroupUuid()))
          .add(allowLabel("Code-Review").ref(userRef).group(adminGroupUuid()).range(-2, 2))
          .add(allow(Permission.SUBMIT).ref(userRef).group(adminGroupUuid()))
          .update();

      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      Config ac = getAccountConfig(allUsersRepo);
      ac.setBoolean(AccountProperties.ACCOUNT, null, AccountProperties.KEY_ACTIVE, false);

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(MagicBranch.NEW_CHANGE + userRef);
      r.assertOkStatus();
      accountIndexedCounter.assertNoReindex();
      assertThat(r.getChange().change().getDest().branch()).isEqualTo(userRef);

      gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
      gApi.changes().id(r.getChangeId()).current().submit();
      accountIndexedCounter.assertReindexOf(foo);

      assertThat(gApi.accounts().id(foo.id().get()).getActive()).isFalse();
    }
  }

  @Test
  public void pushWatchConfigToUserBranch() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, RefNames.refsUsers(admin.id()) + ":userRef");
      allUsersRepo.reset("userRef");

      Config wc = new Config();
      wc.setString(
          ProjectWatches.PROJECT,
          project.get(),
          ProjectWatches.KEY_NOTIFY,
          ProjectWatches.NotifyValue.create(null, EnumSet.of(NotifyType.ALL_COMMENTS)).toString());
      PushOneCommit push =
          pushFactory.create(
              admin.newIdent(),
              allUsersRepo,
              "Add project watch",
              ProjectWatches.WATCH_CONFIG,
              wc.toText());
      push.to(RefNames.REFS_USERS_SELF).assertOkStatus();
      accountIndexedCounter.assertReindexOf(admin);

      String invalidNotifyValue = "]invalid[";
      wc.setString(
          ProjectWatches.PROJECT, project.get(), ProjectWatches.KEY_NOTIFY, invalidNotifyValue);
      push =
          pushFactory.create(
              admin.newIdent(),
              allUsersRepo,
              "Add invalid project watch",
              ProjectWatches.WATCH_CONFIG,
              wc.toText());
      PushOneCommit.Result r = push.to(RefNames.REFS_USERS_SELF);
      r.assertErrorStatus("invalid account configuration");
      r.assertMessage(
          String.format(
              "%s: Invalid project watch of account %d for project %s: %s",
              ProjectWatches.WATCH_CONFIG, admin.id().get(), project.get(), invalidNotifyValue));
    }
  }

  @Test
  public void pushAccountConfigToUserBranch() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestAccount oooUser = accountCreator.create("away", "away@mail.invalid", "Ambrose Way", null);
      requestScopeOperations.setApiUser(oooUser.id());

      // Must clone as oooUser to ensure the push is allowed.
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, oooUser);
      fetch(allUsersRepo, RefNames.refsUsers(oooUser.id()) + ":userRef");
      allUsersRepo.reset("userRef");

      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_STATUS, "out-of-office");

      accountIndexedCounter.clear();
      pushFactory
          .create(
              oooUser.newIdent(),
              allUsersRepo,
              "Update account config",
              AccountProperties.ACCOUNT_CONFIG,
              ac.toText())
          .to(RefNames.refsUsers(oooUser.id()))
          .assertOkStatus();

      accountIndexedCounter.assertReindexOf(oooUser);

      AccountInfo info = gApi.accounts().self().get();
      assertThat(info.email).isEqualTo(oooUser.email());
      assertThat(info.name).isEqualTo(oooUser.fullName());
      assertThat(info.status).isEqualTo("out-of-office");
    }
  }

  @Test
  public void pushAccountConfigToUserBranchIsRejectedIfConfigIsInvalid() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      String userRef = RefNames.refsUsers(admin.id());
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  "invalid config")
              .to(RefNames.REFS_USERS_SELF);
      r.assertErrorStatus("invalid account configuration");
      r.assertMessage(
          String.format(
              "commit '%s' has an invalid '%s' file for account '%s':"
                  + " Invalid config file %s in project %s in branch %s in commit %s",
              r.getCommit().name(),
              AccountProperties.ACCOUNT_CONFIG,
              admin.id(),
              AccountProperties.ACCOUNT_CONFIG,
              allUsers.get(),
              userRef,
              r.getCommit().name()));
      accountIndexedCounter.assertNoReindex();
    }
  }

  @Test
  public void pushAccountConfigToUserBranchIsRejectedIfPreferredEmailIsInvalid() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, RefNames.refsUsers(admin.id()) + ":userRef");
      allUsersRepo.reset("userRef");

      String noEmail = "no.email";
      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_PREFERRED_EMAIL, noEmail);

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(RefNames.REFS_USERS_SELF);
      r.assertErrorStatus("invalid account configuration");
      r.assertMessage(
          String.format("invalid preferred email '%s' for account '%s'", noEmail, admin.id()));
      accountIndexedCounter.assertNoReindex();
    }
  }

  @Test
  public void pushAccountConfigToUserBranchInvalidPreferredEmailButNotChanged() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestAccount foo =
          accountCreator.create(name("foo"), name("foo") + "@example.com", "Foo", null);
      String userRef = RefNames.refsUsers(foo.id());

      String noEmail = "no.email";
      accountsUpdateProvider
          .get()
          .update("Set Preferred Email", foo.id(), u -> u.setPreferredEmail(noEmail));
      accountIndexedCounter.clear();

      projectOperations
          .project(allUsers)
          .forUpdate()
          .add(allow(Permission.PUSH).ref(userRef).group(REGISTERED_USERS))
          .update();
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, foo);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      String status = "in vacation";
      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_STATUS, status);

      pushFactory
          .create(
              foo.newIdent(),
              allUsersRepo,
              "Update account config",
              AccountProperties.ACCOUNT_CONFIG,
              ac.toText())
          .to(userRef)
          .assertOkStatus();
      accountIndexedCounter.assertReindexOf(foo);

      AccountInfo info = gApi.accounts().id(foo.id().get()).get();
      assertThat(info.email).isEqualTo(noEmail);
      assertThat(info.name).isEqualTo(foo.fullName());
      assertThat(info.status).isEqualTo(status);
    }
  }

  @Test
  public void pushAccountConfigToUserBranchIfPreferredEmailDoesNotExistAsExtId() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestAccount foo =
          accountCreator.create(name("foo"), name("foo") + "@example.com", "Foo", null);
      String userRef = RefNames.refsUsers(foo.id());
      accountIndexedCounter.clear();

      projectOperations
          .project(allUsers)
          .forUpdate()
          .add(allow(Permission.PUSH).ref(userRef).group(adminGroupUuid()))
          .update();

      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers, foo);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      String email = "some.email@example.com";
      Config ac = getAccountConfig(allUsersRepo);
      ac.setString(AccountProperties.ACCOUNT, null, AccountProperties.KEY_PREFERRED_EMAIL, email);

      pushFactory
          .create(
              foo.newIdent(),
              allUsersRepo,
              "Update account config",
              AccountProperties.ACCOUNT_CONFIG,
              ac.toText())
          .to(userRef)
          .assertOkStatus();
      accountIndexedCounter.assertReindexOf(foo);

      AccountInfo info = gApi.accounts().id(foo.id().get()).get();
      assertThat(info.email).isEqualTo(email);
      assertThat(info.name).isEqualTo(foo.fullName());
    }
  }

  @Test
  public void pushAccountConfigToUserBranchIsRejectedIfOwnAccountIsDeactivated() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, RefNames.refsUsers(admin.id()) + ":userRef");
      allUsersRepo.reset("userRef");

      Config ac = getAccountConfig(allUsersRepo);
      ac.setBoolean(AccountProperties.ACCOUNT, null, AccountProperties.KEY_ACTIVE, false);

      PushOneCommit.Result r =
          pushFactory
              .create(
                  admin.newIdent(),
                  allUsersRepo,
                  "Update account config",
                  AccountProperties.ACCOUNT_CONFIG,
                  ac.toText())
              .to(RefNames.REFS_USERS_SELF);
      r.assertErrorStatus("invalid account configuration");
      r.assertMessage("cannot deactivate own account");
      accountIndexedCounter.assertNoReindex();
    }
  }

  @Test
  public void pushAccountConfigToUserBranchDeactivateOtherAccount() throws Exception {
    AccountIndexedCounter accountIndexedCounter = new AccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      projectOperations
          .allProjectsForUpdate()
          .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
          .update();

      TestAccount foo = accountCreator.create(name("foo"));
      assertThat(gApi.accounts().id(foo.id().get()).getActive()).isTrue();
      String userRef = RefNames.refsUsers(foo.id());
      accountIndexedCounter.clear();

      projectOperations
          .project(allUsers)
          .forUpdate()
          .add(allow(Permission.PUSH).ref(userRef).group(adminGroupUuid()))
          .update();

      TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
      fetch(allUsersRepo, userRef + ":userRef");
      allUsersRepo.reset("userRef");

      Config ac = getAccountConfig(allUsersRepo);
      ac.setBoolean(AccountProperties.ACCOUNT, null, AccountProperties.KEY_ACTIVE, false);

      pushFactory
          .create(
              admin.newIdent(),
              allUsersRepo,
              "Update account config",
              AccountProperties.ACCOUNT_CONFIG,
              ac.toText())
          .to(userRef)
          .assertOkStatus();
      accountIndexedCounter.assertReindexOf(foo);

      assertThat(gApi.accounts().id(foo.id().get()).getActive()).isFalse();
    }
  }

  @Test
  public void cannotCreateNonUserBranchUnderRefsUsersWithAccessDatabaseCapability()
      throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS + "*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS + "*").group(adminGroupUuid()))
        .update();

    String userRef = RefNames.REFS_USERS + "foo";
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    PushOneCommit.Result r = pushFactory.create(admin.newIdent(), allUsersRepo).to(userRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create non-user branch under refs/users/.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNull();
    }
  }

  @Test
  public void cannotCreateUserBranch() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS + "*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS + "*").group(adminGroupUuid()))
        .update();

    String userRef = RefNames.refsUsers(Account.id(seq.nextAccountId()));
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    PushOneCommit.Result r = pushFactory.create(admin.newIdent(), allUsersRepo).to(userRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create user branch.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNull();
    }
  }

  @Test
  public void createUserBranchWithAccessDatabaseCapability() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS + "*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS + "*").group(adminGroupUuid()))
        .update();

    String userRef = RefNames.refsUsers(Account.id(seq.nextAccountId()));
    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    pushFactory.create(admin.newIdent(), allUsersRepo).to(userRef).assertOkStatus();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNotNull();
    }
  }

  private Config getAccountConfig(TestRepository<?> allUsersRepo) throws Exception {
    Config ac = new Config();
    try (TreeWalk tw =
        TreeWalk.forPath(
            allUsersRepo.getRepository(),
            AccountProperties.ACCOUNT_CONFIG,
            getHead(allUsersRepo.getRepository(), "HEAD").getTree())) {
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
}
