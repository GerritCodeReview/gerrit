// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts.QueryRequest;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.GerritServerTests;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

@Ignore
public abstract class AbstractQueryAccountsTest extends GerritServerTests {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxPages", 10);
    return cfg;
  }

  @Rule public final TestName testName = new TestName();

  @Inject protected AccountCache accountCache;

  @Inject protected AccountManager accountManager;

  @Inject protected GerritApi gApi;

  @Inject protected IdentifiedUser.GenericFactory userFactory;

  @Inject private Provider<AnonymousUser> anonymousUser;

  @Inject protected InMemoryDatabase schemaFactory;

  @Inject protected InternalChangeQuery internalChangeQuery;

  @Inject protected SchemaCreator schemaCreator;

  @Inject protected ThreadLocalRequestContext requestContext;

  @Inject protected OneOffRequestContext oneOffRequestContext;

  protected LifecycleManager lifecycle;
  protected ReviewDb db;
  protected AccountInfo currentUserInfo;
  protected CurrentUser user;

  protected abstract Injector createInjector();

  @Before
  public void setUpInjector() throws Exception {
    lifecycle = new LifecycleManager();
    Injector injector = createInjector();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);

    Account.Id userId = createAccount("user", "User", "user@example.com", true);
    user = userFactory.create(userId);
    requestContext.setContext(newRequestContext(userId));
    currentUserInfo = gApi.accounts().id(userId.get()).get();
  }

  protected RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser = userFactory.create(requestUserId);
    return new RequestContext() {
      @Override
      public CurrentUser getUser() {
        return requestUser;
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    };
  }

  protected void setAnonymous() {
    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return anonymousUser.get();
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });
  }

  @After
  public void tearDownInjector() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void byId() throws Exception {
    AccountInfo user = newAccount("user");

    assertQuery("9999999");
    assertQuery(currentUserInfo._accountId, currentUserInfo);
    assertQuery(user._accountId, user);
  }

  @Test
  public void bySelf() throws Exception {
    assertQuery("self", currentUserInfo);
  }

  @Test
  public void byEmail() throws Exception {
    AccountInfo user1 = newAccountWithEmail("user1", name("user1@example.com"));

    String domain = name("test.com");
    AccountInfo user2 = newAccountWithEmail("user2", "user2@" + domain);
    AccountInfo user3 = newAccountWithEmail("user3", "user3@" + domain);

    String prefix = name("prefix");
    AccountInfo user4 = newAccountWithEmail("user4", prefix + "user4@example.com");

    AccountInfo user5 = newAccountWithEmail("user5", name("user5MixedCase@example.com"));

    assertQuery("notexisting@test.com");

    assertQuery(currentUserInfo.email, currentUserInfo);
    assertQuery("email:" + currentUserInfo.email, currentUserInfo);

    assertQuery(user1.email, user1);
    assertQuery("email:" + user1.email, user1);

    assertQuery(domain, user2, user3);

    assertQuery("email:" + prefix, user4);

    assertQuery(user5.email, user5);
    assertQuery("email:" + user5.email, user5);
    assertQuery("email:" + user5.email.toUpperCase(), user5);
  }

  @Test
  public void byUsername() throws Exception {
    AccountInfo user1 = newAccount("myuser");

    assertQuery("notexisting");
    assertQuery("Not Existing");

    assertQuery(user1.username, user1);
    assertQuery("username:" + user1.username, user1);
    assertQuery("username:" + user1.username.toUpperCase(), user1);
  }

  @Test
  public void isActive() throws Exception {
    String domain = name("test.com");
    AccountInfo user1 = newAccountWithEmail("user1", "user1@" + domain);
    AccountInfo user2 = newAccountWithEmail("user2", "user2@" + domain);
    AccountInfo user3 = newAccount("user3", "user3@" + domain, false);
    AccountInfo user4 = newAccount("user4", "user4@" + domain, false);

    // by default only active accounts are returned
    assertQuery(domain, user1, user2);
    assertQuery("name:" + domain, user1, user2);

    assertQuery("is:active name:" + domain, user1, user2);

    assertQuery("is:inactive name:" + domain, user3, user4);
  }

  @Test
  public void byName() throws Exception {
    AccountInfo user1 = newAccountWithFullName("jdoe", "John Doe");
    AccountInfo user2 = newAccountWithFullName("jroe", "Jane Roe");
    AccountInfo user3 = newAccountWithFullName("user3", "Mr Selfish");

    assertQuery("notexisting");
    assertQuery("Not Existing");

    assertQuery(quote(user1.name), user1);
    assertQuery("name:" + quote(user1.name), user1);
    assertQuery("John", user1);
    assertQuery("john", user1);
    assertQuery("Doe", user1);
    assertQuery("doe", user1);
    assertQuery("DOE", user1);
    assertQuery("Jo Do", user1);
    assertQuery("jo do", user1);
    assertQuery("self", currentUserInfo, user3);
    assertQuery("name:John", user1);
    assertQuery("name:john", user1);
    assertQuery("name:Doe", user1);
    assertQuery("name:doe", user1);
    assertQuery("name:DOE", user1);
    assertQuery("name:self", user3);

    assertQuery(quote(user2.name), user2);
    assertQuery("name:" + quote(user2.name), user2);
  }

  @Test
  public void withLimit() throws Exception {
    String domain = name("test.com");
    AccountInfo user1 = newAccountWithEmail("user1", "user1@" + domain);
    AccountInfo user2 = newAccountWithEmail("user2", "user2@" + domain);
    AccountInfo user3 = newAccountWithEmail("user3", "user3@" + domain);

    List<AccountInfo> result = assertQuery(domain, user1, user2, user3);
    assertThat(Iterables.getLast(result)._moreAccounts).isNull();

    result = assertQuery(newQuery(domain).withLimit(2), result.subList(0, 2));
    assertThat(Iterables.getLast(result)._moreAccounts).isTrue();
  }

  @Test
  public void withStart() throws Exception {
    String domain = name("test.com");
    AccountInfo user1 = newAccountWithEmail("user1", "user1@" + domain);
    AccountInfo user2 = newAccountWithEmail("user2", "user2@" + domain);
    AccountInfo user3 = newAccountWithEmail("user3", "user3@" + domain);

    List<AccountInfo> result = assertQuery(domain, user1, user2, user3);
    assertQuery(newQuery(domain).withStart(1), result.subList(1, 3));
  }

  @Test
  public void withDetails() throws Exception {
    AccountInfo user1 = newAccount("myuser", "My User", "my.user@example.com", true);

    List<AccountInfo> result = assertQuery(user1.username, user1);
    AccountInfo ai = result.get(0);
    assertThat(ai._accountId).isEqualTo(user1._accountId);
    assertThat(ai.name).isNull();
    assertThat(ai.username).isNull();
    assertThat(ai.email).isNull();
    assertThat(ai.avatars).isNull();

    result = assertQuery(newQuery(user1.username).withOption(ListAccountsOption.DETAILS), user1);
    ai = result.get(0);
    assertThat(ai._accountId).isEqualTo(user1._accountId);
    assertThat(ai.name).isEqualTo(user1.name);
    assertThat(ai.username).isEqualTo(user1.username);
    assertThat(ai.email).isEqualTo(user1.email);
    assertThat(ai.avatars).isNull();
  }

  @Test
  public void withSecondaryEmails() throws Exception {
    AccountInfo user1 = newAccount("myuser", "My User", "my.user@example.com", true);
    String[] secondaryEmails = new String[] {"bar@example.com", "foo@example.com"};
    addEmails(user1, secondaryEmails);

    List<AccountInfo> result = assertQuery(user1.username, user1);
    assertThat(result.get(0).secondaryEmails).isNull();

    result = assertQuery(newQuery(user1.username).withOption(ListAccountsOption.DETAILS), user1);
    assertThat(result.get(0).secondaryEmails).isNull();

    result = assertQuery(newQuery(user1.username).withOption(ListAccountsOption.ALL_EMAILS), user1);
    assertThat(result.get(0).secondaryEmails)
        .containsExactlyElementsIn(Arrays.asList(secondaryEmails))
        .inOrder();

    result =
        assertQuery(
            newQuery(user1.username)
                .withOptions(ListAccountsOption.DETAILS, ListAccountsOption.ALL_EMAILS),
            user1);
    assertThat(result.get(0).secondaryEmails)
        .containsExactlyElementsIn(Arrays.asList(secondaryEmails))
        .inOrder();
  }

  @Test
  public void asAnonymous() throws Exception {
    AccountInfo user1 = newAccount("user1");

    setAnonymous();
    assertQuery("9999999");
    assertQuery("self");
    assertQuery("username:" + user1.username, user1);
  }

  protected AccountInfo newAccount(String username) throws Exception {
    return newAccountWithEmail(username, null);
  }

  protected AccountInfo newAccountWithEmail(String username, String email) throws Exception {
    return newAccount(username, email, true);
  }

  protected AccountInfo newAccountWithFullName(String username, String fullName) throws Exception {
    return newAccount(username, fullName, null, true);
  }

  protected AccountInfo newAccount(String username, String email, boolean active) throws Exception {
    return newAccount(username, null, email, active);
  }

  protected AccountInfo newAccount(String username, String fullName, String email, boolean active)
      throws Exception {
    String uniqueName = name(username);

    try {
      gApi.accounts().id(uniqueName).get();
      fail("user " + uniqueName + " already exists");
    } catch (ResourceNotFoundException e) {
      // expected: user does not exist yet
    }

    Account.Id id = createAccount(uniqueName, fullName, email, active);
    return gApi.accounts().id(id.get()).get();
  }

  protected String quote(String s) {
    return "\"" + s + "\"";
  }

  protected String name(String name) {
    if (name == null) {
      return null;
    }
    String suffix = testName.getMethodName().toLowerCase();
    if (name.contains("@")) {
      return name + "." + suffix;
    }
    return name + "_" + suffix;
  }

  private Account.Id createAccount(String username, String fullName, String email, boolean active)
      throws Exception {
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      Account.Id id = accountManager.authenticate(AuthRequest.forUser(username)).getAccountId();
      if (email != null) {
        accountManager.link(id, AuthRequest.forEmail(email));
      }
      Account a = db.accounts().get(id);
      a.setFullName(fullName);
      a.setPreferredEmail(email);
      a.setActive(active);
      db.accounts().update(ImmutableList.of(a));
      accountCache.evict(id);
      return id;
    }
  }

  private void addEmails(AccountInfo account, String... emails) throws Exception {
    Account.Id id = new Account.Id(account._accountId);
    for (String email : emails) {
      accountManager.link(id, AuthRequest.forEmail(email));
    }
    accountCache.evict(id);
  }

  protected QueryRequest newQuery(Object query) throws RestApiException {
    return gApi.accounts().query(query.toString());
  }

  protected List<AccountInfo> assertQuery(Object query, AccountInfo... accounts) throws Exception {
    return assertQuery(newQuery(query), accounts);
  }

  protected List<AccountInfo> assertQuery(QueryRequest query, AccountInfo... accounts)
      throws Exception {
    return assertQuery(query, Arrays.asList(accounts));
  }

  protected List<AccountInfo> assertQuery(QueryRequest query, List<AccountInfo> accounts)
      throws Exception {
    List<AccountInfo> result = query.get();
    Iterable<Integer> ids = ids(result);
    assertThat(ids)
        .named(format(query, result, accounts))
        .containsExactlyElementsIn(ids(accounts))
        .inOrder();
    return result;
  }

  private String format(
      QueryRequest query, List<AccountInfo> actualIds, List<AccountInfo> expectedAccounts) {
    StringBuilder b = new StringBuilder();
    b.append("query '").append(query.getQuery()).append("' with expected accounts ");
    b.append(format(expectedAccounts));
    b.append(" and result ");
    b.append(format(actualIds));
    return b.toString();
  }

  private String format(Iterable<AccountInfo> accounts) {
    StringBuilder b = new StringBuilder();
    b.append("[");
    Iterator<AccountInfo> it = accounts.iterator();
    while (it.hasNext()) {
      AccountInfo a = it.next();
      b.append("{")
          .append(a._accountId)
          .append(", ")
          .append("name=")
          .append(a.name)
          .append(", ")
          .append("email=")
          .append(a.email)
          .append(", ")
          .append("username=")
          .append(a.username)
          .append("}");
      if (it.hasNext()) {
        b.append(", ");
      }
    }
    b.append("]");
    return b.toString();
  }

  protected static Iterable<Integer> ids(AccountInfo... accounts) {
    return ids(Arrays.asList(accounts));
  }

  protected static Iterable<Integer> ids(List<AccountInfo> accounts) {
    return accounts.stream().map(a -> a._accountId).collect(toList());
  }
}
