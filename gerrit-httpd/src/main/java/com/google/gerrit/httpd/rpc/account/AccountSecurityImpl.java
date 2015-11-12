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

import com.google.common.base.Strings;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.AccountSecurity;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class AccountSecurityImpl extends BaseServiceImplementation implements
    AccountSecurity {
  private final Realm realm;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> user;
  private final AccountByEmailCache byEmailCache;
  private final AccountCache accountCache;

  private final DeleteExternalIds.Factory deleteExternalIdsFactory;
  private final ExternalIdDetailFactory.Factory externalIdDetailFactory;

  private final ChangeHooks hooks;
  private final GroupCache groupCache;
  private final AuditService auditService;

  @Inject
  AccountSecurityImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final Realm r, final Provider<IdentifiedUser> u,
      final ProjectCache pc,
      final AccountByEmailCache abec, final AccountCache uac,
      final DeleteExternalIds.Factory deleteExternalIdsFactory,
      final ExternalIdDetailFactory.Factory externalIdDetailFactory,
      final ChangeHooks hooks, final GroupCache groupCache,
      final AuditService auditService) {
    super(schema, currentUser);
    realm = r;
    user = u;
    projectCache = pc;
    byEmailCache = abec;
    accountCache = uac;
    this.auditService = auditService;
    this.deleteExternalIdsFactory = deleteExternalIdsFactory;
    this.externalIdDetailFactory = externalIdDetailFactory;
    this.hooks = hooks;
    this.groupCache = groupCache;
  }

  @Override
  public void myExternalIds(AsyncCallback<List<AccountExternalId>> callback) {
    externalIdDetailFactory.create().to(callback);
  }

  @Override
  public void deleteExternalIds(final Set<AccountExternalId.Key> keys,
      final AsyncCallback<Set<AccountExternalId.Key>> callback) {
    deleteExternalIdsFactory.create(keys).to(callback);
  }

  @Override
  public void updateContact(final String name, final String emailAddr,
      final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      @Override
      public Account run(ReviewDb db) throws OrmException, Failure {
        IdentifiedUser self = user.get();
        final Account me = db.accounts().get(self.getAccountId());
        final String oldEmail = me.getPreferredEmail();
        if (realm.allowsEdit(Account.FieldName.FULL_NAME)) {
          me.setFullName(Strings.emptyToNull(name));
        }
        if (!Strings.isNullOrEmpty(emailAddr)
            && !self.hasEmailAddress(emailAddr)) {
          throw new Failure(new PermissionDeniedException("Email address must be verified"));
        }
        me.setPreferredEmail(Strings.emptyToNull(emailAddr));
        db.accounts().update(Collections.singleton(me));
        if (!eq(oldEmail, me.getPreferredEmail())) {
          byEmailCache.evict(oldEmail);
          byEmailCache.evict(me.getPreferredEmail());
        }
        accountCache.evict(me.getId());
        return me;
      }
    });
  }

  private static boolean eq(final String a, final String b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null && a.equals(b);
  }

  @Override
  public void enterAgreement(final String agreementName,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      @Override
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        ContributorAgreement ca = projectCache.getAllProjects().getConfig()
            .getContributorAgreement(agreementName);
        if (ca == null) {
          throw new Failure(new NoSuchEntityException());
        }

        if (ca.getAutoVerify() == null) {
          throw new Failure(new IllegalStateException(
              "cannot enter a non-autoVerify agreement"));
        } else if (ca.getAutoVerify().getUUID() == null) {
          throw new Failure(new NoSuchEntityException());
        }

        AccountGroup group = groupCache.get(ca.getAutoVerify().getUUID());
        if (group == null) {
          throw new Failure(new NoSuchEntityException());
        }

        Account account = user.get().getAccount();
        hooks.doClaSignupHook(account, ca.getName());

        final AccountGroupMember.Key key =
            new AccountGroupMember.Key(account.getId(), group.getId());
        AccountGroupMember m = db.accountGroupMembers().get(key);
        if (m == null) {
          m = new AccountGroupMember(key);
          auditService.dispatchAddAccountsToGroup(account.getId(), Collections
              .singleton(m));
          db.accountGroupMembers().insert(Collections.singleton(m));
          accountCache.evict(m.getAccountId());
        }

        return VoidResult.INSTANCE;
      }
    });
  }
}
