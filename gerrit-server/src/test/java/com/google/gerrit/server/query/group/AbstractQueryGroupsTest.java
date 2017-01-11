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

package com.google.gerrit.server.query.group;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.groups.Groups.QueryRequest;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.query.account.InternalAccountQuery;
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

import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Ignore
public abstract class AbstractQueryGroupsTest extends GerritServerTests {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("index", null, "maxPages", 10);
    return cfg;
  }

  @Rule
  public final TestName testName = new TestName();

  @Inject
  protected AccountCache accountCache;

  @Inject
  protected AccountManager accountManager;

  @Inject
  protected GerritApi gApi;

  @Inject
  protected IdentifiedUser.GenericFactory userFactory;

  @Inject
  private Provider<AnonymousUser> anonymousUser;

  @Inject
  protected InMemoryDatabase schemaFactory;

  @Inject
  protected SchemaCreator schemaCreator;

  @Inject
  protected ThreadLocalRequestContext requestContext;

  @Inject
  protected OneOffRequestContext oneOffRequestContext;

  @Inject
  protected InternalAccountQuery internalAccountQuery;

  @Inject
  protected AllProjectsName allProjects;

  @Inject
  protected GroupCache groupCache;

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
    final CurrentUser requestUser =
        userFactory.create(requestUserId);
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
    requestContext.setContext(new RequestContext() {
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
  public void byUuid() throws Exception {
    assertQuery("uuid:6d70856bc40ded50f2585c4c0f7e179f3544a272");
    assertQuery("uuid:non-existing");

    GroupInfo group = createGroup(name("group"));
    assertQuery("uuid:" + group.id, group);
  }

  @Test
  public void withLimit() throws Exception {
    GroupInfo group1 = createGroup(name("group1"));
    GroupInfo group2 = createGroup(name("group2"));
    GroupInfo group3 = createGroup(name("group3"));

    String query =
        "uuid:" + group1.id + " OR uuid:" + group2.id + " OR uuid:" + group3.id;
    List<GroupInfo> result = assertQuery(query, group1, group2, group3);
    assertThat(result.get(result.size() - 1)._moreGroups).isNull();

    result = assertQuery(newQuery(query).withLimit(2), result.subList(0, 2));
    assertThat(result.get(result.size() - 1)._moreGroups).isTrue();
  }

  @Test
  public void withStart() throws Exception {
    GroupInfo group1 = createGroup(name("group1"));
    GroupInfo group2 = createGroup(name("group2"));
    GroupInfo group3 = createGroup(name("group3"));

    String query =
        "uuid:" + group1.id + " OR uuid:" + group2.id + " OR uuid:" + group3.id;
    List<GroupInfo> result = assertQuery(query, group1, group2, group3);

    assertQuery(newQuery(query).withStart(1), result.subList(1, 3));
  }

  @Test
  public void asAnonymous() throws Exception {
    GroupInfo group = createGroup(name("group"));

    setAnonymous();
    assertQuery("uuid:" + group.id);
  }

  private Account.Id createAccount(String username, String fullName,
      String email, boolean active) throws Exception {
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      Account.Id id =
          accountManager.authenticate(AuthRequest.forUser(username)).getAccountId();
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

  protected GroupInfo createGroup(String name, AccountInfo... members)
      throws Exception {
    GroupInput in = new GroupInput();
    in.name = name;
    in.members = Arrays.asList(members).stream()
        .map(a -> String.valueOf(a._accountId)).collect(toList());
    return gApi.groups().create(in).get();
  }

  protected GroupInfo getGroup(AccountGroup.UUID uuid) throws Exception {
    return gApi.groups().id(uuid.get()).get();
  }

  protected List<GroupInfo> assertQuery(Object query, GroupInfo... groups)
      throws Exception {
    return assertQuery(newQuery(query), groups);
  }

  protected List<GroupInfo> assertQuery(QueryRequest query,
      GroupInfo... groups) throws Exception {
    return assertQuery(query, Arrays.asList(groups));
  }

  protected List<GroupInfo> assertQuery(QueryRequest query,
      List<GroupInfo> groups) throws Exception {
    List<GroupInfo> result = query.get();
    Iterable<String> uuids = uuids(result);
    assertThat(uuids).named(format(query, result, groups))
        .containsExactlyElementsIn(uuids(groups));
    return result;
  }

  protected QueryRequest newQuery(Object query) {
    return gApi.groups().query(query.toString());
  }

  protected String format(QueryRequest query, List<GroupInfo> actualGroups,
      List<GroupInfo> expectedGroups) {
    StringBuilder b = new StringBuilder();
    b.append("query '").append(query.getQuery())
        .append("' with expected groups ");
    b.append(format(expectedGroups));
    b.append(" and result ");
    b.append(format(actualGroups));
    return b.toString();
  }

  protected String format(Iterable<GroupInfo> groups) {
    StringBuilder b = new StringBuilder();
    b.append("[");
    Iterator<GroupInfo> it = groups.iterator();
    while (it.hasNext()) {
      GroupInfo g = it.next();
      b.append("{").append(g.id).append(", ").append("name=").append(g.name)
          .append(", ").append("groupId=").append(g.groupId).append(", ")
          .append("url=").append(g.url).append(", ").append("ownerId=")
          .append(g.ownerId).append(", ").append("owner=").append(g.owner)
          .append(", ").append("description=").append(g.description)
          .append(", ").append("visibleToAll=")
          .append(toBoolean(g.options.visibleToAll)).append("}");
      if (it.hasNext()) {
        b.append(", ");
      }
    }
    b.append("]");
    return b.toString();
  }

  protected static boolean toBoolean(Boolean b) {
    return b == null ? false : b;
  }

  protected static Iterable<String> ids(GroupInfo... groups) {
    return uuids(Arrays.asList(groups));
  }

  protected static Iterable<String> uuids(List<GroupInfo> groups) {
    return groups.stream().map(g -> g.id).collect(toList());
  }

  protected String name(String name) {
    if (name == null) {
      return null;
    }
    return name + "_" + testName.getMethodName().toLowerCase();
  }
}
