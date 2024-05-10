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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.Sequences;
import com.google.inject.BindingAnnotation;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Creates and updates accounts.
 *
 * <p>This interface should be used for all account updates. See {@link AccountDelta} for what can
 * be updated.
 *
 * <p>For creating a new account a new account ID can be retrieved from {@link
 * Sequences#nextAccountId()}.
 *
 * <p>See the implementing classes for more information.
 */
public abstract class AccountsUpdate {
  public interface AccountsUpdateLoader {
    /**
     * Creates an {@code AccountsUpdate} which uses the identity of the specified user as author for
     * all commits related to accounts. The server identity will be used as committer.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.UserInitiated} annotation on the provider of an {@code
     * AccountsUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed
     */
    AccountsUpdate create(IdentifiedUser currentUser);

    /**
     * Creates an {@code AccountsUpdate} which uses the server identity as author and committer for
     * all commits related to accounts.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.ServerInitiated} annotation on the provider of an {@code
     * AccountsUpdate} instead.
     */
    AccountsUpdate createWithServerIdent();

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface WithReindex {}

    @BindingAnnotation
    @Target({FIELD, PARAMETER, METHOD})
    @Retention(RUNTIME)
    @interface NoReindex {}
  }

  /** Data holder for the set of arguments required to update an account. Used for batch updates. */
  public static class UpdateArguments {
    public final String message;
    public final Account.Id accountId;
    public final AccountsUpdate.ConfigureDeltaFromState configureDeltaFromState;

    public UpdateArguments(
        String message,
        Account.Id accountId,
        AccountsUpdate.ConfigureDeltaFromState configureDeltaFromState) {
      this.message = message;
      this.accountId = accountId;
      this.configureDeltaFromState = configureDeltaFromState;
    }
  }

  /**
   * Account updates are commonly performed by evaluating the current account state and creating a
   * delta to be applied to it in a later step. This is done by implementing this interface.
   *
   * <p>If the current account state is not needed, use a {@link Consumer} of {@link
   * com.google.gerrit.server.account.AccountDelta.Builder} instead.
   */
  @FunctionalInterface
  public interface ConfigureDeltaFromState {
    /**
     * Receives the current {@link AccountState} (which is immutable) and configures an {@link
     * com.google.gerrit.server.account.AccountDelta.Builder} with changes to the account.
     *
     * @param accountState the state of the account that is being updated
     * @param delta the changes to be applied
     */
    void configure(AccountState accountState, AccountDelta.Builder delta) throws IOException;
  }

  /** Returns an instance that runs all specified consumers. */
  public static ConfigureDeltaFromState joinConsumers(
      List<Consumer<AccountDelta.Builder>> consumers) {
    return (accountStateIgnored, update) -> consumers.forEach(c -> c.accept(update));
  }

  static ConfigureDeltaFromState fromConsumer(Consumer<AccountDelta.Builder> consumer) {
    return (a, u) -> consumer.accept(u);
  }

  protected final PersonIdent committerIdent;
  protected final PersonIdent authorIdent;

  protected final Optional<IdentifiedUser> currentUser;

  protected AccountsUpdate(PersonIdent serverIdent, Optional<IdentifiedUser> user) {
    this.currentUser = user;
    this.committerIdent = serverIdent;
    this.authorIdent = createPersonIdent(serverIdent, user);
  }

  /**
   * Like {@link #insert(String, Account.Id, ConfigureDeltaFromState)}, but using a {@link Consumer}
   * instead, i.e. the update does not depend on the current account state (which, for insertion,
   * would only contain the account ID).
   */
  @CanIgnoreReturnValue
  public final AccountState insert(
      String message, Account.Id accountId, Consumer<AccountDelta.Builder> init)
      throws IOException, ConfigInvalidException {
    return insert(message, accountId, AccountsUpdate.fromConsumer(init));
  }

  /**
   * Inserts a new account.
   *
   * @param message commit message for the account creation, must not be {@code null or empty}
   * @param accountId ID of the new account
   * @param init to populate the new account
   * @return the newly created account
   * @throws DuplicateKeyException if the account already exists
   * @throws IOException if creating the user branch fails due to an IO error
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public abstract AccountState insert(
      String message, Account.Id accountId, ConfigureDeltaFromState init)
      throws IOException, ConfigInvalidException;

  /**
   * Like {@link #update(String, Account.Id, ConfigureDeltaFromState)}, but using a {@link Consumer}
   * instead, i.e. the update does not depend on the current account state.
   */
  @CanIgnoreReturnValue
  public final Optional<AccountState> update(
      String message, Account.Id accountId, Consumer<AccountDelta.Builder> update)
      throws IOException, ConfigInvalidException {
    return update(message, accountId, AccountsUpdate.fromConsumer(update));
  }

  /**
   * Gets the account and updates it atomically.
   *
   * <p>Changing the registration date of an account is not supported.
   *
   * @param message commit message for the account update, must not be {@code null or empty}
   * @param accountId ID of the account
   * @param configureDeltaFromState deltaBuilder to update the account, only invoked if the account
   *     exists
   * @return the updated account, {@link Optional#empty} if the account doesn't exist
   * @throws IOException if updating the user branch fails due to an IO error
   * @throws LockFailureException if updating the user branch still fails due to concurrent updates
   *     after the retry timeout exceeded
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  @CanIgnoreReturnValue
  public final Optional<AccountState> update(
      String message, Account.Id accountId, ConfigureDeltaFromState configureDeltaFromState)
      throws IOException, ConfigInvalidException {
    return updateBatch(
            ImmutableList.of(new UpdateArguments(message, accountId, configureDeltaFromState)))
        .get(0);
  }

  /**
   * Updates multiple different accounts atomically. This will only store a single new value (aka
   * set of all external IDs of the host) in the external ID cache, which is important for storage
   * economy. All {@code updates} must be for different accounts.
   *
   * <p>NOTE on error handling: Since updates are executed in multiple stages, with some stages
   * resulting from the union of all individual updates, we cannot point to the update that caused
   * the error. Callers should be aware that a single "update of death" (or a set of updates that
   * together have this property) will always prevent the entire batch from being executed.
   */
  @CanIgnoreReturnValue
  public final ImmutableList<Optional<AccountState>> updateBatch(List<UpdateArguments> updates)
      throws IOException, ConfigInvalidException {
    checkArgument(
        updates.stream().map(u -> u.accountId.get()).distinct().count() == updates.size(),
        "updates must all be for different accounts");
    return executeUpdates(updates);
  }

  /**
   * Deletes all the account state data.
   *
   * @param message commit message for the account update, must not be {@code null or empty}
   * @param accountId ID of the account
   * @throws IOException if updating the user branch fails due to an IO error
   * @throws ConfigInvalidException if any of the account fields has an invalid value
   */
  public abstract void delete(String message, Account.Id accountId)
      throws IOException, ConfigInvalidException;

  @VisibleForTesting // productionVisibility: protected
  public abstract ImmutableList<Optional<AccountState>> executeUpdates(
      List<UpdateArguments> updates) throws ConfigInvalidException, IOException;

  private static PersonIdent createPersonIdent(
      PersonIdent serverIdent, Optional<IdentifiedUser> user) {
    return user.isPresent() ? user.get().newCommitterIdent(serverIdent) : serverIdent;
  }
}
