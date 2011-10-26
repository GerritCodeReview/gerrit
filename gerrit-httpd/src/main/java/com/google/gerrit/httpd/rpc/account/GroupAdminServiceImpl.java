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
import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.common.data.GroupOptions;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.Realm;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

class GroupAdminServiceImpl extends BaseServiceImplementation implements
    GroupAdminService {
  private final Provider<IdentifiedUser> identifiedUser;
  private final Realm accountRealm;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final AddGroupMemberHandler.Factory addGroupMemberHandlerFactory;
  private final AddGroupIncludeHandler.Factory addGroupIncludeHandlerFactory;
  private final RemoveGroupMemberHandler.Factory removeGroupMemberHandlerFactory;
  private final RemoveGroupIncludeHandler.Factory removeGroupIncludeHandlerFactory;

  private final CreateGroup.Factory createGroupFactory;
  private final RenameGroup.Factory renameGroupFactory;
  private final GroupDetailHandler.Factory groupDetailFactory;

  @Inject
  GroupAdminServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final Realm accountRealm,
      final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final CreateGroup.Factory createGroupFactory,
      final RenameGroup.Factory renameGroupFactory,
      final GroupDetailHandler.Factory groupDetailFactory,
      final AddGroupMemberHandler.Factory addGroupMemberHandlerFactory,
      final AddGroupIncludeHandler.Factory addGroupIncludeHandlerFactory,
      final RemoveGroupMemberHandler.Factory removeGroupMemberHandlerFactory,
      final RemoveGroupIncludeHandler.Factory removeGroupIncludeHandlerFactory) {
    super(schema, currentUser);
    this.identifiedUser = currentUser;
    this.accountRealm = accountRealm;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.createGroupFactory = createGroupFactory;
    this.renameGroupFactory = renameGroupFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.addGroupMemberHandlerFactory = addGroupMemberHandlerFactory;
    this.addGroupIncludeHandlerFactory = addGroupIncludeHandlerFactory;
    this.removeGroupMemberHandlerFactory = removeGroupMemberHandlerFactory;
    this.removeGroupIncludeHandlerFactory = removeGroupIncludeHandlerFactory;
  }

  public void visibleGroups(final AsyncCallback<GroupList> callback) {
    run(callback, new Action<GroupList>() {
      public GroupList run(ReviewDb db) throws OrmException,
          NoSuchGroupException {
        final IdentifiedUser user = identifiedUser.get();
        final List<AccountGroup> list;
        if (user.getCapabilities().canAdministrateServer()) {
          list = db.accountGroups().all().toList();
        } else {
          list = new ArrayList<AccountGroup>();
          for(final AccountGroup group : db.accountGroups().all().toList()) {
            final GroupControl c = groupControlFactory.controlFor(group);
            if (c.isVisible()) {
              list.add(c.getAccountGroup());
            }
          }
        }
        Collections.sort(list, new Comparator<AccountGroup>() {
          public int compare(final AccountGroup a, final AccountGroup b) {
            return a.getName().compareTo(b.getName());
          }
        });

        List<GroupDetail> l = new ArrayList<GroupDetail>();
        for(AccountGroup group : list) {
          l.add(groupDetailFactory.create(group.getId()).call());
        }
        GroupList res = new GroupList();
        res.setGroups(l);
        res.setCanCreateGroup(user.getCapabilities().canCreateGroup());
        return res;
      }
    });
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
        group.setEmailOnlyAuthors(groupOptions.isEmailOnlyAuthors());
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

        final AccountGroup owner =
            groupCache.get(new AccountGroup.NameKey(newOwnerName));
        if (owner == null) {
          throw new Failure(new NoSuchEntityException());
        }

        group.setOwnerGroupId(owner.getId());
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

  public void changeExternalGroup(final AccountGroup.Id groupId,
      final AccountGroup.ExternalNameKey bindTo,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final AccountGroup group = db.accountGroups().get(groupId);
        assertAmGroupOwner(db, group);
        group.setExternalNameKey(bindTo);
        db.accountGroups().update(Collections.singleton(group));
        groupCache.evict(group);
        return VoidResult.INSTANCE;
      }
    });
  }

  public void searchExternalGroups(final String searchFilter,
      final AsyncCallback<List<AccountGroup.ExternalNameKey>> callback) {
    final ArrayList<AccountGroup.ExternalNameKey> matches =
        new ArrayList<AccountGroup.ExternalNameKey>(
            accountRealm.lookupGroups(searchFilter));
    Collections.sort(matches, new Comparator<AccountGroup.ExternalNameKey>() {
      @Override
      public int compare(AccountGroup.ExternalNameKey a,
          AccountGroup.ExternalNameKey b) {
        return a.get().compareTo(b.get());
      }
    });
    callback.onSuccess(matches);
  }

  public void addGroupMember(final AccountGroup.Id groupId,
      final String nameOrEmail, final AsyncCallback<GroupDetail> callback) {
    addGroupMemberHandlerFactory.create(groupId, nameOrEmail).to(callback);
  }

  public void addGroupInclude(final AccountGroup.Id groupId,
      final String groupName, final AsyncCallback<GroupDetail> callback) {
    addGroupIncludeHandlerFactory.create(groupId, groupName).to(callback);
  }

  public void deleteGroupMembers(final AccountGroup.Id groupId,
      final Set<Account.Id> accountIds,
      final AsyncCallback<GroupMemberResult> callback) {
    removeGroupMemberHandlerFactory.create(groupId, accountIds).to(callback);
  }

  public void deleteGroupIncludes(final AccountGroup.Id groupId,
      final Set<AccountGroup.Id> groupsToRemove,
      final AsyncCallback<GroupMemberResult> callback) {
    removeGroupIncludeHandlerFactory.create(groupId, groupsToRemove).to(
        callback);
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
