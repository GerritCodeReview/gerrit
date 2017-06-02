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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Schema_150_to_151_Test {
  @Inject private AccountManager accountManager;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private SchemaFactory<ReviewDb> schemaFactory;
  @Inject private SchemaCreator schemaCreator;
  @Inject private ThreadLocalRequestContext requestContext;
  @Inject private Schema_151 schema151;
  @Inject private CreateGroup.Factory createGroupFactory;

  // Only for use in setting up/tearing down injector.
  @Inject private InMemoryDatabase inMemoryDatabase;

  private LifecycleManager lifecycle;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    try (ReviewDb underlyingDb = inMemoryDatabase.getDatabase().open()) {
      schemaCreator.create(underlyingDb);
    }
    db = schemaFactory.open();
    Account.Id userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    IdentifiedUser user = userFactory.create(userId);

    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });
  }

  @After
  public void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(inMemoryDatabase);
  }

  @Test
  public void createdOnIsPopulatedForGroupsCreatedAfterAudit() throws Exception {
    Timestamp testStartTime = TimeUtil.nowTs();
    AccountGroup.Id groupId = createGroup("Group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);

    schema151.migrateData(db, new TestUpdateUI());

    AccountGroup group = db.accountGroups().get(groupId);
    assertThat(group.getCreatedOn()).isAtLeast(testStartTime);
  }

  @Test
  public void createdOnIsPopulatedForGroupsCreatedBeforeAudit() throws Exception {
    AccountGroup.Id groupId = createGroup("Ancient group for schema migration");
    setCreatedOnToVeryOldTimestamp(groupId);
    removeAuditEntriesFor(groupId);

    schema151.migrateData(db, new TestUpdateUI());

    AccountGroup group = db.accountGroups().get(groupId);
    assertThat(group.getCreatedOn()).isEqualTo(AccountGroup.auditCreationInstantTs());
  }

  private AccountGroup.Id createGroup(String name) throws Exception {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name;
    GroupInfo groupInfo =
        createGroupFactory.create(name).apply(TopLevelResource.INSTANCE, groupInput);
    return new Id(groupInfo.groupId);
  }

  private void setCreatedOnToVeryOldTimestamp(Id groupId) throws OrmException {
    AccountGroup group = db.accountGroups().get(groupId);
    Instant instant = LocalDateTime.of(1800, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC);
    group.setCreatedOn(Timestamp.from(instant));
    db.accountGroups().update(ImmutableList.of(group));
  }

  private void removeAuditEntriesFor(AccountGroup.Id groupId) throws Exception {
    ResultSet<AccountGroupMemberAudit> groupMemberAudits =
        db.accountGroupMembersAudit().byGroup(groupId);
    db.accountGroupMembersAudit().delete(groupMemberAudits);
  }

  private static class TestUpdateUI implements UpdateUI {

    @Override
    public void message(String msg) {}

    @Override
    public boolean yesno(boolean def, String msg) {
      return false;
    }

    @Override
    public boolean isBatch() {
      return false;
    }

    @Override
    public void pruneSchema(StatementExecutor e, List<String> pruneList) throws OrmException {}
  }
}
