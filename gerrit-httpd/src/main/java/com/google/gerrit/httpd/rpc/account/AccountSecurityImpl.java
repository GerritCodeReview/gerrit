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
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.AccountSecurity;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.errors.ContactInformationStoreException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.ContactInformation;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.ChangeUserName;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.mail.EmailTokenVerifier;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.TimeUtil;
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
  private final ContactStore contactStore;
  private final Realm realm;
  private final ProjectCache projectCache;
  private final Provider<IdentifiedUser> user;
  private final EmailTokenVerifier emailTokenVerifier;
  private final AccountByEmailCache byEmailCache;
  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final boolean useContactInfo;

  private final ChangeUserName.CurrentUser changeUserNameFactory;
  private final DeleteExternalIds.Factory deleteExternalIdsFactory;
  private final ExternalIdDetailFactory.Factory externalIdDetailFactory;

  private final ChangeHooks hooks;
  private final GroupCache groupCache;

  @Inject
  AccountSecurityImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser, final ContactStore cs,
      final Realm r, final Provider<IdentifiedUser> u,
      final EmailTokenVerifier etv, final ProjectCache pc,
      final AccountByEmailCache abec, final AccountCache uac,
      final AccountManager am,
      final ChangeUserName.CurrentUser changeUserNameFactory,
      final DeleteExternalIds.Factory deleteExternalIdsFactory,
      final ExternalIdDetailFactory.Factory externalIdDetailFactory,
      final ChangeHooks hooks, final GroupCache groupCache) {
    super(schema, currentUser);
    contactStore = cs;
    realm = r;
    user = u;
    emailTokenVerifier = etv;
    projectCache = pc;
    byEmailCache = abec;
    accountCache = uac;
    accountManager = am;

    useContactInfo = contactStore != null && contactStore.isEnabled();

    this.changeUserNameFactory = changeUserNameFactory;
    this.deleteExternalIdsFactory = deleteExternalIdsFactory;
    this.externalIdDetailFactory = externalIdDetailFactory;
    this.hooks = hooks;
    this.groupCache = groupCache;
  }

  @Override
  public void changeUserName(final String newName,
      final AsyncCallback<VoidResult> callback) {
    if (realm.allowsEdit(Account.FieldName.USER_NAME)) {
      Handler.wrap(changeUserNameFactory.create(newName)).to(callback);
    } else {
      callback.onFailure(new PermissionDeniedException("Not allowed to change"
          + " username"));
    }
  }

  public void myExternalIds(AsyncCallback<List<AccountExternalId>> callback) {
    externalIdDetailFactory.create().to(callback);
  }

  public void deleteExternalIds(final Set<AccountExternalId.Key> keys,
      final AsyncCallback<Set<AccountExternalId.Key>> callback) {
    deleteExternalIdsFactory.create(keys).to(callback);
  }

  public void updateContact(final String name, final String emailAddr,
      final ContactInformation info, final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      public Account run(ReviewDb db) throws OrmException, Failure {
        IdentifiedUser self = user.get();
        final Account me = db.accounts().get(self.getAccountId());
        final String oldEmail = me.getPreferredEmail();
        if (realm.allowsEdit(Account.FieldName.FULL_NAME)) {
          me.setFullName(Strings.emptyToNull(name));
        }
        if (!Strings.isNullOrEmpty(emailAddr)
            && !self.getEmailAddresses().contains(emailAddr)) {
          throw new Failure(new PermissionDeniedException("Email address must be verified"));
        }
        me.setPreferredEmail(Strings.emptyToNull(emailAddr));
        if (useContactInfo) {
          if (ContactInformation.hasAddress(info)
              || (me.isContactFiled() && ContactInformation.hasData(info))) {
            me.setContactFiled(TimeUtil.nowTs());
          }
          if (ContactInformation.hasData(info)) {
            try {
              contactStore.store(me, info);
            } catch (ContactInformationStoreException e) {
              throw new Failure(e);
            }
          }
        }
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

  public void enterAgreement(final String agreementName,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
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
        hooks.doClaSignupHook(account, ca);

        final AccountGroupMember.Key key =
            new AccountGroupMember.Key(account.getId(), group.getId());
        AccountGroupMember m = db.accountGroupMembers().get(key);
        if (m == null) {
          m = new AccountGroupMember(key);
          db.accountGroupMembersAudit().insert(
              Collections.singleton(new AccountGroupMemberAudit(
                  m, account.getId(), TimeUtil.nowTs())));
          db.accountGroupMembers().insert(Collections.singleton(m));
          accountCache.evict(m.getAccountId());
        }

        return VoidResult.INSTANCE;
      }
    });
  }

  public void validateEmail(final String tokenString,
      final AsyncCallback<VoidResult> callback) {
    try {
      EmailTokenVerifier.ParsedToken token = emailTokenVerifier.decode(tokenString);
      Account.Id currentUser = user.get().getAccountId();
      if (currentUser.equals(token.getAccountId())) {
        accountManager.link(currentUser, token.toAuthRequest());
        callback.onSuccess(VoidResult.INSTANCE);
      } else {
        throw new EmailTokenVerifier.InvalidTokenException();
      }
    } catch (EmailTokenVerifier.InvalidTokenException e) {
      callback.onFailure(e);
    } catch (AccountException e) {
      callback.onFailure(e);
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }
}
