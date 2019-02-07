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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.index.Schema;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
 * includes inactive accounts, with a small number of specific exceptions noted in method Javadoc.
 */
@Singleton
public class AccountResolver {

  /** Format of inputs allowed for a single call to {@code resolve}. */
  public enum InputFormat {
    /** Only pure numeric account IDs are resolved. */
    ACCOUNT_ID,

    /**
     * Inputs may contain names and/or email addresses.
     *
     * <p>The following input formats are recognized:
     *
     * <ul>
     *   <li>A full name and email address ({@code "Full Name <email@example>"}). This case
     *       short-circuits if the input matches.
     *   <li>An email address ({@code "email@example"}. This case short-circuits if the input
     *       matches.
     *   <li>An account name recognized by the configured {@link Realm#lookup(String)} Realm}.
     *   <li>A full name ({@code "Full Name"}).
     *   <li>As a fallback, a {@link
     *       com.google.gerrit.server.query.account.AccountPredicates#defaultPredicate(Schema,
     *       boolean, String) default search} against the account index.
     * </ul>
     */
    NAME_OR_EMAIL,

    /**
     * Any recognizable format is accepted.
     *
     * <p>The following input formats are recognized:
     *
     * <ul>
     *   <li>The strings {@code "self"} and {@code "me"}, if the current user is an {@link
     *       IdentifiedUser}.
     *   <li>A bare account ID ({@code "18419"}). In this case, and <strong>only</strong> this case,
     *       may return exactly one inactive account. This case short-circuits if the input matches.
     *   <li>An account ID in parentheses following a full name ({@code "Full Name (18419)"}). This
     *       case short-circuits if the input matches.
     *   <li>A username ({@code "username"}).
     *   <li>A full name and email address ({@code "Full Name <email@example>"}). This case
     *       short-circuits if the input matches.
     *   <li>An email address ({@code "email@example"}. This case short-circuits if the input
     *       matches.
     *   <li>An account name recognized by the configured {@link Realm#lookup(String)} Realm}.
     *   <li>A full name ({@code "Full Name"}).
     *   <li>As a fallback, a {@link
     *       com.google.gerrit.server.query.account.AccountPredicates#defaultPredicate(Schema,
     *       boolean, String) default search} against the account index.
     * </ul>
     */
    ANY
  }

  public static class UnresolvableAccountException extends UnprocessableEntityException {
    private static final long serialVersionUID = 1L;
    private final Result result;

    @VisibleForTesting
    UnresolvableAccountException(Result result) {
      super(exceptionMessage(result));
      this.result = result;
    }

    public boolean isSelf() {
      return result.isSelf();
    }
  }

  public static String exceptionMessage(Result result) {
    checkArgument(result.asList().size() != 1);
    if (result.asList().isEmpty()) {
      if (result.isSelf()) {
        return "Resolving account '" + result.input() + "' requires login";
      }
      if (result.filteredInactive().isEmpty()) {
        return "Account '" + result.input() + "' not found";
      }
      return result.filteredInactive().stream()
          .map(a -> formatForException(result, a))
          .collect(
              joining(
                  "\n",
                  "Account '"
                      + result.input()
                      + "' only matches inactive accounts. To use an inactive account, retry with"
                      + " one of the following exact account IDs:\n",
                  ""));
    }

    return result.asList().stream()
        .map(a -> formatForException(result, a))
        .collect(joining("\n", "Account '" + result.input() + "' is ambiguous:\n", ""));
  }

  private static String formatForException(Result result, AccountState state) {
    return state.getAccount().getId()
        + ": "
        + state.getAccount().getNameEmail(result.accountResolver().anonymousCowardName);
  }

  public static boolean isSelf(String input) {
    return "self".equals(input) || "me".equals(input);
  }

  public class Result {
    private final String input;
    private final ImmutableList<AccountState> list;
    private final ImmutableList<AccountState> filteredInactive;

    @VisibleForTesting
    Result(String input, List<AccountState> list, List<AccountState> filteredInactive) {
      this.input = requireNonNull(input);
      this.list = canonicalize(list);
      this.filteredInactive = canonicalize(filteredInactive);
    }

    private ImmutableList<AccountState> canonicalize(List<AccountState> list) {
      TreeSet<AccountState> set = new TreeSet<>(comparing(a -> a.getAccount().getId().get()));
      set.addAll(requireNonNull(list));
      return ImmutableList.copyOf(set);
    }

    public String input() {
      return input;
    }

    public boolean isSelf() {
      return AccountResolver.isSelf(input);
    }

    public ImmutableList<AccountState> asList() {
      return list;
    }

    public ImmutableSet<Account.Id> asNonEmptyIdSet() throws UnresolvableAccountException {
      if (list.isEmpty()) {
        throw new UnresolvableAccountException(this);
      }
      return asIdSet();
    }

    public ImmutableSet<Account.Id> asIdSet() {
      return list.stream().map(a -> a.getAccount().getId()).collect(toImmutableSet());
    }

    public AccountState asUnique() throws UnresolvableAccountException {
      ensureUnique();
      return list.get(0);
    }

    private void ensureUnique() throws UnresolvableAccountException {
      if (list.size() != 1) {
        throw new UnresolvableAccountException(this);
      }
    }

    public IdentifiedUser asUniqueUser() throws UnresolvableAccountException {
      ensureUnique();
      if (isSelf()) {
        // In the special case of "self", use the exact IdentifiedUser from the request context, to
        // preserve the peer address and any other per-request state.
        return self.get().asIdentifiedUser();
      }
      return userFactory.create(asUnique());
    }

    public IdentifiedUser asUniqueUserOnBehalfOf(CurrentUser caller)
        throws UnresolvableAccountException {
      ensureUnique();
      if (isSelf()) {
        // TODO(dborowitz): This preserves old behavior, but it seems wrong to discard the caller.
        return self.get().asIdentifiedUser();
      }
      return userFactory.runAs(
          null, list.get(0).getAccount().getId(), requireNonNull(caller).getRealUser());
    }

    @VisibleForTesting
    ImmutableList<AccountState> filteredInactive() {
      return filteredInactive;
    }

    private AccountResolver accountResolver() {
      return AccountResolver.this;
    }
  }

  @VisibleForTesting
  interface Searcher<I> {
    default boolean callerShouldFilterOutInactiveCandidates() {
      return true;
    }

    default boolean callerMayAssumeCandidatesAreVisible() {
      return false;
    }

    Optional<I> tryParse(String input) throws IOException;

    Stream<AccountState> search(I input) throws IOException, ConfigInvalidException;

    boolean shortCircuitIfNoResults();

    default Optional<Stream<AccountState>> trySearch(String input)
        throws IOException, ConfigInvalidException {
      Optional<I> parsed = tryParse(input);
      return parsed.isPresent() ? Optional.of(search(parsed.get())) : Optional.empty();
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

  private abstract class AccountIdSearcher implements Searcher<Account.Id> {
    @Override
    public final Stream<AccountState> search(Account.Id input) {
      return Streams.stream(accountCache.get(input));
    }
  }

  private class BySelf extends StringSearcher {
    @Override
    public boolean callerShouldFilterOutInactiveCandidates() {
      return false;
    }

    @Override
    public boolean callerMayAssumeCandidatesAreVisible() {
      return true;
    }

    @Override
    protected boolean matches(String input) {
      return "self".equals(input) || "me".equals(input);
    }

    @Override
    public Stream<AccountState> search(String input) {
      CurrentUser user = self.get();
      if (!user.isIdentifiedUser()) {
        return Stream.empty();
      }
      return Stream.of(user.asIdentifiedUser().state());
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return true;
    }
  }

  private class ByExactAccountId extends AccountIdSearcher {
    @Override
    public boolean callerShouldFilterOutInactiveCandidates() {
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

  private class ByUsername extends StringSearcher {
    @Override
    public boolean matches(String input) {
      return ExternalId.isValidUsername(input);
    }

    @Override
    public Stream<AccountState> search(String input) {
      return Streams.stream(accountCache.getByUsername(input));
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
    public Stream<AccountState> search(String nameOrEmail) throws IOException {
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
          allMatches.stream()
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
    public Stream<AccountState> search(String input) throws IOException {
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
    public Optional<AccountState> tryParse(String input) {
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
    public Stream<AccountState> search(String input) {
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

  private final ImmutableList<Searcher<?>> accountIdSearchers =
      ImmutableList.of(new ByExactAccountId());

  private final ImmutableList<Searcher<?>> nameOrEmailSearchers =
      ImmutableList.of(
          new ByNameAndEmail(),
          new ByEmail(),
          new FromRealm(),
          new ByFullName(),
          new ByDefaultSearch());

  private final ImmutableList<Searcher<?>> allSearchers =
      ImmutableList.<Searcher<?>>builder()
          .add(new BySelf())
          .addAll(accountIdSearchers)
          .add(new ByParenthesizedAccountId())
          .add(new ByUsername())
          .addAll(nameOrEmailSearchers)
          .build();

  private final AccountCache accountCache;
  private final AccountControl.Factory accountControlFactory;
  private final Emails emails;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<CurrentUser> self;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final Realm realm;
  private final String anonymousCowardName;

  @Inject
  AccountResolver(
      AccountCache accountCache,
      Emails emails,
      AccountControl.Factory accountControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      Provider<CurrentUser> self,
      Provider<InternalAccountQuery> accountQueryProvider,
      Realm realm,
      @AnonymousCowardName String anonymousCowardName) {
    this.realm = realm;
    this.accountCache = accountCache;
    this.accountControlFactory = accountControlFactory;
    this.userFactory = userFactory;
    this.self = self;
    this.accountQueryProvider = accountQueryProvider;
    this.emails = emails;
    this.anonymousCowardName = anonymousCowardName;
  }

  /** Equivalent to {@link #resolve(String, InputFormat) resolve(ANY)}. */
  public Result resolve(String input) throws ConfigInvalidException, IOException {
    return resolve(input, InputFormat.ANY);
  }

  /**
   * Resolves all accounts matching the input string.
   *
   * <p>The following input formats are recognized:
   *
   * <ul>
   *   <li>The strings {@code "self"} and {@code "me"}, if the current user is an {@link
   *       IdentifiedUser}. In this case, may return exactly one inactive account.
   *   <li>A bare account ID ({@code "18419"}). In this case, may return exactly one inactive
   *       account. This case short-circuits if the input matches.
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
   * @param format allowed input formats. Inputs not in a supported format will return empty
   *     results.
   * @return a result describing matching accounts. Never null even if the result set is empty.
   * @throws ConfigInvalidException if an error occurs.
   * @throws IOException if an error occurs.
   */
  public Result resolve(String input, InputFormat format)
      throws ConfigInvalidException, IOException {
    ImmutableList<Searcher<?>> searchers;
    switch (format) {
      case ACCOUNT_ID:
        searchers = accountIdSearchers;
        break;
      case NAME_OR_EMAIL:
        searchers = nameOrEmailSearchers;
        break;
      case ANY:
        searchers = allSearchers;
        break;
      default:
        throw new IllegalStateException("unknown format: " + format);
    }
    return searchImpl(input, searchers, visibilitySupplier());
  }

  private Supplier<Predicate<AccountState>> visibilitySupplier() {
    return () -> accountControlFactory.get()::canSee;
  }

  @VisibleForTesting
  Result searchImpl(
      String input,
      ImmutableList<Searcher<?>> searchers,
      Supplier<Predicate<AccountState>> visibilitySupplier)
      throws ConfigInvalidException, IOException {
    visibilitySupplier = Suppliers.memoize(visibilitySupplier::get);
    List<AccountState> inactive = new ArrayList<>();

    for (Searcher<?> searcher : searchers) {
      Optional<Stream<AccountState>> maybeResults = searcher.trySearch(input);
      if (!maybeResults.isPresent()) {
        continue;
      }
      Stream<AccountState> results = maybeResults.get();

      if (!searcher.callerMayAssumeCandidatesAreVisible()) {
        results = results.filter(visibilitySupplier.get());
      }

      List<AccountState> list;
      if (searcher.callerShouldFilterOutInactiveCandidates()) {
        // Keep track of all inactive candidates discovered by any searchers. If we end up short-
        // circuiting, the inactive list will be discarded.
        List<AccountState> active = new ArrayList<>();
        results.forEach(a -> (a.getAccount().isActive() ? active : inactive).add(a));
        list = active;
      } else {
        list = results.collect(toImmutableList());
      }

      if (!list.isEmpty()) {
        return createResult(input, list);
      }
      if (searcher.shortCircuitIfNoResults()) {
        // For a short-circuiting searcher, return results even if empty.
        return !inactive.isEmpty() ? emptyResult(input, inactive) : createResult(input, list);
      }
    }
    return emptyResult(input, inactive);
  }

  private Result createResult(String input, List<AccountState> list) {
    return new Result(input, list, ImmutableList.of());
  }

  private Result emptyResult(String input, List<AccountState> inactive) {
    return new Result(input, ImmutableList.of(), inactive);
  }

  private Stream<AccountState> toAccountStates(Set<Account.Id> ids) {
    return accountCache.get(ids).values().stream();
  }
}
