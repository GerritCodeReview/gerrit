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

package com.google.gerrit.server.git.validators;

import static java.util.stream.Collectors.toSet;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.account.ProjectWatches.ProjectWatchKey;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.index.RegexQueryPermissionChecker;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Validator that is used to ensure that new commits on any ref in {@code refs/users} are conforming
 * to the NoteDb format for accounts. Used when a user pushes to one of the refs in {@code
 * refs/users} manually.
 */
public class AccountValidator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<IdentifiedUser> self;
  private final AllUsersName allUsersName;
  private final OutgoingEmailValidator emailValidator;
  private final RegexQueryPermissionChecker regexPermissionChecker;
  private final GitRepositoryManager repoManager;

  @Inject
  public AccountValidator(
      Provider<IdentifiedUser> self,
      AllUsersName allUsersName,
      OutgoingEmailValidator emailValidator,
      RegexQueryPermissionChecker regexPermissionChecker,
      GitRepositoryManager repoManager) {
    this.self = self;
    this.allUsersName = allUsersName;
    this.emailValidator = emailValidator;
    this.regexPermissionChecker = regexPermissionChecker;
    this.repoManager = repoManager;
  }

  /**
   * Returns a list of validation messages. An empty list means that there were no issues found. If
   * the list is non-empty, the commit will be rejected.
   */
  public List<String> validate(
      Account.Id accountId,
      Repository allUsersRepo,
      RevWalk rw,
      @Nullable ObjectId oldId,
      ObjectId newId)
      throws IOException {
    AccountConfig oldAccountConfig = null;
    Optional<Account> oldAccount = Optional.empty();
    if (oldId != null && !ObjectId.zeroId().equals(oldId)) {
      try {
        oldAccountConfig = loadAccountConfig(accountId, allUsersRepo, rw, oldId, null);
        oldAccount = oldAccountConfig.getLoadedAccount();
      } catch (ConfigInvalidException e) {
        // ignore, maybe the new commit is repairing it now
      }
    }

    ImmutableList.Builder<String> messages = ImmutableList.builder();
    AccountConfig newAccountConfig;
    Optional<Account> newAccount;
    try {
      newAccountConfig = loadAccountConfig(accountId, allUsersRepo, rw, newId, messages);
      newAccount = newAccountConfig.getLoadedAccount();
    } catch (ConfigInvalidException e) {
      return ImmutableList.of(
          String.format(
              "commit '%s' has an invalid '%s' file for account '%s': %s",
              newId.name(), AccountProperties.ACCOUNT_CONFIG, accountId.get(), e.getMessage()));
    }

    if (!newAccount.isPresent()) {
      return ImmutableList.of(String.format("account '%s' does not exist", accountId.get()));
    }

    if (!newAccount.get().isActive() && accountId.equals(self.get().getAccountId())) {
      messages.add("cannot deactivate own account");
    }

    String newPreferredEmail = newAccount.get().preferredEmail();
    if (newPreferredEmail != null
        && (!oldAccount.isPresent()
            || !newPreferredEmail.equals(oldAccount.get().preferredEmail()))) {
      if (!emailValidator.isValid(newPreferredEmail)) {
        messages.add(
            String.format(
                "invalid preferred email '%s' for account '%s'",
                newPreferredEmail, accountId.get()));
      }
    }

    if (!regexPermissionChecker.isAllowed()
        && !projectWatchesRegexFilters(oldAccountConfig)
            .containsAll(projectWatchesRegexFilters(newAccountConfig))) {
      messages.add(
          String.format(
              "invalid project watch filters: " + RegexPermissionPolicy.NOT_PERMITTED_MESSAGE));
    }

    return messages.build();
  }

  private Set<ProjectWatchKey> projectWatchesRegexFilters(AccountConfig accountConfig) {
    return Optional.ofNullable(accountConfig).map(AccountConfig::getProjectWatches).stream()
        .flatMap(watches -> watches.keySet().stream())
        .filter(watchKey -> hasRegex(watchKey.filter()))
        .collect(toSet());
  }

  private boolean hasRegex(@Nullable String query) {
    try {
      return query != null && regexPermissionChecker.containsRegexInQuery(query);
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log("Unable to parse regex query: %s", query);
      return false;
    }
  }

  private AccountConfig loadAccountConfig(
      Account.Id accountId,
      Repository allUsersRepo,
      RevWalk rw,
      ObjectId commit,
      @Nullable ImmutableList.Builder<String> messages)
      throws IOException, ConfigInvalidException {
    rw.reset();
    AccountConfig accountConfig = new AccountConfig(accountId, allUsersName, allUsersRepo);
    accountConfig.load(allUsersName, rw, commit);
    if (messages != null) {
      messages.addAll(
          accountConfig.getValidationErrors().stream()
              .map(ValidationError::getMessage)
              .collect(toSet()));
    }
    return accountConfig;
  }

  public boolean allowRegexInFilters(Account.Id accountId, List<ProjectWatchInfo> input)
      throws IOException, ConfigInvalidException {
    if (regexPermissionChecker.isAllowed()) {
      return true;
    }

    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      String accountRefName = RefNames.refsUsers(accountId);
      Ref accountRef = allUsersRepo.exactRef(accountRefName);
      Set<ProjectWatchKey> currentProjectWatches =
          (accountRef == null
              ? ImmutableSet.of()
              : getProjectWatchKeys(allUsersRepo, accountRef.getObjectId(), accountId));
      Set<ProjectWatchKey> newProjectWatches =
          input.stream()
              .filter(projectWatchInfo -> !Strings.isNullOrEmpty(projectWatchInfo.project))
              .filter(projectWatchInfo -> hasRegex(projectWatchInfo.filter))
              .map(
                  watchInfo ->
                      ProjectWatchKey.create(Project.nameKey(watchInfo.project), watchInfo.filter))
              .collect(Collectors.toUnmodifiableSet());

      return currentProjectWatches.containsAll(newProjectWatches);
    }
  }

  private ImmutableSet<ProjectWatchKey> getProjectWatchKeys(
      Repository allUsersRepo, ObjectId accountObjectId, Account.Id accountId)
      throws ConfigInvalidException, IOException {
    try (ObjectReader or = allUsersRepo.newObjectReader();
        RevWalk rw = new RevWalk(or)) {
      AccountConfig accountConfig =
          loadAccountConfig(accountId, allUsersRepo, rw, accountObjectId, null);
      return ImmutableSet.copyOf(
          accountConfig.getProjectWatches().keySet().stream()
              .filter(projectWatchKey -> hasRegex(projectWatchKey.filter()))
              .collect(toSet()));
    }
  }
}
