// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.client.admin.AccountGroupDetail;
import com.google.gerrit.client.admin.GroupAdminService;
import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NameAlreadyUsedException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupAdminServiceImpl extends BaseServiceImplementation implements
    GroupAdminService {
  public void ownedGroups(final AsyncCallback<List<AccountGroup>> callback) {
    run(callback, new Action<List<AccountGroup>>() {
      public List<AccountGroup> run(ReviewDb db) throws OrmException {
        final List<AccountGroup> result;
        if (Common.getGroupCache().isAdministrator(Common.getAccountId())) {
          result = db.accountGroups().all().toList();
        } else {
          result = myOwnedGroups(db);
          Collections.sort(result, new Comparator<AccountGroup>() {
            public int compare(final AccountGroup a, final AccountGroup b) {
              return a.getName().compareTo(b.getName());
            }
          });
        }
        return result;
      }
    });
  }

  public void createGroup(final String newName,
      final AsyncCallback<AccountGroup.Id> callback) {
    run(callback, new Action<AccountGroup.Id>() {
      public AccountGroup.Id run(final ReviewDb db) throws OrmException,
          Failure {
        final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(newName);
        if (db.accountGroups().get(nameKey) != null) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final AccountGroup group =
            new AccountGroup(nameKey, new AccountGroup.Id(db
                .nextAccountGroupId()));
        group.setNameKey(nameKey);
        group.setDescription("");

        final Account.Id me = Common.getAccountId();
        final AccountGroupMember m =
            new AccountGroupMember(
                new AccountGroupMember.Key(me, group.getId()));

        final Transaction txn = db.beginTransaction();
        db.accountGroups().insert(Collections.singleton(group), txn);
        db.accountGroupMembers().insert(Collections.singleton(m), txn);
        db.accountGroupMembersAudit().insert(
            Collections.singleton(new AccountGroupMemberAudit(m, me)), txn);
        txn.commit();
        Common.getGroupCache().notifyGroupAdd(m);

        return group.getId();
      }
    });
  }

  public void groupDetail(final AccountGroup.Id groupId,
      final AsyncCallback<AccountGroupDetail> callback) {
    run(callback, new Action<AccountGroupDetail>() {
      public AccountGroupDetail run(ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);

        final AccountGroupDetail d = new AccountGroupDetail();
        final boolean auto = Common.getGroupCache().isAutoGroup(group.getId());
        d.load(db, new AccountInfoCacheFactory(db), group, auto);
        return d;
      }
    });
  }

  public void changeGroupDescription(final AccountGroup.Id groupId,
      final String description, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);
        group.setDescription(description);
        db.accountGroups().update(Collections.singleton(group));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void changeGroupOwner(final AccountGroup.Id groupId,
      final String newOwnerName, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);

        final AccountGroup owner =
            db.accountGroups().get(new AccountGroup.NameKey(newOwnerName));
        if (owner == null) {
          throw new Failure(new NoSuchEntityException());
        }

        group.setOwnerGroupId(owner.getId());
        db.accountGroups().update(Collections.singleton(group));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void renameGroup(final AccountGroup.Id groupId, final String newName,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);

        final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(newName);
        if (!nameKey.equals(group.getNameKey())) {
          if (db.accountGroups().get(nameKey) != null) {
            throw new Failure(new NameAlreadyUsedException());
          }
          group.setNameKey(nameKey);
          db.accountGroups().update(Collections.singleton(group));
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void addGroupMember(final AccountGroup.Id groupId,
      final String nameOrEmail, final AsyncCallback<AccountGroupDetail> callback) {
    run(callback, new Action<AccountGroupDetail>() {
      public AccountGroupDetail run(ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);
        if (group.isAutomaticMembership()
            || Common.getGroupCache().isAutoGroup(groupId)) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final Account a = findAccount(db, nameOrEmail);
        final AccountGroupMember.Key key =
            new AccountGroupMember.Key(a.getId(), groupId);
        if (db.accountGroupMembers().get(key) != null) {
          return new AccountGroupDetail();
        }

        final AccountGroupMember m = new AccountGroupMember(key);
        final Transaction txn = db.beginTransaction();
        db.accountGroupMembers().insert(Collections.singleton(m), txn);
        db.accountGroupMembersAudit().insert(
            Collections.singleton(new AccountGroupMemberAudit(m, Common
                .getAccountId())), txn);
        txn.commit();
        Common.getGroupCache().notifyGroupAdd(m);

        final AccountGroupDetail d = new AccountGroupDetail();
        d.loadOneMember(db, a, m);
        return d;
      }
    });
  }

  public void deleteGroupMembers(final Set<AccountGroupMember.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = Common.getAccountId();
        final Set<AccountGroup.Id> owned = ids(myOwnedGroups(db));
        Boolean amAdmin = null;
        for (final AccountGroupMember.Key k : keys) {
          if (!owned.contains(k.getAccountGroupId())) {
            if (amAdmin == null) {
              amAdmin = Common.getGroupCache().isAdministrator(me);
            }
            if (!amAdmin) {
              throw new Failure(new NoSuchEntityException());
            }
          }
        }
        for (final AccountGroupMember.Key k : keys) {
          final AccountGroupMember m = db.accountGroupMembers().get(k);
          if (m != null) {
            AccountGroupMemberAudit audit = null;
            for (AccountGroupMemberAudit a : db.accountGroupMembersAudit()
                .byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
              if (a.isActive()) {
                audit = a;
                break;
              }
            }

            final Transaction txn = db.beginTransaction();
            db.accountGroupMembers().delete(Collections.singleton(m), txn);
            if (audit != null) {
              audit.removed(me);
              db.accountGroupMembersAudit().update(
                  Collections.singleton(audit), txn);
            } else {
              audit = new AccountGroupMemberAudit(m, me);
              audit.removedLegacy();
              db.accountGroupMembersAudit().insert(
                  Collections.singleton(audit), txn);
            }
            txn.commit();
            Common.getGroupCache().notifyGroupDelete(m);
          }
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  private void assertAmGroupOwner(final ReviewDb db, final AccountGroup group)
      throws Failure {
    if (group == null) {
      throw new Failure(new NoSuchEntityException());
    }
    final Account.Id me = Common.getAccountId();
    if (!Common.getGroupCache().isInGroup(me, group.getOwnerGroupId())
        && !Common.getGroupCache().isAdministrator(me)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  private static Set<AccountGroup.Id> ids(
      final Collection<AccountGroup> groupList) {
    final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>();
    for (final AccountGroup group : groupList) {
      r.add(group.getId());
    }
    return r;
  }

  private List<AccountGroup> myOwnedGroups(final ReviewDb db)
      throws OrmException {
    final Account.Id me = Common.getAccountId();
    final List<AccountGroup> own = new ArrayList<AccountGroup>();
    for (final AccountGroup.Id groupId : Common.getGroupCache()
        .getEffectiveGroups(me)) {
      for (final AccountGroup g : db.accountGroups().ownedByGroup(groupId)) {
        own.add(g);
      }
    }
    return own;
  }

  private static Account findAccount(final ReviewDb db, final String nameOrEmail)
      throws OrmException, Failure {
    final Account r = Account.find(db, nameOrEmail);
    if (r == null) {
      throw new Failure(new NoSuchEntityException());
    }
    return r;
  }
}
