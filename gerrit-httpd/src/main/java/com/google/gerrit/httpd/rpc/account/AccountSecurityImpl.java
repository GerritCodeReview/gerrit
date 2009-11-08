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

import com.google.gerrit.common.data.AccountSecurity;
import com.google.gerrit.common.errors.ContactInformationStoreException;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.common.errors.InvalidSshUserNameException;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gerrit.reviewdb.ContactInformation;
import com.google.gerrit.reviewdb.ContributorAgreement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class AccountSecurityImpl extends BaseServiceImplementation implements
    AccountSecurity {

  private static final Pattern SSH_USER_NAME_PATTERN = Pattern.compile(Account.SSH_USER_NAME_PATTERN);

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ContactStore contactStore;
  private final AuthConfig authConfig;
  private final Realm realm;
  private final RegisterNewEmailSender.Factory registerNewEmailFactory;
  private final SshKeyCache sshKeyCache;
  private final AccountByEmailCache byEmailCache;
  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final boolean useContactInfo;

  private final ExternalIdDetailFactory.Factory externalIdDetailFactory;
  private final MyGroupsFactory.Factory myGroupsFactory;

  @Inject
  AccountSecurityImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser, final ContactStore cs,
      final AuthConfig ac, final Realm r,
      final RegisterNewEmailSender.Factory esf, final SshKeyCache skc,
      final AccountByEmailCache abec, final AccountCache uac,
      final AccountManager am,
      final ExternalIdDetailFactory.Factory externalIdDetailFactory,
      final MyGroupsFactory.Factory myGroupsFactory) {
    super(schema, currentUser);
    contactStore = cs;
    authConfig = ac;
    realm = r;
    registerNewEmailFactory = esf;
    sshKeyCache = skc;
    byEmailCache = abec;
    accountCache = uac;
    accountManager = am;

    useContactInfo = contactStore != null && contactStore.isEnabled();

    this.externalIdDetailFactory = externalIdDetailFactory;
    this.myGroupsFactory = myGroupsFactory;
  }

  public void mySshKeys(final AsyncCallback<List<AccountSshKey>> callback) {
    run(callback, new Action<List<AccountSshKey>>() {
      public List<AccountSshKey> run(ReviewDb db) throws OrmException {
        return db.accountSshKeys().byAccount(getAccountId()).toList();
      }
    });
  }

  public void addSshKey(final String keyText,
      final AsyncCallback<AccountSshKey> callback) {
    run(callback, new Action<AccountSshKey>() {
      public AccountSshKey run(final ReviewDb db) throws OrmException, Failure {
        int max = 0;
        final Account.Id me = getAccountId();
        for (final AccountSshKey k : db.accountSshKeys().byAccount(me)) {
          max = Math.max(max, k.getKey().get());
        }

        final AccountSshKey key;
        try {
          key = sshKeyCache.create(new AccountSshKey.Id(me, max + 1), keyText);
        } catch (InvalidSshKeyException e) {
          throw new Failure(e);
        }
        db.accountSshKeys().insert(Collections.singleton(key));
        uncacheSshKeys(me);
        return key;
      }
    });
  }

  public void deleteSshKeys(final Set<AccountSshKey.Id> ids,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = getAccountId();
        for (final AccountSshKey.Id keyId : ids) {
          if (!me.equals(keyId.getParentKey()))
            throw new Failure(new NoSuchEntityException());
        }

        final List<AccountSshKey> k = db.accountSshKeys().get(ids).toList();
        if (!k.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.accountSshKeys().delete(k, txn);
          txn.commit();
          uncacheSshKeys(me);
        }

        return VoidResult.INSTANCE;
      }
    });
  }

  private void uncacheSshKeys(final Account.Id me) {
    uncacheSshKeys(accountCache.get(me).getAccount().getSshUserName());
  }

  private void uncacheSshKeys(final String userName) {
    sshKeyCache.evict(userName);
  }

  @Override
  public void changeSshUserName(final String newName,
      final AsyncCallback<VoidResult> callback) {
    if (!realm.allowsEdit(Account.FieldName.SSH_USER_NAME)) {
      callback.onFailure(new NameAlreadyUsedException());
      return;
    }

    run(callback, new Action<VoidResult>() {
      @Override
      public VoidResult run(ReviewDb db) throws OrmException, Failure {
        final Account me = db.accounts().get(getAccountId());
        if (me == null) {
          throw new Failure(new NoSuchEntityException());
        }
        if (newName != null && !SSH_USER_NAME_PATTERN.matcher(newName).matches()) {
          throw new Failure(new InvalidSshUserNameException());
        }
        final Account other;
        if (newName != null) {
          other = db.accounts().bySshUserName(newName);
        } else {
          other = null;
        }

        if (other != null) {
          if (other.getId().equals(me.getId())) {
            return VoidResult.INSTANCE;
          } else {
            throw new Failure(new NameAlreadyUsedException());
          }
        }

        final String oldName = me.getSshUserName();
        me.setSshUserName(newName);
        db.accounts().update(Collections.singleton(me));
        uncacheSshKeys(oldName);
        uncacheSshKeys(newName);
        accountCache.evict(me.getId());
        return VoidResult.INSTANCE;
      }
    });
  }

  public void myExternalIds(AsyncCallback<List<AccountExternalId>> callback) {
    externalIdDetailFactory.create().to(callback);
  }

  @Override
  public void myGroups(final AsyncCallback<List<AccountGroup>> callback) {
    myGroupsFactory.create().to(callback);
  }

  public void deleteExternalIds(final Set<AccountExternalId.Key> keys,
      final AsyncCallback<Set<AccountExternalId.Key>> callback) {
    run(callback, new Action<Set<AccountExternalId.Key>>() {
      public Set<AccountExternalId.Key> run(final ReviewDb db)
          throws OrmException, Failure {
        // Determine the records we will allow the user to remove.
        //
        final Account.Id me = getAccountId();
        final Map<AccountExternalId.Key, AccountExternalId> all =
            db.accountExternalIds()
                .toMap(db.accountExternalIds().byAccount(me));

        // Don't permit deletes unless they are for our own account
        //
        for (final AccountExternalId.Key keyId : keys) {
          if (!all.containsKey(keyId))
            throw new Failure(new NoSuchEntityException());
        }

        final AccountExternalId mostRecent =
            AccountExternalId.mostRecent(all.values());
        final Set<AccountExternalId.Key> removed =
            new HashSet<AccountExternalId.Key>();
        final List<AccountExternalId> toDelete =
            new ArrayList<AccountExternalId>();
        for (final AccountExternalId.Key k : keys) {
          final AccountExternalId e = all.get(k);
          if (e == null) {
            // Its already gone, tell the client its gone
            //
            removed.add(k);

          } else if (e == mostRecent) {
            // Don't delete the most recently accessed identity; the
            // user might lock themselves out of the account.
            //
            continue;

          } else {
            toDelete.add(e);
            removed.add(e.getKey());
          }
        }

        if (!toDelete.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.accountExternalIds().delete(toDelete, txn);
          txn.commit();
          accountCache.evict(me);
          for (AccountExternalId e : toDelete) {
            byEmailCache.evict(e.getEmailAddress());
          }
        }

        return removed;
      }
    });
  }

  public void updateContact(final String name, final String emailAddr,
      final ContactInformation info, final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      public Account run(ReviewDb db) throws OrmException, Failure {
        final Account me = db.accounts().get(getAccountId());
        final String oldEmail = me.getPreferredEmail();
        if (realm.allowsEdit(Account.FieldName.FULL_NAME)) {
          me.setFullName(name != null && !name.isEmpty() ? name : null);
        }
        me.setPreferredEmail(emailAddr);
        if (useContactInfo) {
          if (ContactInformation.hasAddress(info)
              || (me.isContactFiled() && ContactInformation.hasData(info))) {
            me.setContactFiled();
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

  public void enterAgreement(final ContributorAgreement.Id id,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final ContributorAgreement cla = db.contributorAgreements().get(id);
        if (cla == null || !cla.isActive()) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountAgreement a =
            new AccountAgreement(new AccountAgreement.Key(getAccountId(), id));
        if (cla.isAutoVerify()) {
          a.review(AccountAgreement.Status.VERIFIED, null);
        }
        db.accountAgreements().insert(Collections.singleton(a));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void registerEmail(final String address,
      final AsyncCallback<VoidResult> cb) {
    try {
      final RegisterNewEmailSender sender;
      sender = registerNewEmailFactory.create(address);
      sender.send();
      cb.onSuccess(VoidResult.INSTANCE);
    } catch (EmailException e) {
      log.error("Cannot send email verification message to " + address, e);
      cb.onFailure(e);
    } catch (RuntimeException e) {
      log.error("Cannot send email verification message to " + address, e);
      cb.onFailure(e);
    }
  }

  public void validateEmail(final String token,
      final AsyncCallback<VoidResult> callback) {
    try {
      final ValidToken t =
          authConfig.getEmailRegistrationToken().checkToken(token, null);
      if (t == null || t.getData() == null || "".equals(t.getData())) {
        callback.onFailure(new IllegalStateException("Invalid token"));
        return;
      }
      final String newEmail = new String(Base64.decode(t.getData()), "UTF-8");
      if (!newEmail.contains("@")) {
        callback.onFailure(new IllegalStateException("Invalid token"));
        return;
      }
      accountManager.link(getAccountId(), AuthRequest.forEmail(newEmail));
      callback.onSuccess(VoidResult.INSTANCE);
    } catch (XsrfException e) {
      callback.onFailure(e);
    } catch (UnsupportedEncodingException e) {
      callback.onFailure(e);
    } catch (AccountException e) {
      callback.onFailure(e);
    }
  }
}
