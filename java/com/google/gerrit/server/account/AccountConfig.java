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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
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
 * ‘account.config’ file in the user branch in the All-Users repository that contains the properties
 * of the account.
 *
 * <p>The 'account.config' file is a git config file that has one 'account' section with the
 * properties of the account:
 *
 * <pre>
 *   [account]
 *     active = false
 *     fullName = John Doe
 *     preferredEmail = john.doe@foo.com
 *     status = Overloaded with reviews
 * </pre>
 *
 * <p>All keys are optional. This means 'account.config' may not exist on the user branch if no
 * properties are set.
 *
 * <p>Not setting a key and setting a key to an empty string are treated the same way and result in
 * a {@code null} value.
 *
 * <p>If no value for 'active' is specified, by default the account is considered as active.
 *
 * <p>The commit date of the first commit on the user branch is used as registration date of the
 * account. The first commit may be an empty commit (if no properties were set and 'account.config'
 * doesn't exist).
 */
public class AccountConfig extends VersionedMetaData implements ValidationError.Sink {
  public static final String ACCOUNT_CONFIG = "account.config";
  public static final String ACCOUNT = "account";
  public static final String KEY_ACTIVE = "active";
  public static final String KEY_FULL_NAME = "fullName";
  public static final String KEY_PREFERRED_EMAIL = "preferredEmail";
  public static final String KEY_STATUS = "status";

  private final Account.Id accountId;
  private final Repository repo;
  private final String ref;

  private Optional<Account> loadedAccount;
  private Optional<ObjectId> externalIdsRev;
  private WatchConfig watchConfig;
  private PreferencesConfig prefConfig;
  private Optional<InternalAccountUpdate> accountUpdate = Optional.empty();
  private Timestamp registeredOn;
  private boolean eagerParsing;
  private List<ValidationError> validationErrors;

  public AccountConfig(Account.Id accountId, Repository allUsersRepo) {
    this.accountId = checkNotNull(accountId, "accountId");
    this.repo = checkNotNull(allUsersRepo, "allUsersRepo");
    this.ref = RefNames.refsUsers(accountId);
  }

  /**
   * Sets whether all account data should be eagerly parsed.
   *
   * <p>Eager parsing should only be used if the caller is interested in validation errors for all
   * account data (see {@link #getValidationErrors()}.
   *
   * @param eagerParsing whether all account data should be eagerly parsed
   * @return this AccountConfig instance for chaining
   */
  public AccountConfig setEagerParsing(boolean eagerParsing) {
    checkState(loadedAccount == null, "Account %s already loaded", accountId.get());
    this.eagerParsing = eagerParsing;
    return this;
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
    return loadedAccount;
  }

  /**
   * Returns the revision of the {@code refs/meta/external-ids} branch.
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
  public Map<ProjectWatchKey, Set<NotifyType>> getProjectWatches() {
    checkLoaded();
    return watchConfig.getProjectWatches();
  }

  /**
   * Get the general preferences of the loaded account.
   *
   * @return the general preferences of the loaded account
   */
  public GeneralPreferencesInfo getGeneralPreferences() {
    checkLoaded();
    return prefConfig.getGeneralPreferences();
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
    this.loadedAccount = Optional.of(account);
    this.accountUpdate =
        Optional.of(
            InternalAccountUpdate.builder()
                .setActive(account.isActive())
                .setFullName(account.getFullName())
                .setPreferredEmail(account.getPreferredEmail())
                .setStatus(account.getStatus())
                .build());
    this.registeredOn = account.getRegisteredOn();
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
    this.registeredOn = registeredOn;
    this.loadedAccount = Optional.of(new Account(accountId, registeredOn));
    return loadedAccount.get();
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
      registeredOn = new Timestamp(rw.next().getCommitTime() * 1000L);

      Config accountConfig = readConfig(ACCOUNT_CONFIG);
      loadedAccount = Optional.of(parse(accountConfig, revision.name()));

      Ref externalIdsRef = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
      externalIdsRev =
          externalIdsRef != null ? Optional.of(externalIdsRef.getObjectId()) : Optional.empty();

      watchConfig = new WatchConfig(accountId, readConfig(WatchConfig.WATCH_CONFIG), this);

      prefConfig =
          new PreferencesConfig(
              accountId,
              readConfig(PreferencesConfig.PREFERENCES_CONFIG),
              PreferencesConfig.readDefaultConfig(repo),
              this);

      if (eagerParsing) {
        watchConfig.parse();
        prefConfig.parse();
      }
    } else {
      loadedAccount = Optional.empty();
    }
  }

  private Account parse(Config cfg, String metaId) {
    Account account = new Account(accountId, registeredOn);
    account.setActive(cfg.getBoolean(ACCOUNT, null, KEY_ACTIVE, true));
    account.setFullName(get(cfg, KEY_FULL_NAME));

    String preferredEmail = get(cfg, KEY_PREFERRED_EMAIL);
    account.setPreferredEmail(preferredEmail);

    account.setStatus(get(cfg, KEY_STATUS));
    account.setMetaId(metaId);
    return account;
  }

  @Override
  public RevCommit commit(MetaDataUpdate update) throws IOException {
    RevCommit c = super.commit(update);
    loadedAccount.get().setMetaId(c.name());
    return c;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    checkLoaded();

    if (!loadedAccount.isPresent()) {
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

      commit.setAuthor(new PersonIdent(commit.getAuthor(), registeredOn));
      commit.setCommitter(new PersonIdent(commit.getCommitter(), registeredOn));
    }

    Config accountConfig = saveAccount();
    saveProjectWatches();
    saveGeneralPreferences();

    // metaId is set in the commit(MetaDataUpdate) method after the commit is created
    loadedAccount = Optional.of(parse(accountConfig, null));

    accountUpdate = Optional.empty();

    return true;
  }

  private Config saveAccount() throws IOException, ConfigInvalidException {
    Config accountConfig = readConfig(ACCOUNT_CONFIG);
    if (accountUpdate.isPresent()) {
      writeToAccountConfig(accountUpdate.get(), accountConfig);
    }
    saveConfig(ACCOUNT_CONFIG, accountConfig);
    return accountConfig;
  }

  public static void writeToAccountConfig(InternalAccountUpdate accountUpdate, Config cfg) {
    accountUpdate.getActive().ifPresent(active -> setActive(cfg, active));
    accountUpdate.getFullName().ifPresent(fullName -> set(cfg, KEY_FULL_NAME, fullName));
    accountUpdate
        .getPreferredEmail()
        .ifPresent(preferredEmail -> set(cfg, KEY_PREFERRED_EMAIL, preferredEmail));
    accountUpdate.getStatus().ifPresent(status -> set(cfg, KEY_STATUS, status));
  }

  private void saveProjectWatches() throws IOException {
    if (accountUpdate.isPresent()
        && (!accountUpdate.get().getDeletedProjectWatches().isEmpty()
            || !accountUpdate.get().getUpdatedProjectWatches().isEmpty())) {
      Map<ProjectWatchKey, Set<NotifyType>> projectWatches = watchConfig.getProjectWatches();
      accountUpdate.get().getDeletedProjectWatches().forEach(pw -> projectWatches.remove(pw));
      accountUpdate
          .get()
          .getUpdatedProjectWatches()
          .forEach((pw, nt) -> projectWatches.put(pw, nt));
      saveConfig(WatchConfig.WATCH_CONFIG, watchConfig.save(projectWatches));
    }
  }

  private void saveGeneralPreferences() throws IOException, ConfigInvalidException {
    if (accountUpdate.isPresent() && accountUpdate.get().getGeneralPreferences().isPresent()) {
      saveConfig(
          PreferencesConfig.PREFERENCES_CONFIG,
          prefConfig.saveGeneralPreferences(accountUpdate.get().getGeneralPreferences().get()));
    }
  }

  /**
   * Sets/Unsets {@code account.active} in the given config.
   *
   * <p>{@code account.active} is set to {@code false} if the account is inactive.
   *
   * <p>If the account is active {@code account.active} is unset since {@code true} is the default
   * if this field is missing.
   *
   * @param cfg the config
   * @param value whether the account is active
   */
  private static void setActive(Config cfg, boolean value) {
    if (!value) {
      cfg.setBoolean(ACCOUNT, null, KEY_ACTIVE, false);
    } else {
      cfg.unset(ACCOUNT, null, KEY_ACTIVE);
    }
  }

  /**
   * Sets/Unsets the given key in the given config.
   *
   * <p>The key unset if the value is {@code null}.
   *
   * @param cfg the config
   * @param key the key
   * @param value the value
   */
  private static void set(Config cfg, String key, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      cfg.setString(ACCOUNT, null, key, value);
    } else {
      cfg.unset(ACCOUNT, null, key);
    }
  }

  /**
   * Gets the given key from the given config.
   *
   * <p>Empty values are returned as {@code null}
   *
   * @param cfg the config
   * @param key the key
   * @return the value, {@code null} if key was not set or key was set to empty string
   */
  private static String get(Config cfg, String key) {
    return Strings.emptyToNull(cfg.getString(ACCOUNT, null, key));
  }

  private void checkLoaded() {
    checkState(loadedAccount != null, "Account %s not loaded yet", accountId.get());
  }

  /**
   * Get the validation errors, if any were discovered during parsing the account data.
   *
   * <p>To get validation errors for all account data request eager parsing before loading the
   * account (see {@link #setEagerParsing(boolean)}).
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
