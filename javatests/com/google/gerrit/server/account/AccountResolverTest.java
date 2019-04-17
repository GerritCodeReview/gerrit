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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountResolver.Result;
import com.google.gerrit.server.account.AccountResolver.Searcher;
import com.google.gerrit.server.account.AccountResolver.StringSearcher;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.Test;

public class AccountResolverTest extends GerritBaseTests {
  private class TestSearcher extends StringSearcher {
    private final String pattern;
    private final boolean shortCircuit;
    private final ImmutableList<AccountState> accounts;
    private boolean assumeVisible;
    private boolean filterInactive;

    private TestSearcher(String pattern, boolean shortCircuit, AccountState... accounts) {
      this.pattern = pattern;
      this.shortCircuit = shortCircuit;
      this.accounts = ImmutableList.copyOf(accounts);
    }

    @Override
    protected boolean matches(String input) {
      return input.matches(pattern);
    }

    @Override
    public Stream<AccountState> search(String input) {
      return accounts.stream();
    }

    @Override
    public boolean shortCircuitIfNoResults() {
      return shortCircuit;
    }

    @Override
    public boolean callerMayAssumeCandidatesAreVisible() {
      return assumeVisible;
    }

    void setCallerMayAssumeCandidatesAreVisible() {
      this.assumeVisible = true;
    }

    @Override
    public boolean callerShouldFilterOutInactiveCandidates() {
      return filterInactive;
    }

    void setCallerShouldFilterOutInactiveCandidates() {
      this.filterInactive = true;
    }

    @Override
    public String toString() {
      return accounts.stream()
          .map(a -> a.getAccount().getId().toString())
          .collect(joining(",", pattern + "(", ")"));
    }
  }

  @Test
  public void noShortCircuit() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(
            new TestSearcher("foo", false, newAccount(1)),
            new TestSearcher("bar", false, newAccount(2), newAccount(3)));

    Result result = search("foo", searchers, allVisible());
    assertThat(result.input()).isEqualTo("foo");
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1));

    result = search("bar", searchers, allVisible());
    assertThat(result.input()).isEqualTo("bar");
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(2, 3));

    result = search("baz", searchers, allVisible());
    assertThat(result.input()).isEqualTo("baz");
    assertThat(result.asIdSet()).isEmpty();
  }

  @Test
  public void shortCircuit() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(
            new TestSearcher("f.*", true), new TestSearcher("foo|bar", false, newAccount(1)));

    Result result = search("foo", searchers, allVisible());
    assertThat(result.input()).isEqualTo("foo");
    assertThat(result.asIdSet()).isEmpty();

    result = search("bar", searchers, allVisible());
    assertThat(result.input()).isEqualTo("bar");
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1));
  }

  @Test
  public void filterInvisible() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(new TestSearcher("foo", false, newAccount(1), newAccount(2)));

    assertThat(search("foo", searchers, allVisible()).asIdSet())
        .containsExactlyElementsIn(ids(1, 2));
    assertThat(search("foo", searchers, only(2)).asIdSet()).containsExactlyElementsIn(ids(2));
  }

  @Test
  public void skipVisibilityCheck() throws Exception {
    TestSearcher searcher = new TestSearcher("foo", false, newAccount(1), newAccount(2));
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher);

    assertThat(search("foo", searchers, only(2)).asIdSet()).containsExactlyElementsIn(ids(2));

    searcher.setCallerMayAssumeCandidatesAreVisible();
    assertThat(search("foo", searchers, only(2)).asIdSet()).containsExactlyElementsIn(ids(1, 2));
  }

  @Test
  public void dontFilterInactive() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(
            new TestSearcher("foo", false, newInactiveAccount(1)),
            new TestSearcher("f.*", false, newInactiveAccount(2)));

    Result result = search("foo", searchers, allVisible());
    // Searchers always short-circuit when finding a non-empty result list, and this one didn't
    // filter out inactive results, so the second searcher never ran.
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1));
    assertThat(getOnlyElement(result.asList()).getAccount().isActive()).isFalse();
    assertThat(filteredInactiveIds(result)).isEmpty();
  }

  @Test
  public void filterInactiveEventuallyFindingResults() throws Exception {
    TestSearcher searcher1 = new TestSearcher("foo", false, newInactiveAccount(1));
    searcher1.setCallerShouldFilterOutInactiveCandidates();
    TestSearcher searcher2 = new TestSearcher("f.*", false, newAccount(2));
    searcher2.setCallerShouldFilterOutInactiveCandidates();
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher1, searcher2);

    Result result = search("foo", searchers, allVisible());
    assertThat(search("foo", searchers, allVisible()).asIdSet()).containsExactlyElementsIn(ids(2));
    // No info about inactive results exposed if there was at least one active result.
    assertThat(filteredInactiveIds(result)).isEmpty();
  }

  @Test
  public void filterInactiveEventuallyFindingNoResults() throws Exception {
    TestSearcher searcher1 = new TestSearcher("foo", false, newInactiveAccount(1));
    searcher1.setCallerShouldFilterOutInactiveCandidates();
    TestSearcher searcher2 = new TestSearcher("f.*", false, newInactiveAccount(2));
    searcher2.setCallerShouldFilterOutInactiveCandidates();
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher1, searcher2);

    Result result = search("foo", searchers, allVisible());
    assertThat(result.asIdSet()).isEmpty();
    assertThat(filteredInactiveIds(result)).containsExactlyElementsIn(ids(1, 2));
  }

  @Test
  public void dontShortCircuitAfterFilteringInactiveCandidatesResultsInEmptyList()
      throws Exception {
    AccountState account1 = newAccount(1);
    AccountState account2 = newInactiveAccount(2);
    TestSearcher searcher1 = new TestSearcher("foo", false, account2);
    searcher1.setCallerShouldFilterOutInactiveCandidates();

    TestSearcher searcher2 = new TestSearcher("foo", false, account1, account2);
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher1, searcher2);

    // searcher1 matched, but filtered out all candidates because account2 is inactive. Actual
    // result came from searcher2 instead.
    Result result = search("foo", searchers, allVisible());
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1, 2));
  }

  @Test
  public void shortCircuitAfterFilteringInactiveCandidatesResultsInEmptyList() throws Exception {
    AccountState account1 = newAccount(1);
    AccountState account2 = newInactiveAccount(2);
    TestSearcher searcher1 = new TestSearcher("foo", true, account2);
    searcher1.setCallerShouldFilterOutInactiveCandidates();

    TestSearcher searcher2 = new TestSearcher("foo", false, account1, account2);
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher1, searcher2);

    // searcher1 matched and then filtered out all candidates because account2 is inactive, but
    // still short-circuited.
    Result result = search("foo", searchers, allVisible());
    assertThat(result.asIdSet()).isEmpty();
    assertThat(filteredInactiveIds(result)).containsExactlyElementsIn(ids(2));
  }

  @Test
  public void asUniqueWithNoResults() throws Exception {
    try {
      String input = "foo";
      ImmutableList<Searcher<?>> searchers = ImmutableList.of();
      Supplier<Predicate<AccountState>> visibilitySupplier = allVisible();
      search(input, searchers, visibilitySupplier).asUnique();
      assert_().fail("Expected UnresolvableAccountException");
    } catch (UnresolvableAccountException e) {
      assertThat(e).hasMessageThat().isEqualTo("Account 'foo' not found");
    }
  }

  @Test
  public void asUniqueWithOneResult() throws Exception {
    AccountState account = newAccount(1);
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(new TestSearcher("foo", false, account));
    assertThat(search("foo", searchers, allVisible()).asUnique().getAccount().getId())
        .isEqualTo(account.getAccount().getId());
  }

  @Test
  public void asUniqueWithMultipleResults() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(new TestSearcher("foo", false, newAccount(1), newAccount(2)));
    try {
      search("foo", searchers, allVisible()).asUnique();
      assert_().fail("Expected UnresolvableAccountException");
    } catch (UnresolvableAccountException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Account 'foo' is ambiguous:\n1: Anonymous Name (1)\n2: Anonymous Name (2)");
    }
  }

  @Test
  public void exceptionMessageNotFound() throws Exception {
    AccountResolver resolver = newAccountResolver();
    assertThat(
            new UnresolvableAccountException(
                resolver.new Result("foo", ImmutableList.of(), ImmutableList.of())))
        .hasMessageThat()
        .isEqualTo("Account 'foo' not found");
  }

  @Test
  public void exceptionMessageSelf() throws Exception {
    AccountResolver resolver = newAccountResolver();
    UnresolvableAccountException e =
        new UnresolvableAccountException(
            resolver.new Result("self", ImmutableList.of(), ImmutableList.of()));
    assertThat(e.isSelf()).isTrue();
    assertThat(e).hasMessageThat().isEqualTo("Resolving account 'self' requires login");
  }

  @Test
  public void exceptionMessageMe() throws Exception {
    AccountResolver resolver = newAccountResolver();
    UnresolvableAccountException e =
        new UnresolvableAccountException(
            resolver.new Result("me", ImmutableList.of(), ImmutableList.of()));
    assertThat(e.isSelf()).isTrue();
    assertThat(e).hasMessageThat().isEqualTo("Resolving account 'me' requires login");
  }

  @Test
  public void exceptionMessageAmbiguous() throws Exception {
    AccountResolver resolver = newAccountResolver();
    assertThat(
            new UnresolvableAccountException(
                resolver
                .new Result(
                    "foo", ImmutableList.of(newAccount(3), newAccount(1)), ImmutableList.of())))
        .hasMessageThat()
        .isEqualTo("Account 'foo' is ambiguous:\n1: Anonymous Name (1)\n3: Anonymous Name (3)");
  }

  @Test
  public void exceptionMessageOnlyInactive() throws Exception {
    AccountResolver resolver = newAccountResolver();
    assertThat(
            new UnresolvableAccountException(
                resolver
                .new Result(
                    "foo",
                    ImmutableList.of(),
                    ImmutableList.of(newInactiveAccount(3), newInactiveAccount(1)))))
        .hasMessageThat()
        .isEqualTo(
            "Account 'foo' only matches inactive accounts. To use an inactive account, retry"
                + " with one of the following exact account IDs:\n"
                + "1: Anonymous Name (1)\n"
                + "3: Anonymous Name (3)");
  }

  private Result search(
      String input,
      ImmutableList<Searcher<?>> searchers,
      Supplier<Predicate<AccountState>> visibilitySupplier)
      throws Exception {
    return newAccountResolver().searchImpl(input, searchers, visibilitySupplier);
  }

  private static AccountResolver newAccountResolver() {
    return new AccountResolver(null, null, null, null, null, null, null, "Anonymous Name");
  }

  private AccountState newAccount(int id) {
    return AccountState.forAccount(
        new AllUsersName("All-Users"), new Account(Account.id(id), TimeUtil.nowTs()));
  }

  private AccountState newInactiveAccount(int id) {
    Account a = new Account(Account.id(id), TimeUtil.nowTs());
    a.setActive(false);
    return AccountState.forAccount(new AllUsersName("All-Users"), a);
  }

  private static ImmutableSet<Account.Id> ids(int... ids) {
    return Arrays.stream(ids).mapToObj(Account::id).collect(toImmutableSet());
  }

  private static Supplier<Predicate<AccountState>> allVisible() {
    return () -> a -> true;
  }

  private static Supplier<Predicate<AccountState>> only(int... ids) {
    ImmutableSet<Account.Id> idSet =
        Arrays.stream(ids).mapToObj(Account::id).collect(toImmutableSet());
    return () -> a -> idSet.contains(a.getAccount().getId());
  }

  private static ImmutableSet<Account.Id> filteredInactiveIds(Result result) {
    return result.filteredInactive().stream()
        .map(a -> a.getAccount().getId())
        .collect(toImmutableSet());
  }
}
