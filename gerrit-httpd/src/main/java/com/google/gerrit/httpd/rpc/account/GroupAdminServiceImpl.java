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
import com.google.gerrit.common.errors.InactiveAccountException;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupIncludedGroup;
import com.google.gerrit.reviewdb.AccountGroupIncludedGroupAudit;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.IncludedGroupCache;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.account.Realm;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class GroupAdminServiceImpl extends BaseServiceImplementation implements
    GroupAdminService {
  private final Provider<IdentifiedUser> identifiedUser;
  private final AccountCache accountCache;
  private final AccountResolver accountResolver;
  private final Realm accountRealm;
  private final GroupCache groupCache;
  private final IncludedGroupCache includedGroupCache;
  private final GroupControl.Factory groupControlFactory;

  private final CreateGroup.Factory createGroupFactory;
  private final RenameGroup.Factory renameGroupFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;

  @Inject
  GroupAdminServiceImpl(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final AccountCache accountCache, final IncludedGroupCache includedGroupCache,
      final AccountResolver accountResolver,
      final Realm accountRealm, final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final CreateGroup.Factory createGroupFactory,
      final RenameGroup.Factory renameGroupFactory,
      final GroupDetailFactory.Factory groupDetailFactory) {
    super(schema, currentUser);
    this.identifiedUser = currentUser;
    this.accountCache = accountCache;
    this.includedGroupCache = includedGroupCache;
    this.accountResolver = accountResolver;
    this.accountRealm = accountRealm;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.createGroupFactory = createGroupFactory;
    this.renameGroupFactory = renameGroupFactory;
    this.groupDetailFactory = groupDetailFactory;
  }

  public void ownedGroups(final AsyncCallback<List<AccountGroup>> callback) {
    run(callback, new Action<List<AccountGroup>>() {
      public List<AccountGroup> run(ReviewDb db) throws OrmException {
        final IdentifiedUser user = identifiedUser.get();
        final List<AccountGroup> result;
        if (user.isAdministrator()) {
          result = db.accountGroups().all().toList();
        } else {
          final HashSet<AccountGroup.Id> seen = new HashSet<AccountGroup.Id>();
          result = new ArrayList<AccountGroup>();
          for (final AccountGroup.Id myGroup : user.getEffectiveGroups()) {
            for (AccountGroup group : db.accountGroups().ownedByGroup(myGroup)) {
              final AccountGroup.Id id = group.getId();
              if (!seen.add(id)) {
                continue;
              }
              try {
                GroupControl c = groupControlFactory.controlFor(id);
                if (c.isOwner()) {
                  result.add(c.getAccountGroup());
                }
              } catch (NoSuchGroupException e) {
                continue;
              }
            }
          }
        }
        Collections.sort(result, new Comparator<AccountGroup>() {
          public int compare(final AccountGroup a, final AccountGroup b) {
            return a.getName().compareTo(b.getName());
          }
        });
        return result;
      }
    });
  }

  public void createGroup(final String newName,
      final AsyncCallback<AccountGroup.Id> callback) {
    createGroupFactory.create(newName).to(callback);
  }

  public void groupDetail(final AccountGroup.Id groupId,
      final AsyncCallback<GroupDetail> callback) {
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
        new ArrayList<AccountGroup.ExternalNameKey>(accountRealm
            .lookupGroups(searchFilter));
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
    run(callback, new Action<GroupDetail>() {
      public GroupDetail run(ReviewDb db) throws OrmException, Failure,
          NoSuchGroupException {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
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

  public void addIncludedGroup(final AccountGroup.Id groupId,
      final String groupName, final AsyncCallback<GroupDetail> callback) {
    run(callback, new Action<GroupDetail>() {
      public GroupDetail run(ReviewDb db) throws OrmException, Failure,
          NoSuchGroupException {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final AccountGroup a = findGroup(groupName);
        if (!control.canAddGroup(a.getId())) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroupIncludedGroup.Key key =
          new AccountGroupIncludedGroup.Key(groupId, a.getId());
          AccountGroupIncludedGroup m = db.accountGroupIncludedGroups().get(key);
          if (m == null) {
            m = new AccountGroupIncludedGroup(key);
            db.accountGroupIncludedGroupsAudit().insert(
                Collections.singleton(new AccountGroupIncludedGroupAudit(m,
                    getAccountId())));
            db.accountGroupIncludedGroups().insert(Collections.singleton(m));
            includedGroupCache.evictGroup(groupId);
            includedGroupCache.evictIncludedGroup(a.getId());
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
        if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
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

  public void deleteIncludedGroups(final AccountGroup.Id groupId,
      final Set<AccountGroupIncludedGroup.Key> keys, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException,
          NoSuchGroupException, Failure {
        final GroupControl control = groupControlFactory.validateFor(groupId);
        if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
          throw new Failure(new NameAlreadyUsedException());
        }

        final Account.Id me = getAccountId();
        for (final AccountGroupIncludedGroup.Key k : keys) {
          final AccountGroupIncludedGroup m = db.accountGroupIncludedGroups().get(k);
          if (m != null) {
            if (!control.canRemoveGroup(m.getIncludedGroupId())) {
              throw new Failure(new NoSuchEntityException());
            }

            AccountGroupIncludedGroupAudit audit = null;
            for (AccountGroupIncludedGroupAudit a : db.accountGroupIncludedGroupsAudit()
                .byGroupIncludedGroup(m.getGroupId(), m.getIncludedGroupId())) {
              if (a.isActive()) {
                audit = a;
                break;
              }
            }

            if (audit != null) {
              audit.removed(me);
              db.accountGroupIncludedGroupsAudit()
                  .update(Collections.singleton(audit));
            }
            db.accountGroupIncludedGroups().delete(Collections.singleton(m));
            includedGroupCache.evictIncludedGroup(m.getIncludedGroupId());
          }

          includedGroupCache.evictGroup(groupId);
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
      throw new Failure(new NoSuchEntityException());
    }
    return g;
  }

}
