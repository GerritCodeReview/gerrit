// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.NameAlreadyUsedException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupAdminServiceImpl extends BaseServiceImplementation implements
    GroupAdminService {
  public GroupAdminServiceImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void ownedGroups(final AsyncCallback<List<AccountGroup>> callback) {
    run(callback, new Action<List<AccountGroup>>() {
      public List<AccountGroup> run(ReviewDb db) throws OrmException {
        final List<AccountGroup> result;
        if (amAdmin(db)) {
          result = db.accountGroups().all().toList();
        } else {
          final Set<AccountGroup.Id> mine = myOwnedGroups(db);
          result =
              new ArrayList<AccountGroup>(db.accountGroups().get(mine).toList());
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
        if (nameKey.equals(ADMIN_GROUP)) {
          // Forbid creating the admin group, its highly special because it
          // has near root level access to the server, based upon its name.
          //
          throw new Failure(new NameAlreadyUsedException());
        }

        if (db.accountGroups().get(nameKey) != null) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final AccountGroup group =
            new AccountGroup(nameKey, new AccountGroup.Id(db
                .nextAccountGroupId()));
        group.setNameKey(nameKey);
        group.setDescription("");

        final AccountGroupMember m =
            new AccountGroupMember(new AccountGroupMember.Key(RpcUtil
                .getAccountId(), group.getId()));
        m.setGroupOwner(true);

        final Transaction txn = db.beginTransaction();
        db.accountGroups().insert(Collections.singleton(group), txn);
        db.accountGroupMembers().insert(Collections.singleton(m), txn);
        txn.commit();
        return group.getId();
      }
    });
  }

  public void groupDetail(final AccountGroup.Id groupId,
      final AsyncCallback<AccountGroupDetail> callback) {
    run(callback, new Action<AccountGroupDetail>() {
      public AccountGroupDetail run(ReviewDb db) throws OrmException, Failure {
        assertAmGroupOwner(db, groupId);
        final AccountGroup group = db.accountGroups().get(groupId);
        if (group == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroupDetail d = new AccountGroupDetail();
        d.load(db, new AccountInfoCacheFactory(db), group);
        return d;
      }
    });
  }

  public void changeGroupDescription(final AccountGroup.Id groupId,
      final String description, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        assertAmGroupOwner(db, groupId);
        final AccountGroup group = db.accountGroups().get(groupId);
        if (group == null) {
          throw new Failure(new NoSuchEntityException());
        }
        group.setDescription(description);
        db.accountGroups().update(Collections.singleton(group));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void renameGroup(final AccountGroup.Id groupId, final String newName,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        assertAmGroupOwner(db, groupId);
        final AccountGroup group = db.accountGroups().get(groupId);
        if (group == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(newName);
        if (group.getName().equals(ADMIN_GROUP) || nameKey.equals(ADMIN_GROUP)) {
          // Forbid renaming the admin group, its highly special because it
          // has near root level access to the server, based upon its name.
          //
          throw new Failure(new NameAlreadyUsedException());
        }

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
        assertAmGroupOwner(db, groupId);
        final Account a = findAccount(db, nameOrEmail);
        final AccountGroupMember.Key key =
            new AccountGroupMember.Key(a.getId(), groupId);
        if (db.accountGroupMembers().get(key) != null) {
          return new AccountGroupDetail();
        }

        final AccountGroupMember m = new AccountGroupMember(key);
        db.accountGroupMembers().insert(Collections.singleton(m));
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
        final Set<AccountGroup.Id> owned = myOwnedGroups(db);
        Boolean amAdmin = null;
        for (final AccountGroupMember.Key k : keys) {
          if (!owned.contains(k.getAccountGroupId())) {
            if (amAdmin == null) {
              amAdmin = amAdmin(db);
            }
            if (!amAdmin) {
              throw new Failure(new NoSuchEntityException());
            }
          }
        }
        for (final AccountGroupMember.Key k : keys) {
          final AccountGroupMember m = db.accountGroupMembers().get(k);
          if (m != null) {
            db.accountGroupMembers().delete(Collections.singleton(m));
          }
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void changeGroupOwner(final AccountGroupMember.Key key,
      final boolean owner, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        assertAmGroupOwner(db, key.getAccountGroupId());
        final AccountGroupMember m = db.accountGroupMembers().get(key);
        if (m == null) {
          throw new Failure(new NoSuchEntityException());
        }
        if (m.isGroupOwner() != owner) {
          m.setGroupOwner(owner);
          db.accountGroupMembers().update(Collections.singleton(m));
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  private static boolean amAdmin(final ReviewDb db) throws OrmException {
    final AccountGroup admin = db.accountGroups().get(ADMIN_GROUP);
    if (admin == null) {
      return false;
    }
    return db.accountGroupMembers().get(
        new AccountGroupMember.Key(RpcUtil.getAccountId(), admin.getId())) != null;
  }

  private static void assertAmGroupOwner(final ReviewDb db,
      final AccountGroup.Id groupId) throws OrmException, Failure {
    final AccountGroupMember m =
        db.accountGroupMembers().get(
            new AccountGroupMember.Key(RpcUtil.getAccountId(), groupId));
    if ((m == null || !m.isGroupOwner()) && !amAdmin(db)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  private static Set<AccountGroup.Id> myOwnedGroups(final ReviewDb db)
      throws OrmException {
    final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>();
    for (final AccountGroupMember m : db.accountGroupMembers().owned(
        RpcUtil.getAccountId())) {
      r.add(m.getAccountGroupId());
    }
    return r;
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
