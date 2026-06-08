package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.account.AccountProperties.ACCOUNT;
import static com.google.gerrit.server.account.AccountProperties.ACCOUNT_CONFIG;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static com.google.gerrit.truth.ConfigSubject.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AccountIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.server.Sequence;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdFactoryNoteDbImpl;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdsNoteDbImpl;
import com.google.gerrit.server.account.storage.notedb.AccountsUpdateNoteDbImpl;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.account.StalenessChecker;
import com.google.gerrit.server.project.RefPattern;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
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
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class AccountNoteDbIT extends AbstractAccountIT {
  @Inject private AccountIndexer accountIndexer;
  @Inject private StalenessChecker stalenessChecker;
  @Inject private ExternalIdNotes.Factory extIdNotesFactory;
  @Inject private ExternalIdsNoteDbImpl externalIdsNoteDbImpl;
  @Inject private GitReferenceUpdated gitReferenceUpdated;
  @Inject private ExternalIdFactoryNoteDbImpl externalIdFactoryNoteDbImpl;

  @Override
  protected ExternalIdFactory getExternalIdFactory() {
    return externalIdFactoryNoteDbImpl;
  }

  @Override
  protected ExternalIds getExternalIdsReader() {
    return externalIdsNoteDbImpl;
  }

  @Override
  protected AccountsUpdate getAccountsUpdateWithRunnables(
      Runnable afterReadRevision, Runnable beforeCommit, RetryHelper retryHelper) {
    return getAccountsUpdateNoteDbImplWithRunnables(afterReadRevision, beforeCommit, retryHelper);
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected Account.Id createByAccountCreator(int expectedAccountReindexCalls) throws Exception {
    AccountIndexedCounter accountIndexedCounter = getAccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      String name = "foo";
      TestAccount foo = accountCreator.create(name);
      AccountInfo info = gApi.accounts().id(foo.id().get()).get();
      if (server.isUsernameSupported()) {
        assertThat(info.username).isEqualTo(name);
      } else {
        assertThat(info.email).isEqualTo(foo.email());
      }
      assertThat(info.name).isEqualTo(name);
      accountIndexedCounter.assertReindexOf(foo, expectedAccountReindexCalls);
      assertUserBranch(foo.id(), name, null);
      return foo.id();
    }
  }

  @Test
  public void createByAccountCreator() throws Exception {
    RefUpdateCounter refUpdateCounter = createRefUpdateCounter();
    try (Registration registration = extensionRegistry.newRegistration().add(refUpdateCounter)) {
      Account.Id accountId = createByAccountCreator(1);
      refUpdateCounter.assertRefUpdateFor(
          RefUpdateCounter.projectRef(allUsers, RefNames.refsUsers(accountId)),
          RefUpdateCounter.projectRef(allUsers, RefNames.REFS_EXTERNAL_IDS),
          RefUpdateCounter.projectRef(allUsers, RefNames.REFS_SEQUENCES + Sequence.NAME_ACCOUNTS));
    }
  }

  @Test
  public void createAnonymousCowardByAccountCreator() throws Exception {
    AccountIndexedCounter accountIndexedCounter = getAccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      TestAccount anonymousCoward = accountCreator.create();
      accountIndexedCounter.assertReindexOf(anonymousCoward);
      assertUserBranchWithoutAccountConfig(anonymousCoward.id());
    }
  }

  @Test
  public void commitMessageOnAccountUpdates() throws Exception {
    AccountsUpdate au = accountsUpdateProvider.get();
    Account.Id accountId = Account.id(seq.nextAccountId());
    au.insert("Create Test Account", accountId, u -> {});
    assertLastCommitMessageOfUserBranch(accountId, "Create Test Account");

    au.update("Set Status", accountId, u -> u.setStatus("Foo"));
    assertLastCommitMessageOfUserBranch(accountId, "Set Status");
  }

  private void assertLastCommitMessageOfUserBranch(Account.Id accountId, String expectedMessage)
      throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      Ref exactRef = repo.exactRef(RefNames.refsUsers(accountId));
      assertThat(rw.parseCommit(exactRef.getObjectId()).getShortMessage())
          .isEqualTo(expectedMessage);
    }
  }

  @Test
  @UseClockStep
  public void createAtomically() throws Exception {
    Account.Id accountId = Account.id(seq.nextAccountId());
    String fullName = "Foo";
    ExternalId extId = getExternalIdFactory().createEmail(accountId, "foo@example.com");
    AccountState accountState =
        accountsUpdateProvider
            .get()
            .insert(
                "Create Account Atomically",
                accountId,
                u -> u.setFullName(fullName).addExternalId(extId));
    assertThat(accountState.account().fullName()).isEqualTo(fullName);

    AccountInfo info = gApi.accounts().id(accountId.get()).get();
    assertThat(info.name).isEqualTo(fullName);

    List<EmailInfo> emails = gApi.accounts().id(accountId.get()).getEmails();
    assertThat(emails.stream().map(e -> e.email).collect(toImmutableSet()))
        .containsExactly(extId.email());

    RevCommit commitUserBranch =
        projectOperations.project(allUsers).getHead(RefNames.refsUsers(accountId));
    RevCommit commitRefsMetaExternalIds =
        projectOperations.project(allUsers).getHead(RefNames.REFS_EXTERNAL_IDS);
    assertThat(commitUserBranch.getCommitTime())
        .isEqualTo(commitRefsMetaExternalIds.getCommitTime());
  }

  @Test
  public void updateAccountWithoutAccountConfigNoteDb() throws Exception {
    TestAccount anonymousCoward = accountCreator.create();
    assertUserBranchWithoutAccountConfig(anonymousCoward.id());

    String status = "OOO";
    Optional<AccountState> accountState =
        accountsUpdateProvider
            .get()
            .update("Set status", anonymousCoward.id(), u -> u.setStatus(status));
    assertThat(accountState).isPresent();
    Account account = accountState.get().account();
    assertThat(account.fullName()).isNull();
    assertThat(account.status()).isEqualTo(status);
    assertUserBranch(anonymousCoward.id(), null, status);
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
          Math.abs(c.getCommitTime() * 1000L - getAccount(accountId).registeredOn().toEpochMilli());
      assertThat(timestampDiffMs).isAtMost(Duration.ofSeconds(1).toMillis());

      // Check the 'account.config' file.
      try (TreeWalk tw = TreeWalk.forPath(or, ACCOUNT_CONFIG, c.getTree())) {
        if (name != null || status != null) {
          assertThat(tw).isNotNull();
          Config cfg = new Config();
          cfg.fromText(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8));
          assertThat(cfg)
              .stringValue(ACCOUNT, null, AccountProperties.KEY_FULL_NAME)
              .isEqualTo(name);
          assertThat(cfg)
              .stringValue(ACCOUNT, null, AccountProperties.KEY_STATUS)
              .isEqualTo(status);
        } else {
          // No account properties were set, hence an 'account.config' file was not created.
          assertThat(tw).isNull();
        }
      }
    }
  }

  @Test
  public void checkMetaIdAndUniqueTag() throws Exception {
    // In open-source Gerrit, the uniqueTag and metaId are always the same. Check them together
    // in this test.
    // metaId and uniqueTag are set when account is loaded
    assertThat(accounts.get(admin.id()).get().account().metaId()).isEqualTo(getMetaId(admin.id()));
    assertThat(accounts.get(admin.id()).get().account().uniqueTag())
        .isEqualTo(getMetaId(admin.id()));

    // metaId and uniqueTag are set when account is created
    AccountsUpdate au = accountsUpdateProvider.get();
    Account.Id accountId = Account.id(seq.nextAccountId());
    AccountState accountState = au.insert("Create Test Account", accountId, u -> {});
    assertThat(accountState.account().metaId()).isEqualTo(getMetaId(accountId));
    assertThat(accountState.account().uniqueTag()).isEqualTo(getMetaId(accountId));

    // metaId and uniqueTag are set when account is updated
    Optional<AccountState> updatedAccountState =
        au.update("Set Full Name", accountId, u -> u.setFullName("foo"));
    assertThat(updatedAccountState).isPresent();
    Account updatedAccount = updatedAccountState.get().account();
    assertThat(accountState.account().metaId()).isNotEqualTo(updatedAccount.metaId());
    assertThat(accountState.account().uniqueTag()).isNotEqualTo(updatedAccount.uniqueTag());
    assertThat(updatedAccount.metaId()).isEqualTo(getMetaId(accountId));
    assertThat(updatedAccount.uniqueTag()).isEqualTo(getMetaId(accountId));
  }

  @Nullable
  private String getMetaId(Account.Id accountId) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(RefNames.refsUsers(accountId));
      return ref != null ? ref.getObjectId().name() : null;
    }
  }

  @Test
  public void externalIdBatchUpdates() throws Exception {
    String extId1String = "foo:bar";
    String extId2String = "foo:baz";
    ExternalId extId1 =
        getExternalIdFactory()
            .createWithEmail(externalIdKeyFactory.parse(extId1String), admin.id(), "1@foo.com");
    ExternalId extId2 =
        getExternalIdFactory()
            .createWithEmail(externalIdKeyFactory.parse(extId2String), user.id(), "2@foo.com");

    int initialCommits = countExternalIdsCommits();
    AccountsUpdate.UpdateArguments ua1 =
        new AccountsUpdate.UpdateArguments(
            "Add External ID", admin.id(), u -> u.addExternalId(extId1));
    AccountsUpdate.UpdateArguments ua2 =
        new AccountsUpdate.UpdateArguments(
            "Add External ID", user.id(), u -> u.addExternalId(extId2));
    ImmutableList<Optional<AccountState>> accountStates =
        accountsUpdateProvider.get().updateBatch(ImmutableList.of(ua1, ua2));
    assertThat(accountStates).hasSize(2);
    assertThat(accountStates.get(0).get().externalIds()).contains(extId1);
    assertThat(accountStates.get(1).get().externalIds()).contains(extId2);
    assertThat(
            gApi.accounts().id(admin.id().get()).getExternalIds().stream()
                .map(e -> e.identity)
                .collect(toImmutableSet()))
        .contains(extId1String);
    assertThat(
            gApi.accounts().id(user.id().get()).getExternalIds().stream()
                .map(e -> e.identity)
                .collect(toImmutableSet()))
        .contains(extId2String);

    // Ensure that we only applied one single commit.
    int afterUpdateCommits = countExternalIdsCommits();
    assertThat(afterUpdateCommits).isEqualTo(initialCommits + 1);
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  protected int countExternalIdsCommits() throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        Git git = new Git(allUsersRepo)) {
      ObjectId refsMetaExternalIdsHead =
          allUsersRepo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId();
      return Iterables.size(git.log().add(refsMetaExternalIdsHead).call());
    }
  }

  @Test
  public void externalIdBatchUpdates_commitMsg_multipleAccounts() throws Exception {
    ExternalId extId1 =
        getExternalIdFactory()
            .createWithEmail(externalIdKeyFactory.parse("foo:bar"), admin.id(), "1@foo.com");
    ExternalId extId2 =
        getExternalIdFactory()
            .createWithEmail(externalIdKeyFactory.parse("foo:baz"), user.id(), "2@foo.com");

    AccountsUpdate.UpdateArguments ua1 =
        new AccountsUpdate.UpdateArguments(
            "first message", admin.id(), u -> u.addExternalId(extId1));
    AccountsUpdate.UpdateArguments ua2 =
        new AccountsUpdate.UpdateArguments(
            "second message", user.id(), u -> u.addExternalId(extId2));
    accountsUpdateProvider.get().updateBatch(ImmutableList.of(ua1, ua2));

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(allUsersRepo)) {
      RevCommit commit =
          rw.parseCommit(allUsersRepo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId());

      assertThat(commit.getFullMessage()).isEqualTo("Batch update for 2 accounts\n");
    }
  }

  @Test
  public void externalIdBatchUpdates_commitMsg_singleAccount() throws Exception {
    ExternalId extId =
        getExternalIdFactory()
            .createWithEmail(externalIdKeyFactory.parse("foo:bar"), admin.id(), "1@foo.com");

    accountsUpdateProvider.get().update("foobar", admin.id(), u -> u.addExternalId(extId));

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(allUsersRepo)) {
      RevCommit commit =
          rw.parseCommit(allUsersRepo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId());

      assertThat(commit.getFullMessage()).isEqualTo("foobar\n");
    }
  }

  @Test
  public void getAccountFromMetaId() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());
    gApi.accounts().self().setStatus("New status");

    AccountState postUpdateStatus = accountCache.get(admin.id()).get();
    assertThat(postUpdateStatus).isNotEqualTo(preUpdateState);
    assertThat(
            accountCache.getFromMetaId(
                admin.id(), ObjectId.fromString(preUpdateState.account().metaId())))
        .isEqualTo(preUpdateState);
    assertThat(
            accountCache.getFromMetaId(
                admin.id(), ObjectId.fromString(postUpdateStatus.account().metaId())))
        .isEqualTo(postUpdateStatus);
  }

  @Test
  public void projectWatchesUpdate_refsUsersUpdated() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());

    ProjectWatchInfo projectWatchInfo = new ProjectWatchInfo();
    projectWatchInfo.project = project.get();
    projectWatchInfo.notifyAllComments = true;
    gApi.accounts().self().setWatchedProjects(ImmutableList.of(projectWatchInfo));

    AccountState updatedState1 = accountCache.get(admin.id()).get();
    assertThat(preUpdateState.account().metaId()).isNotEqualTo(updatedState1.account().metaId());

    gApi.accounts().self().deleteWatchedProjects(ImmutableList.of(projectWatchInfo));

    AccountState updatedState2 = accountCache.get(admin.id()).get();
    assertThat(updatedState1.account().metaId()).isNotEqualTo(updatedState2.account().metaId());
  }

  @Test
  public void updateExternalId_externalIdApiUpdate_refsUsersUpdated() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());

    gApi.accounts().self().addEmail(newEmailInput("secondary@non.google"));
    assertExternalIds(
        admin.id(),
        ImmutableSet.of(
            "mailto:admin@example.com", "username:admin", "mailto:secondary@non.google"));

    AccountState updatedState1 = accountCache.get(admin.id()).get();
    assertThat(preUpdateState.account().metaId()).isNotEqualTo(updatedState1.account().metaId());

    gApi.accounts().self().deleteExternalIds(ImmutableList.of("mailto:secondary@non.google"));

    AccountState updatedState2 = accountCache.get(admin.id()).get();
    assertThat(updatedState1.account().metaId()).isNotEqualTo(updatedState2.account().metaId());
  }

  @Test
  public void addExternalId_accountUpdate_refsUsersUpdated() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());

    ExternalId externalId = getExternalIdFactory().create("custom", "value", admin.id());
    accountsUpdateProvider
        .get()
        .update("Add External ID", admin.id(), u -> u.addExternalId(externalId));
    assertExternalIds(
        admin.id(), ImmutableSet.of("mailto:admin@example.com", "username:admin", "custom:value"));

    AccountState updatedState = accountCache.get(admin.id()).get();
    assertThat(preUpdateState.account().metaId()).isNotEqualTo(updatedState.account().metaId());
  }

  @Test
  public void deleteExternalId_accountUpdate_refsUsersUpdated() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());

    ExternalId externalId = createEmailExternalId(admin.id(), "admin@example.com");
    accountsUpdateProvider
        .get()
        .update("Remove External ID", admin.id(), u -> u.deleteExternalId(externalId));
    assertExternalIds(admin.id(), ImmutableSet.of("username:admin"));

    AccountState updatedState = accountCache.get(admin.id()).get();
    assertThat(preUpdateState.account().metaId()).isNotEqualTo(updatedState.account().metaId());
  }

  @Test
  public void updateExternalId_accountUpdate_refsUsersUpdated() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());

    ExternalId externalId =
        getExternalIdFactory()
            .createWithEmail(
                SCHEME_MAILTO, "secondary@non.google", admin.id(), "secondary@non.google");
    accountsUpdateProvider
        .get()
        .update("Update External ID", admin.id(), u -> u.updateExternalId(externalId));
    assertExternalIds(
        admin.id(),
        ImmutableSet.of(
            "mailto:admin@example.com", "username:admin", "mailto:secondary@non.google"));

    AccountState updatedState = accountCache.get(admin.id()).get();
    assertThat(preUpdateState.account().metaId()).isNotEqualTo(updatedState.account().metaId());
  }

  @Test
  public void replaceExternalId_accountUpdate_refsUsersUpdated() throws Exception {
    AccountState preUpdateState = accountCache.get(admin.id()).get();
    requestScopeOperations.setApiUser(admin.id());

    ExternalId externalId =
        getExternalIdFactory()
            .createWithEmail(
                SCHEME_MAILTO, "secondary@non.google", admin.id(), "secondary@non.google");
    ExternalId oldExternalId =
        getExternalIdsReader().get(createEmailExternalId(admin.id(), admin.email()).key()).get();
    accountsUpdateProvider
        .get()
        .update(
            "Replace External ID", admin.id(), u -> u.replaceExternalId(oldExternalId, externalId));
    assertExternalIds(admin.id(), ImmutableSet.of("mailto:secondary@non.google", "username:admin"));

    AccountState updatedState = accountCache.get(admin.id()).get();
    assertThat(accountCache.get(admin.id()).get()).isNotSameInstanceAs(preUpdateState);
    if (preUpdateState.account().metaId() == null) {
      // When the test is executed on google infrastructure, metaId should be either always set
      // or always be null.
      assertThat(updatedState.account().metaId()).isNull();
    } else {
      assertThat(preUpdateState.account().metaId()).isNotEqualTo(updatedState.account().metaId());
    }
  }

  @Test
  public void accountUpdate_updateBatch_allUsersExternalIdsUpdated_refsUsersUpdated()
      throws Exception {
    AccountState preUpdateAdminState = accountCache.get(admin.id()).get();
    AccountState preUpdateUserState = accountCache.get(user.id()).get();

    requestScopeOperations.setApiUser(admin.id());
    ExternalId extId1 =
        getExternalIdFactory()
            .createWithEmail("custom", "admin-id", admin.id(), "admin-id@test.com");

    ExternalId extId2 =
        getExternalIdFactory().createWithEmail("custom", "user-id", user.id(), "user-id@test.com");

    AccountsUpdate.UpdateArguments ua1 =
        new AccountsUpdate.UpdateArguments(
            "Add External ID", admin.id(), u -> u.addExternalId(extId1));
    AccountsUpdate.UpdateArguments ua2 =
        new AccountsUpdate.UpdateArguments(
            "Add External ID", user.id(), u -> u.addExternalId(extId2));
    AccountIndexedCounter accountIndexedCounter = getAccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      accountsUpdateProvider.get().updateBatch(ImmutableList.of(ua1, ua2));
    }
    accountIndexedCounter.assertReindexOf(admin.id(), 1);
    accountIndexedCounter.assertReindexOf(user.id(), 1);

    assertExternalIds(
        admin.id(),
        ImmutableSet.of("mailto:admin@example.com", "username:admin", "custom:admin-id"));
    assertExternalIds(
        user.id(), ImmutableSet.of("username:user1", "mailto:user1@example.com", "custom:user-id"));
    // Assert reindexing has worked on the updated accounts.
    assertThat(
            Iterables.getOnlyElement(gApi.accounts().query("admin-id@test.com").get())._accountId)
        .isEqualTo(admin.id().get());
    assertThat(Iterables.getOnlyElement(gApi.accounts().query("user-id@test.com").get())._accountId)
        .isEqualTo(user.id().get());
    AccountState updatedAdminState = accountCache.get(admin.id()).get();
    AccountState updatedUserState = accountCache.get(user.id()).get();
    assertThat(preUpdateAdminState.account().metaId())
        .isNotEqualTo(updatedAdminState.account().metaId());
    assertThat(preUpdateUserState.account().metaId())
        .isNotEqualTo(updatedUserState.account().metaId());
  }

  @Test
  public void accountUpdate_updateBatch_someUsersExternalIdsUpdated_refsUsersUpdated()
      throws Exception {
    AccountState preUpdateAdminState = accountCache.get(admin.id()).get();
    AccountState preUpdateUserState = accountCache.get(user.id()).get();

    requestScopeOperations.setApiUser(admin.id());
    AccountsUpdate.UpdateArguments ua1 =
        new AccountsUpdate.UpdateArguments(
            "Update Display Name", admin.id(), u -> u.setDisplayName("DN"));
    AccountsUpdate.UpdateArguments ua2 =
        new AccountsUpdate.UpdateArguments(
            "Remove external Id",
            user.id(),
            u -> u.deleteExternalId(createEmailExternalId(user.id(), user.email())));
    AccountIndexedCounter accountIndexedCounter = getAccountIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(accountIndexedCounter)) {
      accountsUpdateProvider.get().updateBatch(ImmutableList.of(ua1, ua2));
    }
    accountIndexedCounter.assertReindexOf(admin.id(), 1);
    accountIndexedCounter.assertReindexOf(user.id(), 1);

    // Only the version in config of the user with external id update was updated.
    AccountState updatedAdminState = accountCache.get(admin.id()).get();
    AccountState updatedUserState = accountCache.get(user.id()).get();
    assertThat(preUpdateAdminState.account().metaId())
        .isNotEqualTo(updatedAdminState.account().metaId());
    assertThat(preUpdateUserState.account().metaId())
        .isNotEqualTo(updatedUserState.account().metaId());
  }

  @Test
  public void deleteUserBranchWithAccessDatabaseCapability() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(
            allow(Permission.DELETE)
                .ref(RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}")
                .group(REGISTERED_USERS)
                .force(true))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    String userRef = RefNames.refsUsers(admin.id());
    PushResult r = deleteRef(allUsersRepo, userRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(userRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNull();
    }

    assertThat(accountCache.get(admin.id())).isEmpty();
    assertThat(accountQueryProvider.get().byDefault(admin.id().toString(), true)).isEmpty();
  }

  @Test
  public void cannotDeleteUserBranch() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(
            allow(Permission.DELETE)
                .ref(RefNames.REFS_USERS + "${" + RefPattern.USERID_SHARDED + "}")
                .group(REGISTERED_USERS)
                .force(true))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    String userRef = RefNames.refsUsers(admin.id());
    PushResult r = deleteRef(allUsersRepo, userRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(userRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(refUpdate.getMessage()).contains("Not allowed to delete user branch.");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(userRef)).isNotNull();
    }
  }

  @Test
  public void createDefaultUserBranch() throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(RefNames.REFS_USERS_DEFAULT)).isNull();
    }

    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS_DEFAULT).group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS_DEFAULT).group(adminGroupUuid()))
        .update();

    TestRepository<InMemoryRepository> allUsersRepo = cloneProject(allUsers);
    pushFactory
        .create(admin.newIdent(), allUsersRepo)
        .to(RefNames.REFS_USERS_DEFAULT)
        .assertOkStatus();

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(RefNames.REFS_USERS_DEFAULT)).isNotNull();
    }
  }

  @Test
  public void stalenessChecker() throws Exception {
    // Newly created account is not stale.
    AccountInfo accountInfo = gApi.accounts().create(name("foo")).get();
    Account.Id accountId = Account.id(accountInfo._accountId);
    assertThat(stalenessChecker.check(accountId).isStale()).isFalse();

    // Manually updating the user ref makes the index document stale.
    String userRef = RefNames.refsUsers(accountId);
    testRefAction(
        () -> {
          try (Repository repo = repoManager.openRepository(allUsers);
              ObjectInserter oi = repo.newObjectInserter();
              RevWalk rw = new RevWalk(repo)) {
            RevCommit commit = rw.parseCommit(repo.exactRef(userRef).getObjectId());

            PersonIdent ident = new PersonIdent(serverIdent.get(), TimeUtil.now());
            CommitBuilder cb = new CommitBuilder();
            cb.setTreeId(commit.getTree());
            cb.setCommitter(ident);
            cb.setAuthor(ident);
            cb.setMessage(commit.getFullMessage());
            ObjectId emptyCommit = oi.insert(cb);
            oi.flush();

            RefUpdate updateRef = repo.updateRef(userRef);
            updateRef.setExpectedOldObjectId(commit.toObjectId());
            updateRef.setNewObjectId(emptyCommit);
            assertThat(updateRef.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);
          }
        });
    assertStaleAccountAndReindex(accountId);

    // Manually inserting/updating/deleting an external ID of the user makes the index document
    // stale.
    try (Repository repo = repoManager.openRepository(allUsers)) {
      testRefAction(
          () -> {
            ExternalIdNotes extIdNotes = getExternalIdNotes(repo);

            ExternalId.Key key = externalIdKeyFactory.create("foo", "foo");
            extIdNotes.insert(getExternalIdFactory().create(key, accountId));
            try (MetaDataUpdate update = metaDataUpdateFactory.create(allUsers)) {
              extIdNotes.commit(update);
            }
            assertStaleAccountAndReindex(accountId);

            extIdNotes = getExternalIdNotes(repo);
            extIdNotes.upsert(
                getExternalIdFactory().createWithEmail(key, accountId, "foo@example.com"));
            try (MetaDataUpdate update = metaDataUpdateFactory.create(allUsers)) {
              extIdNotes.commit(update);
            }
            assertStaleAccountAndReindex(accountId);

            extIdNotes = getExternalIdNotes(repo);
            extIdNotes.delete(accountId, key);
            try (MetaDataUpdate update = metaDataUpdateFactory.create(allUsers)) {
              extIdNotes.commit(update);
            }
          });
      assertStaleAccountAndReindex(accountId);
    }

    // Manually delete account
    testRefAction(
        () -> {
          try (Repository repo = repoManager.openRepository(allUsers);
              RevWalk rw = new RevWalk(repo)) {
            RevCommit commit = rw.parseCommit(repo.exactRef(userRef).getObjectId());
            RefUpdate updateRef = repo.updateRef(userRef);
            updateRef.setExpectedOldObjectId(commit.toObjectId());
            updateRef.setNewObjectId(ObjectId.zeroId());
            updateRef.setForceUpdate(true);
            assertThat(updateRef.delete()).isEqualTo(RefUpdate.Result.FORCED);
          }
        });
    assertStaleAccountAndReindex(accountId);
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

  private void assertStaleAccountAndReindex(Account.Id accountId) throws IOException {
    assertThat(stalenessChecker.check(accountId).isStale()).isTrue();

    // Reindex fixes staleness
    accountIndexer.index(accountId);
    assertThat(stalenessChecker.check(accountId).isStale()).isFalse();
  }
}
