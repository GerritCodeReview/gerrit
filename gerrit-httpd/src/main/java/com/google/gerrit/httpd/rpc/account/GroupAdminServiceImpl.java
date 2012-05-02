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
import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.common.data.GroupOptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.InactiveAccountException;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeAudit;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupBackends;
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
  private final AccountCache accountCache;
  private final AccountResolver accountResolver;
  private final GroupCache groupCache;
  private final GroupBackend groupBackend;
  private final GroupIncludeCache groupIncludeCache;
  private final GroupControl.Factory groupControlFactory;

  private final CreateGroup.Factory createGroupFactory;
  private final RenameGroup.Factory renameGroupFactory;
  private final GroupDetailHandler.Factory groupDetailFactory;
  private final VisibleGroupsHandler.Factory visibleGroupsFactory;

  @Inject
  GroupAdminServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final AccountCache accountCache,
      final GroupIncludeCache groupIncludeCache,
      final AccountResolver accountResolver,
      final GroupCache groupCache,
      final GroupBackend groupBackend,
      final GroupControl.Factory groupControlFactory,
      final CreateGroup.Factory createGroupFactory,
      final RenameGroup.Factory renameGroupFactory,
      final GroupDetailHandler.Factory groupDetailFactory,
      final VisibleGroupsHandler.Factory visibleGroupsFactory) {
    super(schema, currentUser);
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.accountResolver = accountResolver;
    this.groupCache = groupCache;
    this.groupBackend = groupBackend;
    this.groupControlFactory = groupControlFactory;
    this.createGroupFactory = createGroupFactory;
    this.renameGroupFactory = renameGroupFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.visibleGroupsFactory = visibleGroupsFactory;
  }

  public void visibleGroups(final AsyncCallback<GroupList> callback) {
    visibleGroupsFactory.create().to(callback);
  }

  public void createGroup(final String newName,
      final AsyncCallback<AccountGroup.Id> callback) {
    createGroupFactory.create(newName).to(callback);
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

  public void changeGroupOwner(final AccountGroup.Id groupId,
      final String newOwnerName, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);

        GroupReference owner =
            GroupBackends.findExactSuggestion(groupBackend, newOwnerName);
        if (owner == null) {
          throw new Failure(new NoSuchEntityException());
        }

        group.setOwnerGroupUUID(owner.getUUID());
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

  public void addGroupMember(final AccountGroup.Id groupId,
      final String nameOrEmail, final AsyncCallback<GroupDetail> callback) {
    run(callback, new Action<GroupDetail>() {
      public GroupDetail run(ReviewDb db) throws OrmException, Failure,
          NoSuchGroupException {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (groupCache.get(groupId).getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final Account a = findAccount(nameOrEmail);
        if (!a.isActive()) {
          throw new Failure(new InactiveAccountException(a.getFullName()));
        }
        if (!control.canAddMember(a.getId())) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroupMember.Key key =
            new AccountGroupMember.Key(a.getId(), groupId);
        AccountGroupMember m = db.accountGroupMembers().get(key);
        if (m == null) {
          m = new AccountGroupMember(key);
          db.accountGroupMembersAudit().insert(
              Collections.singleton(new AccountGroupMemberAudit(m,
                  getAccountId())));
          db.accountGroupMembers().insert(Collections.singleton(m));
          accountCache.evict(m.getAccountId());
        }

        return groupDetailFactory.create(groupId).call();
      }
    });
  }

  public void addGroupInclude(final AccountGroup.Id groupId,
      final String groupName, final AsyncCallback<GroupDetail> callback) {
    run(callback, new Action<GroupDetail>() {
      public GroupDetail run(ReviewDb db) throws OrmException, Failure,
          NoSuchGroupException {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (groupCache.get(groupId).getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final AccountGroup a = findGroup(groupName);
        if (!control.canAddGroup(a.getId())) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroupInclude.Key key =
            new AccountGroupInclude.Key(groupId, a.getId());
        AccountGroupInclude m = db.accountGroupIncludes().get(key);
        if (m == null) {
          m = new AccountGroupInclude(key);
          db.accountGroupIncludesAudit().insert(
              Collections.singleton(new AccountGroupIncludeAudit(m,
                  getAccountId())));
          db.accountGroupIncludes().insert(Collections.singleton(m));
          groupIncludeCache.evictInclude(a.getGroupUUID());
        }

        return groupDetailFactory.create(groupId).call();
      }
    });
  }

  public void deleteGroupMembers(final AccountGroup.Id groupId,
      final Set<AccountGroupMember.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException,
          NoSuchGroupException, Failure {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (groupCache.get(groupId).getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        for (final AccountGroupMember.Key k : keys) {
          if (!groupId.equals(k.getAccountGroupId())) {
            throw new Failure(new NoSuchEntityException());
          }
        }

        final Account.Id me = getAccountId();
        for (final AccountGroupMember.Key k : keys) {
          final AccountGroupMember m = db.accountGroupMembers().get(k);
          if (m != null) {
            if (!control.canRemoveMember(m.getAccountId())) {
              throw new Failure(new NoSuchEntityException());
            }

            AccountGroupMemberAudit audit = null;
            for (AccountGroupMemberAudit a : db.accountGroupMembersAudit()
                .byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
              if (a.isActive()) {
                audit = a;
                break;
              }
            }

            if (audit != null) {
              audit.removed(me);
              db.accountGroupMembersAudit()
                  .update(Collections.singleton(audit));
            } else {
              audit = new AccountGroupMemberAudit(m, me);
              audit.removedLegacy();
              db.accountGroupMembersAudit()
                  .insert(Collections.singleton(audit));
            }

            db.accountGroupMembers().delete(Collections.singleton(m));
            accountCache.evict(m.getAccountId());
          }
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void deleteGroupIncludes(final AccountGroup.Id groupId,
      final Set<AccountGroupInclude.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException,
          NoSuchGroupException, Failure {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (groupCache.get(groupId).getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        for (final AccountGroupInclude.Key k : keys) {
          if (!groupId.equals(k.getGroupId())) {
            throw new Failure(new NoSuchEntityException());
          }
        }

        final Account.Id me = getAccountId();
        final Set<AccountGroup.Id> groupsToEvict = new HashSet<AccountGroup.Id>();
        for (final AccountGroupInclude.Key k : keys) {
          final AccountGroupInclude m =
              db.accountGroupIncludes().get(k);
          if (m != null) {
            if (!control.canRemoveGroup(m.getIncludeId())) {
              throw new Failure(new NoSuchEntityException());
            }

            AccountGroupIncludeAudit audit = null;
            for (AccountGroupIncludeAudit a : db
                .accountGroupIncludesAudit().byGroupInclude(
                    m.getGroupId(), m.getIncludeId())) {
              if (a.isActive()) {
                audit = a;
                break;
              }
            }

            if (audit != null) {
              audit.removed(me);
              db.accountGroupIncludesAudit().update(
                  Collections.singleton(audit));
            }
            db.accountGroupIncludes().delete(Collections.singleton(m));
            groupsToEvict.add(k.getIncludeId());
          }
        }
        for (AccountGroup group : db.accountGroups().get(groupsToEvict)) {
          groupIncludeCache.evictInclude(group.getGroupUUID());
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

  private Account findAccount(final String nameOrEmail) throws OrmException,
      Failure {
    final Account r = accountResolver.find(nameOrEmail);
    if (r == null) {
      throw new Failure(new NoSuchAccountException(nameOrEmail));
    }
    return r;
  }

  private AccountGroup findGroup(final String name) throws OrmException,
      Failure {
    final AccountGroup g = groupCache.get(new AccountGroup.NameKey(name));
    if (g == null) {
      throw new Failure(new NoSuchGroupException(name));
    }
    return g;
  }

}
