// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.testutil;

import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class FakeAuditService implements AuditService {

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(AuditService.class).to(FakeAuditService.class);
    }
  }

  public List<AuditEvent> auditEvents = new ArrayList<>();

  public void clearEvents() {
    auditEvents.clear();
  }

  @Override
  public void dispatch(AuditEvent action) {
    auditEvents.add(action);
  }

  @Override
  public void dispatchAddAccountsToGroup(Account.Id actor, Collection<AccountGroupMember> added) {}

  @Override
  public void dispatchDeleteAccountsFromGroup(
      Account.Id actor, Collection<AccountGroupMember> removed) {}

  @Override
  public void dispatchAddGroupsToGroup(Account.Id actor, Collection<AccountGroupById> added) {}

  @Override
  public void dispatchDeleteGroupsFromGroup(
      Account.Id actor, Collection<AccountGroupById> removed) {}
}
