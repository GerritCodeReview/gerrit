// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.index.Schema;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Helper for resolving accounts given arbitrary user-provided input.
 *
 * <p>The {@code resolve*} methods each define a list of accepted formats for account resolution.
 * The algorithm for resolving accounts from a list of formats is as follows:
 *
 * <ol>
 *   <li>For each recognized format in the order listed in the method Javadoc, check whether the
 *       input matches that format.
 *   <li>If so, resolve accounts according to that format.
 *   <li>Filter out invisible and inactive accounts.
 *   <li>If the result list is non-empty, return.
 *   <li>If the format is listed above as being short-circuiting, return.
 *   <li>Otherwise, return to step 1 with the next format.
 * </ol>
 *
 * <p>The result never includes accounts that are not visible to the calling user. It also never
 * includes inactive accounts, with one specific exception noted in method Javadoc.
 */
@Singleton
public class AccountResolver2 {
  @AutoValue
  public abstract static class Result {
    static Result create(String input, ImmutableList<AccountState> list, Searcher<?> searcher) {
      return new AutoValue_AccountResolver2_Result(input, list, Optional.of(searcher.toString()));
    }

    static Result empty(String input) {
      return new AutoValue_AccountResolver2_Result(input, ImmutableList.of(), Optional.empty());
    }

    public abstract String input();

    public ImmutableList<AccountState> asList() {
      return list();
    }

    public ImmutableSet<Account.Id> asIdSet() {
      return list().stream().map(a -> a.getAccount().getId()).collect(toImmutableSet());
    }

    public AccountState asUnique() throws UnprocessableEntityException {
      switch (list().size()) {
        case 1:
          return list().get(0);
        case 0:
          // TODO(dborowitz): Include information about whether this takes invisible/inactive users
          // into account.
          throw new UnprocessableEntityException("Account '" + input() + "' not found");
        default:
          throw new UnprocessableEntityException("Account '" + input() + "' is ambiguous");
      }
    }

    abstract ImmutableList<AccountState> list();

    @VisibleForTesting
    abstract Optional<String> searcher();
  }

  @VisibleForTesting
  interface Searcher<I> {
    default boolean callerShouldFilterOutInactiveCandidates() {
      return true;
    }

    default boolean callerMayAssumeCandidatesAreVisible() {
      return false;
    }

    Optional<I> tryParse(String input) throws IOException, OrmException;

    Stream<AccountState> search(I input) throws OrmException, IOException, ConfigInvalidException;

    boolean shortCircuitIfNoResults();
  }

  private abstract class AccountIdSearcher implements Searcher<Account.Id> {
    @Override
    public final Stream<AccountState> search(Account.Id input) {
      return Streams.stream(byId.get(input));
    }
  }

  private class ByExactAccountId extends AccountIdSearcher {
    @Override
    public boolean callerShouldFilterOutInactiveCandidates() {
      // The only case where we *don't* enforce that the account is active is when passing an exact
      // numeric account ID.
      return false;
    }

    @Override
    public Optional<Account.Id> tryParse(String input) {
      return Account.Id.tryParse(input);
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return true;
    }
  }

  private class ByParenthesizedAccountId extends AccountIdSearcher {
    private final Pattern pattern = Pattern.compile("^.* \\(([1-9][0-9]*)\\)$");

    @Override
    public Optional<Account.Id> tryParse(String input) {
      Matcher m = pattern.matcher(input);
      return m.matches() ? Account.Id.tryParse(m.group(1)) : Optional.empty();
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return true;
    }
  }

  @VisibleForTesting
  abstract static class StringSearcher implements Searcher<String> {
    @Override
    public final Optional<String> tryParse(String input) {
      return matches(input) ? Optional.of(input) : Optional.empty();
    }

    protected abstract boolean matches(String input);
  }

  private class ByUsername extends StringSearcher {
    @Override
    public boolean matches(String input) {
      return ExternalId.isValidUsername(input);
    }

    @Override
    public Stream<AccountState> search(String input) {
      return Streams.stream(byId.getByUsername(input));
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return false;
    }
  }

  private class ByNameAndEmail extends StringSearcher {
    @Override
    protected boolean matches(String input) {
      int lt = input.indexOf('<');
      int gt = input.indexOf('>');
      return lt >= 0 && gt > lt && input.contains("@");
    }

    @Override
    public Stream<AccountState> search(String nameOrEmail) throws OrmException, IOException {
      // TODO(dborowitz): This would probably work as a Searcher<Address>
      int lt = nameOrEmail.indexOf('<');
      int gt = nameOrEmail.indexOf('>');
      Set<Account.Id> ids = emails.getAccountFor(nameOrEmail.substring(lt + 1, gt));
      ImmutableList<AccountState> allMatches = toAccountStates(ids).collect(toImmutableList());
      if (allMatches.isEmpty() || allMatches.size() == 1) {
        return allMatches.stream();
      }

      // More than one match. If there are any that match the full name as well, return only that
      // subset. Otherwise, all are equally non-matching, so return the full set.
      String name = nameOrEmail.substring(0, lt - 1);
      ImmutableList<AccountState> nameMatches =
          allMatches
              .stream()
              .filter(a -> name.equals(a.getAccount().getFullName()))
              .collect(toImmutableList());
      return !nameMatches.isEmpty() ? nameMatches.stream() : allMatches.stream();
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return true;
    }
  }

  private class ByEmail extends StringSearcher {
    @Override
    protected boolean matches(String input) {
      return input.contains("@");
    }

    @Override
    public Stream<AccountState> search(String input) throws OrmException, IOException {
      return toAccountStates(emails.getAccountFor(input));
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return true;
    }
  }

  private class FromRealm extends AccountIdSearcher {
    @Override
    public Optional<Account.Id> tryParse(String input) throws IOException {
      return Optional.ofNullable(realm.lookup(input));
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return false;
    }
  }

  private class ByFullName implements Searcher<AccountState> {
    @Override
    public boolean callerMayAssumeCandidatesAreVisible() {
      return true; // Rely on enforceVisibility from the index.
    }

    @Override
    public Optional<AccountState> tryParse(String input) throws OrmException {
      List<AccountState> results =
          accountQueryProvider.get().enforceVisibility(true).byFullName(input);
      return results.size() == 1 ? Optional.of(results.get(0)) : Optional.empty();
    }

    @Override
    public Stream<AccountState> search(AccountState input) {
      return Stream.of(input);
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return false;
    }
  }

  private class ByDefaultSearch extends StringSearcher {
    @Override
    public boolean callerMayAssumeCandidatesAreVisible() {
      return true; // Rely on enforceVisibility from the index.
    }

    @Override
    protected boolean matches(String input) {
      return true;
    }

    @Override
    public Stream<AccountState> search(String input) throws OrmException {
      // At this point we have no clue. Just perform a whole bunch of suggestions and pray we come
      // up with a reasonable result list.
      // TODO(dborowitz): This doesn't match the documentation; consider whether it's possible to be
      // more strict here.
      return accountQueryProvider.get().enforceVisibility(true).byDefault(input).stream();
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      // In practice this doesn't matter since this is the last searcher in the list, but considered
      // on its own, it doesn't necessarily need to be terminal.
      return false;
    }
  }

  private final ImmutableList<Searcher<?>> searchers =
      ImmutableList.<Searcher<?>>builder()
          .add(new ByExactAccountId())
          .add(new ByParenthesizedAccountId())
          .add(new ByUsername())
          .add(new ByNameAndEmail())
          .add(new ByEmail())
          .add(new FromRealm())
          .add(new ByFullName())
          .add(new ByDefaultSearch())
          .build();

  private final AccountCache byId;
  private final AccountControl.Factory accountControlFactory;
  private final Emails emails;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Realm realm;

  @Inject
  AccountResolver2(
      AccountCache byId,
      Emails emails,
      AccountControl.Factory accountControlFactory,
      Provider<InternalAccountQuery> accountQueryProvider,
      Realm realm) {
    this.realm = realm;
    this.byId = byId;
    this.accountControlFactory = accountControlFactory;
    this.accountQueryProvider = accountQueryProvider;
    this.emails = emails;
  }

  /**
   * Resolves all accounts matching the input string.
   *
   * <p>The following input formats are recognized:
   *
   * <ul>
   *   <li>A bare account ID ({@code "18419"}). In this case, and <strong>only</strong> this case,
   *       may return exactly one inactive account. This case short-circuits if the input matches.
   *   <li>An account ID in parentheses following a full name ({@code "Full Name (18419)"}). This
   *       case short-circuits if the input matches.
   *   <li>A username ({@code "username"}).
   *   <li>A full name and email address ({@code "Full Name <email@example>"}). This case
   *       short-circuits if the input matches.
   *   <li>An email address ({@code "email@example"}. This case short-circuits if the input matches.
   *   <li>An account name recognized by the configured {@link Realm#lookup(String)} Realm}.
   *   <li>A full name ({@code "Full Name"}).
   *   <li>As a fallback, a {@link
   *       com.google.gerrit.server.query.account.AccountPredicates#defaultPredicate(Schema,
   *       boolean, String) default search} against the account index.
   * </ul>
   *
   * @param input input string.
   * @return a result describing matching accounts. Never null even if the result set is empty.
   * @throws OrmException if an error occurs.
   * @throws ConfigInvalidException if an error occurs.
   * @throws IOException if an error occurs.
   */
  public Result resolve(String input) throws OrmException, ConfigInvalidException, IOException {
    return searchImpl(input, searchers, () -> accountControlFactory.get()::canSee);
  }

  @VisibleForTesting
  static Result searchImpl(
      String input,
      ImmutableList<Searcher<?>> searchers,
      Supplier<Predicate<AccountState>> visibilitySupplier)
      throws OrmException, ConfigInvalidException, IOException {
    visibilitySupplier = Suppliers.memoize(visibilitySupplier::get);
    for (Searcher<?> searcher : searchers) {
      Optional<Result> result = trySearch(searcher, input, visibilitySupplier);
      if (result.isPresent()) {
        return result.get();
      }
    }
    return Result.empty(input);
  }

  private static <I> Optional<Result> trySearch(
      Searcher<I> searcher, String input, Supplier<Predicate<AccountState>> visibilitySupplier)
      throws OrmException, ConfigInvalidException, IOException {
    Optional<I> parsed = searcher.tryParse(input);
    if (!parsed.isPresent()) {
      return Optional.empty(); // Input is not in this searcher's format.
    }

    Stream<AccountState> result = searcher.search(parsed.get());
    if (searcher.callerShouldFilterOutInactiveCandidates()) {
      result = result.filter(a -> a.getAccount().isActive());
    }
    if (!searcher.callerMayAssumeCandidatesAreVisible()) {
      result = result.filter(visibilitySupplier.get());
    }
    ImmutableList<AccountState> list = result.collect(toImmutableList());
    if (!list.isEmpty()
        // For a short-circuiting searcher, return results even if empty.
        || searcher.shortCircuitIfNoResults()) {
      return Optional.of(Result.create(input, list, searcher));
    }
    // Otherwise, in the case of an empty list, tell searchImpl to proceed to the next searcher.
    // The emptiness check must happen after filtering, so that end users can't distinguish between
    // the case where the searcher produced no results and where it only produced inactive/invisible
    // results.
    return Optional.empty();
  }

  private Stream<AccountState> toAccountStates(Set<Account.Id> ids) {
    return byId.get(ids).values().stream();
  }
}
