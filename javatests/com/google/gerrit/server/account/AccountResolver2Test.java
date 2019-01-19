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
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountResolver2.Result;
import com.google.gerrit.server.account.AccountResolver2.Searcher;
import com.google.gerrit.server.account.AccountResolver2.StringSearcher;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.Test;

public class AccountResolver2Test extends GerritBaseTests {
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
      return accounts
          .stream()
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

    Result result = AccountResolver2.searchImpl("foo", searchers, allVisible());
    assertThat(result.input()).isEqualTo("foo");
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1));
    assertThat(result.searcher()).hasValue("foo(1)");

    result = AccountResolver2.searchImpl("bar", searchers, allVisible());
    assertThat(result.input()).isEqualTo("bar");
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(2, 3));
    assertThat(result.searcher()).hasValue("bar(2,3)");

    result = AccountResolver2.searchImpl("baz", searchers, allVisible());
    assertThat(result.input()).isEqualTo("baz");
    assertThat(result.asIdSet()).isEmpty();
    assertThat(result.searcher()).isEmpty();
  }

  @Test
  public void shortCircuit() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(
            new TestSearcher("f.*", true), new TestSearcher("foo|bar", false, newAccount(1)));

    Result result = AccountResolver2.searchImpl("foo", searchers, allVisible());
    assertThat(result.input()).isEqualTo("foo");
    assertThat(result.asIdSet()).isEmpty();
    assertThat(result.searcher()).hasValue("f.*()");

    result = AccountResolver2.searchImpl("bar", searchers, allVisible());
    assertThat(result.input()).isEqualTo("bar");
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1));
    assertThat(result.searcher()).hasValue("foo|bar(1)");
  }

  @Test
  public void filterInvisible() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(new TestSearcher("foo", false, newAccount(1), newAccount(2)));

    assertThat(AccountResolver2.searchImpl("foo", searchers, allVisible()).asIdSet())
        .containsExactlyElementsIn(ids(1, 2));
    assertThat(AccountResolver2.searchImpl("foo", searchers, only(2)).asIdSet())
        .containsExactlyElementsIn(ids(2));
  }

  @Test
  public void skipVisibilityCheck() throws Exception {
    TestSearcher searcher = new TestSearcher("foo", false, newAccount(1), newAccount(2));
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher);

    assertThat(AccountResolver2.searchImpl("foo", searchers, only(2)).asIdSet())
        .containsExactlyElementsIn(ids(2));

    searcher.setCallerMayAssumeCandidatesAreVisible();
    assertThat(AccountResolver2.searchImpl("foo", searchers, only(2)).asIdSet())
        .containsExactlyElementsIn(ids(1, 2));
  }

  @Test
  public void filterInactive() throws Exception {
    TestSearcher searcher = new TestSearcher("foo", false, newAccount(1), newInactiveAccount(2));
    ImmutableList<Searcher<?>> searchers = ImmutableList.of(searcher);

    assertThat(AccountResolver2.searchImpl("foo", searchers, allVisible()).asIdSet())
        .containsExactlyElementsIn(ids(1, 2));

    searcher.setCallerShouldFilterOutInactiveCandidates();
    assertThat(AccountResolver2.searchImpl("foo", searchers, allVisible()).asIdSet())
        .containsExactlyElementsIn(ids(1));
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
    Result result = AccountResolver2.searchImpl("foo", searchers, allVisible());
    assertThat(result.asIdSet()).containsExactlyElementsIn(ids(1, 2));
    assertThat(result.searcher()).hasValue("foo(1,2)");
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
    Result result = AccountResolver2.searchImpl("foo", searchers, allVisible());
    assertThat(result.asIdSet()).isEmpty();
    assertThat(result.searcher()).hasValue("foo(2)");
  }

  @Test
  public void asUniqueWithNoResults() throws Exception {
    try {
      AccountResolver2.searchImpl("foo", ImmutableList.of(), allVisible()).asUnique();
      assert_().fail("Expected UnprocessableEntityException");
    } catch (UnprocessableEntityException e) {
      assertThat(e).hasMessageThat().isEqualTo("Account 'foo' not found");
    }
  }

  @Test
  public void asUniqueWithOneResult() throws Exception {
    AccountState account = newAccount(1);
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(new TestSearcher("foo", false, account));
    assertThat(
            AccountResolver2.searchImpl("foo", searchers, allVisible())
                .asUnique()
                .getAccount()
                .getId())
        .isEqualTo(account.getAccount().getId());
  }

  @Test
  public void asUniqueWithMultipleResults() throws Exception {
    ImmutableList<Searcher<?>> searchers =
        ImmutableList.of(new TestSearcher("foo", false, newAccount(1), newAccount(2)));
    try {
      AccountResolver2.searchImpl("foo", searchers, allVisible()).asUnique();
      assert_().fail("Expected UnprocessableEntityException");
    } catch (UnprocessableEntityException e) {
      assertThat(e).hasMessageThat().isEqualTo("Account 'foo' is ambiguous");
    }
  }

  private AccountState newAccount(int id) {
    return AccountState.forAccount(
        new AllUsersName("All-Users"), new Account(new Account.Id(id), TimeUtil.nowTs()));
  }

  private AccountState newInactiveAccount(int id) {
    Account a = new Account(new Account.Id(id), TimeUtil.nowTs());
    a.setActive(false);
    return AccountState.forAccount(new AllUsersName("All-Users"), a);
  }

  private static ImmutableSet<Account.Id> ids(int... ids) {
    return Arrays.stream(ids).mapToObj(Account.Id::new).collect(toImmutableSet());
  }

  private static Supplier<Predicate<AccountState>> allVisible() {
    return () -> a -> true;
  }

  private static Supplier<Predicate<AccountState>> only(int... ids) {
    ImmutableSet<Account.Id> idSet =
        Arrays.stream(ids).mapToObj(Account.Id::new).collect(toImmutableSet());
    return () -> a -> idSet.contains(a.getAccount().getId());
  }
}
