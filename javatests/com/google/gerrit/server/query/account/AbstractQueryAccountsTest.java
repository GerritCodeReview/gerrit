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
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.accounts.Accounts.QueryRequest;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.InternalAccountUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testing.GerritServerTests;
import com.google.gerrit.testing.InMemoryDatabase;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractQueryAccountsTest extends GerritServerTests {
  @Inject protected Accounts accounts;

  @Inject @ServerInitiated protected Provider<AccountsUpdate> accountsUpdate;

  @Inject protected AccountCache accountCache;

  @Inject protected AccountIndexer accountIndexer;

  @Inject protected AccountManager accountManager;

  @Inject protected GerritApi gApi;

  @Inject @GerritPersonIdent Provider<PersonIdent> serverIdent;

  @Inject protected IdentifiedUser.GenericFactory userFactory;

  @Inject private Provider<AnonymousUser> anonymousUser;

  @Inject protected InMemoryDatabase schemaFactory;

  @Inject protected SchemaCreator schemaCreator;

  @Inject protected ThreadLocalRequestContext requestContext;

  @Inject protected OneOffRequestContext oneOffRequestContext;

  @Inject protected Provider<InternalAccountQuery> queryProvider;

  @Inject protected AllProjectsName allProjects;

  @Inject protected AllUsersName allUsers;

  @Inject protected GitRepositoryManager repoManager;

  @Inject protected AccountIndexCollection indexes;

  @Inject protected ExternalIds externalIds;

  protected LifecycleManager lifecycle;
  protected Injector injector;
  protected ReviewDb db;
  protected AccountInfo currentUserInfo;
  protected CurrentUser admin;

  protected abstract Injector createInjector();

  @Before
  public void setUpInjector() throws Exception {
    lifecycle = new LifecycleManager();
    injector = createInjector();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();
    setUpDatabase();
  }

  @After
  public void cleanUp() {
    lifecycle.stop();
    db.close();
  }

  protected void setUpDatabase() throws Exception {
    db = schemaFactory.open();
    schemaCreator.create(db);

    Account.Id adminId = createAccount("admin", "Administrator", "admin@example.com", true);
    admin = userFactory.create(adminId);
    requestContext.setContext(newRequestContext(adminId));
    currentUserInfo = gApi.accounts().id(adminId.get()).get();
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
  public void bySecondaryEmail() throws Exception {
    String prefix = name("secondary");
    String domain = name("test.com");
    String secondaryEmail = prefix + "@" + domain;
    AccountInfo user1 = newAccountWithEmail("user1", name("user1@example.com"));
    addEmails(user1, secondaryEmail);

    AccountInfo user2 = newAccountWithEmail("user2", name("user2@example.com"));
    addEmails(user2, name("other@" + domain));

    assertQuery(secondaryEmail, user1);
    assertQuery("email:" + secondaryEmail, user1);
    assertQuery("email:" + prefix, user1);
    assertQuery(domain, user1, user2);
  }

  @Test
  public void byEmailWithoutModifyAccountCapability() throws Exception {
    String preferredEmail = name("primary@test.com");
    String secondaryEmail = name("secondary@test.com");
    AccountInfo user1 = newAccountWithEmail("user1", preferredEmail);
    addEmails(user1, secondaryEmail);

    AccountInfo user2 = newAccount("user");
    requestContext.setContext(newRequestContext(new Account.Id(user2._accountId)));

    if (getSchemaVersion() < 5) {
      assertMissingField(AccountField.PREFERRED_EMAIL);
      assertFailingQuery("email:foo", "'email' operator is not supported by account index version");
      return;
    }

    // This at least needs the PREFERRED_EMAIL field which is available from schema version 5.
    if (getSchemaVersion() >= 5) {
      assertQuery(preferredEmail, user1);
    } else {
      assertQuery(preferredEmail);
    }

    assertQuery(secondaryEmail);

    assertQuery("email:" + preferredEmail, user1);
    assertQuery("email:" + secondaryEmail);
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
    assertQuery("me", currentUserInfo);
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
  public void byNameWithoutModifyAccountCapability() throws Exception {
    AccountInfo user1 = newAccountWithFullName("jdoe", "John Doe");
    AccountInfo user2 = newAccountWithFullName("jroe", "Jane Roe");

    AccountInfo user3 = newAccount("user");
    requestContext.setContext(newRequestContext(new Account.Id(user3._accountId)));

    assertQuery("notexisting");
    assertQuery("Not Existing");

    // by full name works with any index version
    assertQuery(quote(user1.name), user1);
    assertQuery("name:" + quote(user1.name), user1);
    assertQuery(quote(user2.name), user2);
    assertQuery("name:" + quote(user2.name), user2);

    // by self/me works with any index version
    assertQuery("self", user3);
    assertQuery("me", user3);

    if (getSchemaVersion() < 8) {
      assertMissingField(AccountField.NAME_PART_NO_SECONDARY_EMAIL);

      // prefix queries only work if the NAME_PART_NO_SECONDARY_EMAIL field is available
      assertQuery("john");
      return;
    }

    assertQuery("John", user1);
    assertQuery("john", user1);
    assertQuery("Doe", user1);
    assertQuery("doe", user1);
    assertQuery("DOE", user1);
    assertQuery("Jo Do", user1);
    assertQuery("jo do", user1);
    assertQuery("name:John", user1);
    assertQuery("name:john", user1);
    assertQuery("name:Doe", user1);
    assertQuery("name:doe", user1);
    assertQuery("name:DOE", user1);
  }

  @Test
  public void byCansee() throws Exception {
    String domain = name("test.com");
    AccountInfo user1 = newAccountWithEmail("account1", "account1@" + domain);
    AccountInfo user2 = newAccountWithEmail("account2", "account2@" + domain);
    AccountInfo user3 = newAccountWithEmail("account3", "account3@" + domain);

    Project.NameKey p = createProject(name("p"));
    ChangeInfo c = createChange(p);
    assertQuery("name:" + domain + " cansee:" + c.changeId, user1, user2, user3);

    GroupInfo group = createGroup(name("group"), user1, user2);
    blockRead(p, group);
    assertQuery("name:" + domain + " cansee:" + c.changeId, user3);
  }

  @Test
  public void byWatchedProject() throws Exception {
    Project.NameKey p = createProject(name("p"));
    Project.NameKey p2 = createProject(name("p2"));
    AccountInfo user1 = newAccountWithFullName("jdoe", "John Doe");
    AccountInfo user2 = newAccountWithFullName("jroe", "Jane Roe");
    AccountInfo user3 = newAccountWithFullName("user3", "Mr Selfish");

    assertThat(queryProvider.get().byWatchedProject(p)).isEmpty();

    watch(user1, p, null);
    assertAccounts(queryProvider.get().byWatchedProject(p), user1);

    watch(user2, p, "keyword");
    assertAccounts(queryProvider.get().byWatchedProject(p), user1, user2);

    watch(user3, p2, "keyword");
    watch(user3, allProjects, "keyword");
    assertAccounts(queryProvider.get().byWatchedProject(p), user1, user2);
    assertAccounts(queryProvider.get().byWatchedProject(p2), user3);
    assertAccounts(queryProvider.get().byWatchedProject(allProjects), user3);
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

    result = assertQuery(newQuery(user1.username).withSuggest(true), user1);
    assertThat(result.get(0).secondaryEmails)
        .containsExactlyElementsIn(Arrays.asList(secondaryEmails))
        .inOrder();

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
  public void withSecondaryEmailsWithoutModifyAccountCapability() throws Exception {
    AccountInfo user = newAccount("myuser", "My User", "abc@example.com", true);
    String[] secondaryEmails = new String[] {"dfg@example.com", "hij@example.com"};
    addEmails(user, secondaryEmails);

    requestContext.setContext(newRequestContext(new Account.Id(user._accountId)));

    List<AccountInfo> result = newQuery(user.username).withSuggest(true).get();
    assertThat(result.get(0).secondaryEmails).isNull();

    exception.expect(AuthException.class);
    newQuery(user.username).withOption(ListAccountsOption.ALL_EMAILS).get();
  }

  @Test
  public void asAnonymous() throws Exception {
    AccountInfo user1 = newAccount("user1");

    setAnonymous();
    assertQuery("9999999");
    assertQuery("self");
    assertQuery("username:" + user1.username, user1);
  }

  // reindex permissions are tested by {@link AccountIT#reindexPermissions}
  @Test
  public void reindex() throws Exception {
    AccountInfo user1 = newAccountWithFullName("tester", "Test Usre");

    // update account without reindex so that account index is stale
    Account.Id accountId = new Account.Id(user1._accountId);
    String newName = "Test User";
    try (Repository repo = repoManager.openRepository(allUsers)) {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsers, repo);
      PersonIdent ident = serverIdent.get();
      md.getCommitBuilder().setAuthor(ident);
      md.getCommitBuilder().setCommitter(ident);
      new AccountConfig(accountId, repo)
          .load()
          .setAccountUpdate(InternalAccountUpdate.builder().setFullName(newName).build())
          .commit(md);
    }

    assertQuery("name:" + quote(user1.name), user1);
    assertQuery("name:" + quote(newName));

    gApi.accounts().id(user1.username).index();
    assertQuery("name:" + quote(user1.name));
    assertQuery("name:" + quote(newName), user1);
  }

  @Test
  public void rawDocument() throws Exception {
    AccountInfo userInfo = gApi.accounts().id(admin.getAccountId().get()).get();

    Optional<FieldBundle> rawFields =
        indexes
            .getSearchIndex()
            .getRaw(
                new Account.Id(userInfo._accountId),
                QueryOptions.create(
                    IndexConfig.createDefault(),
                    0,
                    1,
                    indexes.getSearchIndex().getSchema().getStoredFields().keySet()));

    assertThat(rawFields).isPresent();
    assertThat(rawFields.get().getValue(AccountField.ID)).isEqualTo(userInfo._accountId);

    // The field EXTERNAL_ID_STATE is only supported from schema version 6.
    if (getSchemaVersion() < 6) {
      return;
    }

    List<AccountExternalIdInfo> externalIdInfos = gApi.accounts().self().getExternalIds();
    List<ByteArrayWrapper> blobs = new ArrayList<>();
    for (AccountExternalIdInfo info : externalIdInfos) {
      blobs.add(
          new ByteArrayWrapper(externalIds.get(ExternalId.Key.parse(info.identity)).toByteArray()));
    }
    assertThat(rawFields.get().getValue(AccountField.EXTERNAL_ID_STATE)).hasSize(blobs.size());
    assertThat(
            Streams.stream(rawFields.get().getValue(AccountField.EXTERNAL_ID_STATE))
                .map(b -> new ByteArrayWrapper(b))
                .collect(toList()))
        .containsExactlyElementsIn(blobs);
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

  protected Project.NameKey createProject(String name) throws RestApiException {
    ProjectInput in = new ProjectInput();
    in.name = name;
    in.createEmptyCommit = true;
    gApi.projects().create(in);
    return new Project.NameKey(name);
  }

  protected void blockRead(Project.NameKey project, GroupInfo group) throws RestApiException {
    ProjectAccessInput in = new ProjectAccessInput();
    in.add = new HashMap<>();

    AccessSectionInfo a = new AccessSectionInfo();
    PermissionInfo p = new PermissionInfo(null, null, null);
    p.rules =
        ImmutableMap.of(group.id, new PermissionRuleInfo(PermissionRuleInfo.Action.BLOCK, false));
    a.permissions = ImmutableMap.of("read", p);
    in.add = ImmutableMap.of("refs/*", a);

    gApi.projects().name(project.get()).access(in);
  }

  protected ChangeInfo createChange(Project.NameKey project) throws RestApiException {
    ChangeInput in = new ChangeInput();
    in.subject = "A change";
    in.project = project.get();
    in.branch = "master";
    return gApi.changes().create(in).get();
  }

  protected GroupInfo createGroup(String name, AccountInfo... members) throws RestApiException {
    GroupInput in = new GroupInput();
    in.name = name;
    in.members =
        Arrays.asList(members).stream().map(a -> String.valueOf(a._accountId)).collect(toList());
    return gApi.groups().create(in).get();
  }

  protected void watch(AccountInfo account, Project.NameKey project, String filter)
      throws RestApiException {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = project.get();
    pwi.filter = filter;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);
    gApi.accounts().id(account._accountId).setWatchedProjects(projectsToWatch);
  }

  protected String quote(String s) {
    return "\"" + s + "\"";
  }

  protected String name(String name) {
    if (name == null) {
      return null;
    }

    String suffix = getSanitizedMethodName();
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
      accountsUpdate
          .get()
          .update(
              "Update Test Account",
              id,
              u -> {
                u.setFullName(fullName).setPreferredEmail(email).setActive(active);
              });
      return id;
    }
  }

  private void addEmails(AccountInfo account, String... emails) throws Exception {
    Account.Id id = new Account.Id(account._accountId);
    for (String email : emails) {
      accountManager.link(id, AuthRequest.forEmail(email));
    }
    accountCache.evict(id);
    accountIndexer.index(id);
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

  protected void assertAccounts(List<AccountState> accounts, AccountInfo... expectedAccounts) {
    assertThat(accounts.stream().map(a -> a.getAccount().getId().get()).collect(toList()))
        .containsExactlyElementsIn(
            Arrays.asList(expectedAccounts).stream().map(a -> a._accountId).collect(toList()));
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

  protected void assertMissingField(FieldDef<AccountState, ?> field) {
    assertThat(getSchema().hasField(field))
        .named("schema %s has field %s", getSchemaVersion(), field.getName())
        .isFalse();
  }

  protected void assertFailingQuery(String query, String expectedMessage) throws Exception {
    try {
      assertQuery(query);
      fail("expected BadRequestException for query '" + query + "'");
    } catch (BadRequestException e) {
      assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
  }

  protected int getSchemaVersion() {
    return getSchema().getVersion();
  }

  protected Schema<AccountState> getSchema() {
    return indexes.getSearchIndex().getSchema();
  }

  /** Boiler plate code to check two byte arrays for equality */
  private static class ByteArrayWrapper {
    private byte[] arr;

    private ByteArrayWrapper(byte[] arr) {
      this.arr = arr;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ByteArrayWrapper)) {
        return false;
      }
      return Arrays.equals(arr, ((ByteArrayWrapper) other).arr);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(arr);
    }
  }
}
