package com.google.gerrit.audit;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import java.util.Collection;

public interface AuditService {
  void dispatch(AuditEvent action);

  void dispatchAddAccountsToGroup(Account.Id actor, Collection<AccountGroupMember> added);

  void dispatchDeleteAccountsFromGroup(Account.Id actor, Collection<AccountGroupMember> removed);

  void dispatchAddGroupsToGroup(Account.Id actor, Collection<AccountGroupById> added);

  void dispatchDeleteGroupsFromGroup(Account.Id actor, Collection<AccountGroupById> removed);
}
