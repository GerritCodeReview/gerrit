// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CachedPreferences;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

/**
 * Reads/writes account data from/to a user branch in the {@code All-Users} repository.
 *
 * <p>This is the low-level API for account creation and account updates. Most callers should use
 * {@link AccountsUpdate} for creating and updating accounts.
 *
 * <p>This class can read/write account properties, preferences (general, diff and edit preferences)
 * and project watches.
 *
 * <p>The following files are read/written:
 *
 * <ul>
 *   <li>'account.config': Contains the account properties. Parsing and writing it is delegated to
 *       {@link AccountProperties}.
 *   <li>'preferences.config': Contains the preferences. Parsing and writing it is delegated to
 *       {@link StoredPreferences}.
 *   <li>'account.config': Contains the project watches. Parsing and writing it is delegated to
 *       {@link ProjectWatches}.
 * </ul>
 *
 * <p>The commit date of the first commit on the user branch is used as registration date of the
 * account. The first commit may be an empty commit (since all config files are optional).
 */
public class AccountConfig extends VersionedMetaData implements ValidationError.Sink {
  private final Account.Id accountId;
  private final AllUsersName allUsersName;
  private final Repository repo;
  private final String ref;

  private Optional<AccountProperties> loadedAccountProperties;
  private Optional<ObjectId> externalIdsRev;
  private ProjectWatches projectWatches;
  private StoredPreferences preferences;
  private Optional<AccountDelta> accountDelta = Optional.empty();
  private List<ValidationError> validationErrors;

  public AccountConfig(Account.Id accountId, AllUsersName allUsersName, Repository allUsersRepo) {
    this.accountId = requireNonNull(accountId, "accountId");
    this.allUsersName = requireNonNull(allUsersName, "allUsersName");
    this.repo = requireNonNull(allUsersRepo, "allUsersRepo");
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public AccountConfig load() throws IOException, ConfigInvalidException {
    load(allUsersName, repo);
    return this;
  }

  public AccountConfig load(ObjectId rev) throws IOException, ConfigInvalidException {
    load(allUsersName, repo, rev);
    return this;
  }

  /**
   * Get the loaded account.
   *
   * @return the loaded account, {@link Optional#empty()} if load didn't find the account because it
   *     doesn't exist
   * @throws IllegalStateException if the account was not loaded yet
   */
  public Optional<Account> getLoadedAccount() {
    checkLoaded();
    return loadedAccountProperties.map(AccountProperties::getAccount);
  }

  /**
   * Returns the revision of the {@code refs/meta/external-ids} branch.
   *
   * <p>This revision can be used to load the external IDs of the loaded account lazily via {@link
   * ExternalIds#byAccount(com.google.gerrit.entities.Account.Id, ObjectId)}.
   *
   * @return revision of the {@code refs/meta/external-ids} branch, {@link Optional#empty()} if no
   *     {@code refs/meta/external-ids} branch exists
   */
  public Optional<ObjectId> getExternalIdsRev() {
    checkLoaded();
    return externalIdsRev;
  }

  /**
   * Get the project watches of the loaded account.
   *
   * @return the project watches of the loaded account
   */
  public ImmutableMap<ProjectWatchKey, ImmutableSet<NotifyType>> getProjectWatches() {
    checkLoaded();
    return projectWatches.getProjectWatches();
  }

  /**
   * Sets the account. This means the loaded account will be overwritten with the given account.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param account account that should be set
   * @throws IllegalStateException if the account was not loaded yet
   */
  public AccountConfig setAccount(Account account) {
    checkLoaded();
    this.loadedAccountProperties =
        Optional.of(
            new AccountProperties(account.id(), account.registeredOn(), new Config(), null));
    this.accountDelta =
        Optional.of(
            AccountDelta.builder()
                .setActive(account.isActive())
                .setFullName(account.fullName())
                .setDisplayName(account.displayName())
                .setPreferredEmail(account.preferredEmail())
                .setStatus(account.status())
                .build());
    return this;
  }

  /**
   * Creates a new account.
   *
   * @return the new account
   * @throws DuplicateKeyException if the user branch already exists
   */
  public Account getNewAccount() throws DuplicateKeyException {
    return getNewAccount(TimeUtil.now());
  }

  /**
   * Creates a new account.
   *
   * @return the new account
   * @throws DuplicateKeyException if the user branch already exists
   */
  Account getNewAccount(Instant registeredOn) throws DuplicateKeyException {
    checkLoaded();
    if (revision != null) {
      throw new DuplicateKeyException(String.format("account %s already exists", accountId));
    }
    this.loadedAccountProperties =
        Optional.of(new AccountProperties(accountId, registeredOn, new Config(), null));
    return loadedAccountProperties.map(AccountProperties::getAccount).get();
  }

  public AccountConfig setAccountDelta(AccountDelta accountDelta) {
    this.accountDelta = Optional.of(accountDelta);
    return this;
  }

  /**
   * Returns the content of the {@code preferences.config} file wrapped as {@link
   * CachedPreferences}.
   */
  CachedPreferences asCachedPreferences() {
    checkLoaded();
    return CachedPreferences.fromConfig(preferences.getRaw());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      rw.reset();
      rw.markStart(revision);
      rw.sort(RevSort.REVERSE);
      Instant registeredOn = Instant.ofEpochMilli(rw.next().getCommitTime() * 1000L);

      Config accountConfig = readConfig(AccountProperties.ACCOUNT_CONFIG);
      loadedAccountProperties =
          Optional.of(new AccountProperties(accountId, registeredOn, accountConfig, revision));

      projectWatches = new ProjectWatches(accountId, readConfig(ProjectWatches.WATCH_CONFIG), this);

      preferences =
          new StoredPreferences(
              accountId,
              readConfig(StoredPreferences.PREFERENCES_CONFIG),
              StoredPreferences.readDefaultConfig(allUsersName, repo),
              this);

      projectWatches.parse();
      preferences.parse();
    } else {
      loadedAccountProperties = Optional.empty();

      projectWatches = new ProjectWatches(accountId, new Config(), this);

      preferences =
          new StoredPreferences(
              accountId,
              new Config(),
              StoredPreferences.readDefaultConfig(allUsersName, repo),
              this);
    }

    Ref externalIdsRef = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
    externalIdsRev = Optional.ofNullable(externalIdsRef).map(Ref::getObjectId);
  }

  @Override
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    RevCommit c = super.commit(update);
    loadedAccountProperties.get().setMetaId(c);
    return c;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();

    if (!loadedAccountProperties.isPresent()) {
      return false;
    }

    if (revision != null) {
      if (Strings.isNullOrEmpty(commit.getMessage())) {
        commit.setMessage("Update account\n");
      }
    } else {
      if (Strings.isNullOrEmpty(commit.getMessage())) {
        commit.setMessage("Create account\n");
      }

      Instant registeredOn = loadedAccountProperties.get().getRegisteredOn();
      commit.setAuthor(new PersonIdent(commit.getAuthor(), registeredOn));
      commit.setCommitter(new PersonIdent(commit.getCommitter(), registeredOn));
    }

    saveAccount();
    saveProjectWatches();
    savePreferences();

    accountDelta = Optional.empty();

    return true;
  }

  private void saveAccount() throws IOException {
    if (accountDelta.isPresent()) {
      saveConfig(
          AccountProperties.ACCOUNT_CONFIG, loadedAccountProperties.get().save(accountDelta.get()));
    }
  }

  private void saveProjectWatches() throws IOException {
    if (accountDelta.isPresent()
        && (!accountDelta.get().getDeletedProjectWatches().isEmpty()
            || !accountDelta.get().getUpdatedProjectWatches().isEmpty())) {
      Map<ProjectWatchKey, Set<NotifyType>> newProjectWatches =
          new HashMap<>(projectWatches.getProjectWatches());
      accountDelta.get().getDeletedProjectWatches().forEach(newProjectWatches::remove);
      accountDelta.get().getUpdatedProjectWatches().forEach(newProjectWatches::put);
      saveConfig(ProjectWatches.WATCH_CONFIG, projectWatches.save(newProjectWatches));
    }
  }

  private void savePreferences() throws IOException, ConfigInvalidException {
    if (!accountDelta.isPresent()
        || (!accountDelta.get().getGeneralPreferences().isPresent()
            && !accountDelta.get().getDiffPreferences().isPresent()
            && !accountDelta.get().getEditPreferences().isPresent())) {
      return;
    }

    saveConfig(
        StoredPreferences.PREFERENCES_CONFIG,
        preferences.saveGeneralPreferences(
            accountDelta.get().getGeneralPreferences(),
            accountDelta.get().getDiffPreferences(),
            accountDelta.get().getEditPreferences()));
  }

  private void checkLoaded() {
    checkState(loadedAccountProperties != null, "Account %s not loaded yet", accountId.get());
  }

  /**
   * Get the validation errors, if any were discovered during parsing the account data.
   *
   * @return list of errors; empty list if there are no errors.
   */
  public List<ValidationError> getValidationErrors() {
    if (validationErrors != null) {
      return ImmutableList.copyOf(validationErrors);
    }
    return ImmutableList.of();
  }

  @Override
  public void error(ValidationError error) {
    if (validationErrors == null) {
      validationErrors = new ArrayList<>(4);
    }
    validationErrors.add(error);
  }
}
