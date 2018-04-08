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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.git.ValidationError;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.sql.Timestamp;
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
 *       {@link Preferences}.
 *   <li>'account.config': Contains the project watches. Parsing and writing it is delegated to
 *       {@link ProjectWatches}.
 * </ul>
 *
 * <p>The commit date of the first commit on the user branch is used as registration date of the
 * account. The first commit may be an empty commit (since all config files are optional).
 */
public class AccountConfig extends VersionedMetaData implements ValidationError.Sink {
  private final Account.Id accountId;
  private final Repository repo;
  private final String ref;

  private Optional<AccountProperties> loadedAccountProperties;
  private Optional<ObjectId> externalIdsRev;
  private ProjectWatches projectWatches;
  private Preferences preferences;
  private Optional<InternalAccountUpdate> accountUpdate = Optional.empty();
  private List<ValidationError> validationErrors;

  public AccountConfig(Account.Id accountId, Repository allUsersRepo) {
    this.accountId = checkNotNull(accountId, "accountId");
    this.repo = checkNotNull(allUsersRepo, "allUsersRepo");
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public AccountConfig load() throws IOException, ConfigInvalidException {
    load(repo);
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
   * ExternalIds#byAccount(com.google.gerrit.reviewdb.client.Account.Id, ObjectId)}.
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
   * Get the general preferences of the loaded account.
   *
   * @return the general preferences of the loaded account
   */
  public GeneralPreferencesInfo getGeneralPreferences() {
    checkLoaded();
    return preferences.getGeneralPreferences();
  }

  /**
   * Get the diff preferences of the loaded account.
   *
   * @return the diff preferences of the loaded account
   */
  public DiffPreferencesInfo getDiffPreferences() {
    checkLoaded();
    return preferences.getDiffPreferences();
  }

  /**
   * Get the edit preferences of the loaded account.
   *
   * @return the edit preferences of the loaded account
   */
  public EditPreferencesInfo getEditPreferences() {
    checkLoaded();
    return preferences.getEditPreferences();
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
            new AccountProperties(account.getId(), account.getRegisteredOn(), new Config(), null));
    this.accountUpdate =
        Optional.of(
            InternalAccountUpdate.builder()
                .setActive(account.isActive())
                .setFullName(account.getFullName())
                .setPreferredEmail(account.getPreferredEmail())
                .setStatus(account.getStatus())
                .build());
    return this;
  }

  /**
   * Creates a new account.
   *
   * @return the new account
   * @throws OrmDuplicateKeyException if the user branch already exists
   */
  public Account getNewAccount() throws OrmDuplicateKeyException {
    return getNewAccount(TimeUtil.nowTs());
  }

  /**
   * Creates a new account.
   *
   * @return the new account
   * @throws OrmDuplicateKeyException if the user branch already exists
   */
  Account getNewAccount(Timestamp registeredOn) throws OrmDuplicateKeyException {
    checkLoaded();
    if (revision != null) {
      throw new OrmDuplicateKeyException(String.format("account %s already exists", accountId));
    }
    this.loadedAccountProperties =
        Optional.of(new AccountProperties(accountId, registeredOn, new Config(), null));
    return loadedAccountProperties.map(AccountProperties::getAccount).get();
  }

  public AccountConfig setAccountUpdate(InternalAccountUpdate accountUpdate) {
    this.accountUpdate = Optional.of(accountUpdate);
    return this;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (revision != null) {
      rw.reset();
      rw.markStart(revision);
      rw.sort(RevSort.REVERSE);
      Timestamp registeredOn = new Timestamp(rw.next().getCommitTime() * 1000L);

      Config accountConfig = readConfig(AccountProperties.ACCOUNT_CONFIG);
      loadedAccountProperties =
          Optional.of(new AccountProperties(accountId, registeredOn, accountConfig, revision));

      projectWatches = new ProjectWatches(accountId, readConfig(ProjectWatches.WATCH_CONFIG), this);

      preferences =
          new Preferences(
              accountId,
              readConfig(Preferences.PREFERENCES_CONFIG),
              Preferences.readDefaultConfig(repo),
              this);

      projectWatches.parse();
      preferences.parse();
    } else {
      loadedAccountProperties = Optional.empty();

      projectWatches = new ProjectWatches(accountId, new Config(), this);

      preferences =
          new Preferences(accountId, new Config(), Preferences.readDefaultConfig(repo), this);
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

      Timestamp registeredOn = loadedAccountProperties.get().getRegisteredOn();
      commit.setAuthor(new PersonIdent(commit.getAuthor(), registeredOn));
      commit.setCommitter(new PersonIdent(commit.getCommitter(), registeredOn));
    }

    saveAccount();
    saveProjectWatches();
    savePreferences();

    accountUpdate = Optional.empty();

    return true;
  }

  private void saveAccount() throws IOException {
    if (accountUpdate.isPresent()) {
      saveConfig(
          AccountProperties.ACCOUNT_CONFIG,
          loadedAccountProperties.get().save(accountUpdate.get()));
    }
  }

  private void saveProjectWatches() throws IOException {
    if (accountUpdate.isPresent()
        && (!accountUpdate.get().getDeletedProjectWatches().isEmpty()
            || !accountUpdate.get().getUpdatedProjectWatches().isEmpty())) {
      Map<ProjectWatchKey, Set<NotifyType>> newProjectWatches =
          new HashMap<>(projectWatches.getProjectWatches());
      accountUpdate.get().getDeletedProjectWatches().forEach(pw -> newProjectWatches.remove(pw));
      accountUpdate
          .get()
          .getUpdatedProjectWatches()
          .forEach((pw, nt) -> newProjectWatches.put(pw, nt));
      saveConfig(ProjectWatches.WATCH_CONFIG, projectWatches.save(newProjectWatches));
    }
  }

  private void savePreferences() throws IOException, ConfigInvalidException {
    if (!accountUpdate.isPresent()
        || (!accountUpdate.get().getGeneralPreferences().isPresent()
            && !accountUpdate.get().getDiffPreferences().isPresent()
            && !accountUpdate.get().getEditPreferences().isPresent())) {
      return;
    }

    saveConfig(
        Preferences.PREFERENCES_CONFIG,
        preferences.saveGeneralPreferences(
            accountUpdate.get().getGeneralPreferences(),
            accountUpdate.get().getDiffPreferences(),
            accountUpdate.get().getEditPreferences()));
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
