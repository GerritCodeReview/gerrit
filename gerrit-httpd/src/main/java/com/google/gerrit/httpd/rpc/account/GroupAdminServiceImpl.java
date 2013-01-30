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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.GroupAdminService;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupOptions;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuidAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class GroupAdminServiceImpl extends BaseServiceImplementation implements
    GroupAdminService {
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final GroupControl.Factory groupControlFactory;

  private final RenameGroup.Factory renameGroupFactory;
  private final GroupDetailHandler.Factory groupDetailFactory;

  @Inject
  GroupAdminServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final GroupIncludeCache groupIncludeCache,
      final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final RenameGroup.Factory renameGroupFactory,
      final GroupDetailHandler.Factory groupDetailFactory) {
    super(schema, currentUser);
    this.groupIncludeCache = groupIncludeCache;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.renameGroupFactory = renameGroupFactory;
    this.groupDetailFactory = groupDetailFactory;
  }

  public void groupDetail(AccountGroup.Id groupId, AccountGroup.UUID groupUUID,
      AsyncCallback<GroupDetail> callback) {
    if (groupId == null && groupUUID != null) {
      AccountGroup g = groupCache.get(groupUUID);
      if (g != null) {
        groupId = g.getId();
      }
    }
    groupDetailFactory.create(groupId).to(callback);
  }

  public void changeGroupDescription(final AccountGroup.Id groupId,
      final String description, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);
        group.setDescription(description);
        db.accountGroups().update(Collections.singleton(group));
        groupCache.evict(group);
        return VoidResult.INSTANCE;
      }
    });
  }

  public void changeGroupOptions(final AccountGroup.Id groupId,
      final GroupOptions groupOptions, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);
        group.setVisibleToAll(groupOptions.isVisibleToAll());
        db.accountGroups().update(Collections.singleton(group));
        groupCache.evict(group);
        return VoidResult.INSTANCE;
      }
    });
  }

  public void renameGroup(final AccountGroup.Id groupId, final String newName,
      final AsyncCallback<GroupDetail> callback) {
    renameGroupFactory.create(groupId, newName).to(callback);
  }

  public void changeGroupType(final AccountGroup.Id groupId,
      final AccountGroup.Type newType, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);
        group.setType(newType);
        db.accountGroups().update(Collections.singleton(group));
        groupCache.evict(group);
        return VoidResult.INSTANCE;
      }
    });
  }

  public void addGroupInclude(final AccountGroup.Id groupId,
      final AccountGroup.UUID incGroupUUID, final String incGroupName,
      final AsyncCallback<GroupDetail> callback) {
    run(callback, new Action<GroupDetail>() {
      public GroupDetail run(ReviewDb db) throws OrmException, Failure,
          NoSuchGroupException {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        AccountGroup group = groupCache.get(groupId);
        if (group.getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        if (incGroupUUID == null) {
          throw new Failure(new NoSuchGroupException(incGroupName));
        }

        if (!control.canAddGroup(incGroupUUID)) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroupIncludeByUuid.Key key =
            new AccountGroupIncludeByUuid.Key(groupId, incGroupUUID);
        AccountGroupIncludeByUuid m = db.accountGroupIncludesByUuid().get(key);
        if (m == null) {
          m = new AccountGroupIncludeByUuid(key);
          db.accountGroupIncludesByUuidAudit().insert(
              Collections.singleton(new AccountGroupIncludeByUuidAudit(m,
                  getAccountId())));
          db.accountGroupIncludesByUuid().insert(Collections.singleton(m));
          groupIncludeCache.evictMemberIn(incGroupUUID);
          groupIncludeCache.evictMembersOf(group.getGroupUUID());
        }

        return groupDetailFactory.create(groupId).call();
      }
    });
  }

  public void deleteGroupIncludes(final AccountGroup.Id groupId,
      final Set<AccountGroupIncludeByUuid.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException,
          NoSuchGroupException, Failure {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        AccountGroup group = groupCache.get(groupId);
        if (group.getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        for (final AccountGroupIncludeByUuid.Key k : keys) {
          if (!groupId.equals(k.getGroupId())) {
            throw new Failure(new NoSuchEntityException());
          }
        }

        final Account.Id me = getAccountId();
        final Set<AccountGroup.UUID> groupsToEvict = new HashSet<AccountGroup.UUID>();
        for (final AccountGroupIncludeByUuid.Key k : keys) {
          final AccountGroupIncludeByUuid m =
              db.accountGroupIncludesByUuid().get(k);
          if (m != null) {
            if (!control.canRemoveGroup(m.getIncludeUUID())) {
              throw new Failure(new NoSuchEntityException());
            }

            AccountGroupIncludeByUuidAudit audit = null;
            for (AccountGroupIncludeByUuidAudit a : db
                .accountGroupIncludesByUuidAudit().byGroupInclude(
                    m.getGroupId(), m.getIncludeUUID())) {
              if (a.isActive()) {
                audit = a;
                break;
              }
            }

            if (audit != null) {
              audit.removed(me);
              db.accountGroupIncludesByUuidAudit().update(
                  Collections.singleton(audit));
            }
            db.accountGroupIncludesByUuid().delete(Collections.singleton(m));
            groupsToEvict.add(k.getIncludeUUID());
          }
        }
        for (AccountGroup.UUID uuid : groupsToEvict) {
          groupIncludeCache.evictMemberIn(uuid);
        }
        if (!groupsToEvict.isEmpty()) {
          groupIncludeCache.evictMembersOf(group.getGroupUUID());
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  private void assertAmGroupOwner(final ReviewDb db, final AccountGroup group)
      throws Failure {
    try {
      if (!groupControlFactory.controlFor(group.getId()).isOwner()) {
        throw new Failure(new NoSuchGroupException(group.getId()));
      }
    } catch (NoSuchGroupException e) {
      throw new Failure(new NoSuchGroupException(group.getId()));
    }
  }
}
