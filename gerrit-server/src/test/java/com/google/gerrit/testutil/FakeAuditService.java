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
