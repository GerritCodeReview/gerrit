package com.google.gerrit.server.group;

import com.google.common.collect.Lists;
import com.google.gerrit.audit.GroupMemberAuditListener;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class DbGroupMemberAuditListener implements GroupMemberAuditListener {

  private ReviewDb db;

  @Inject
  public DbGroupMemberAuditListener(ReviewDb db) {
    this.db = db;
  }

  @Override
  public void onAddMembers(Id me, Collection<AccountGroupMember> toBeAdded)
      throws Exception {
    final List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    for(AccountGroupMember m : toBeAdded) {
      AccountGroupMemberAudit audit = new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
      auditInserts.add(audit);
    }
    try {
      db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      throw new IOException("Cannot log add group member event", e);
    }
  }

  @Override
  public void onDeleteMembers(Id me, Collection<AccountGroupMember> toBeRemoved) throws IOException {
    try {
    final List<AccountGroupMemberAudit> auditUpdates = Lists.newLinkedList();
    final List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    for (final AccountGroupMember m : toBeRemoved) {
      AccountGroupMemberAudit audit = null;
      for (AccountGroupMemberAudit a : db.accountGroupMembersAudit()
          .byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
        if (a.isActive()) {
          audit = a;
          break;
        }
      }

      if (audit != null) {
        audit.removed(me, TimeUtil.nowTs());
        auditUpdates.add(audit);
      } else {
        audit = new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
        audit.removedLegacy();
        auditInserts.add(audit);
      }
    }
    db.accountGroupMembersAudit().update(auditUpdates);
    db.accountGroupMembersAudit().insert(auditInserts);
    } catch (OrmException e) {
      throw new IOException("Cannot log delete group member event", e);
    }
  }

}
