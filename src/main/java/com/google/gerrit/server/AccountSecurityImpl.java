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

import com.google.gerrit.client.account.AccountSecurity;
import com.google.gerrit.client.account.ExternalIdDetail;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountAgreement;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.TrustedExternalId;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.ContactInformationStoreException;
import com.google.gerrit.client.rpc.InvalidSshKeyException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.ssh.SshUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

class AccountSecurityImpl extends BaseServiceImplementation implements
    AccountSecurity {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final GerritServer server;
  private final ContactStore contactStore;
  private final RegisterNewEmailSender.Factory emailSenderFactory;

  @Inject
  AccountSecurityImpl(final SchemaFactory<ReviewDb> sf, final GerritServer gs,
      final ContactStore cs, final RegisterNewEmailSender.Factory esf) {
    super(sf);
    server = gs;
    contactStore = cs;
    this.emailSenderFactory = esf;
  }

  public void mySshKeys(final AsyncCallback<List<AccountSshKey>> callback) {
    run(callback, new Action<List<AccountSshKey>>() {
      public List<AccountSshKey> run(ReviewDb db) throws OrmException {
        return db.accountSshKeys().byAccount(Common.getAccountId()).toList();
      }
    });
  }

  public void addSshKey(final String keyText,
      final AsyncCallback<AccountSshKey> callback) {
    run(callback, new Action<AccountSshKey>() {
      public AccountSshKey run(final ReviewDb db) throws OrmException, Failure {
        int max = 0;
        final Account.Id me = Common.getAccountId();
        for (final AccountSshKey k : db.accountSshKeys().byAccount(me)) {
          max = Math.max(max, k.getKey().get());
        }

        String keyStr = keyText;
        if (keyStr.startsWith("---- BEGIN SSH2 PUBLIC KEY ----")) {
          keyStr = SshUtil.toOpenSshPublicKey(keyStr);
        }

        final AccountSshKey newKey =
            new AccountSshKey(new AccountSshKey.Id(me, max + 1), keyStr);
        try {
          SshUtil.parse(newKey);
        } catch (NoSuchAlgorithmException e) {
          throw new Failure(new InvalidSshKeyException());
        } catch (InvalidKeySpecException e) {
          throw new Failure(new InvalidSshKeyException());
        } catch (NoSuchProviderException e) {
          log.error("Cannot parse SSH key", e);
          throw new Failure(new InvalidSshKeyException());
        }
        db.accountSshKeys().insert(Collections.singleton(newKey));
        uncacheSshKeys(me, db);
        return newKey;
      }
    });
  }

  public void deleteSshKeys(final Set<AccountSshKey.Id> ids,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = Common.getAccountId();
        for (final AccountSshKey.Id keyId : ids) {
          if (!me.equals(keyId.getParentKey()))
            throw new Failure(new NoSuchEntityException());
        }

        final List<AccountSshKey> k = db.accountSshKeys().get(ids).toList();
        if (!k.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.accountSshKeys().delete(k, txn);
          txn.commit();
          uncacheSshKeys(me, db);
        }

        return VoidResult.INSTANCE;
      }
    });
  }

  private void uncacheSshKeys(final Account.Id me, final ReviewDb db) {
    final Account a = Common.getAccountCache().get(me, db);
    if (a != null) {
      uncacheSshKeys(a.getSshUserName());
    }
  }

  private void uncacheSshKeys(final String userName) {
    if (userName != null) {
      server.getSshKeysCache().remove(userName);
    }
  }

  public void myExternalIds(AsyncCallback<ExternalIdDetail> callback) {
    run(callback, new Action<ExternalIdDetail>() {
      public ExternalIdDetail run(ReviewDb db) throws OrmException {
        final Account.Id me = Common.getAccountId();
        final List<TrustedExternalId> trusted =
            Common.getGroupCache().getTrustedExternalIds(db);
        final List<AccountExternalId> myIds =
            db.accountExternalIds().byAccount(me).toList();
        return new ExternalIdDetail(myIds, trusted);
      }
    });
  }

  public void deleteExternalIds(final Set<AccountExternalId.Key> keys,
      final AsyncCallback<Set<AccountExternalId.Key>> callback) {
    run(callback, new Action<Set<AccountExternalId.Key>>() {
      public Set<AccountExternalId.Key> run(final ReviewDb db)
          throws OrmException, Failure {
        // Don't permit deletes unless they are for our own account
        //
        final Account.Id me = Common.getAccountId();
        for (final AccountExternalId.Key keyId : keys) {
          if (!me.equals(keyId.getParentKey()))
            throw new Failure(new NoSuchEntityException());
        }

        // Determine the records we will allow the user to remove.
        //
        final Map<AccountExternalId.Key, AccountExternalId> all =
            db.accountExternalIds()
                .toMap(db.accountExternalIds().byAccount(me));
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

          } else if (e.canUserDelete()) {
            toDelete.add(e);
            removed.add(e.getKey());
          }
        }

        if (!toDelete.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.accountExternalIds().delete(toDelete, txn);
          txn.commit();
          Common.getGroupCache().invalidate(me);
        }

        return removed;
      }
    });
  }

  public void updateContact(final String fullName, final String emailAddr,
      final ContactInformation info, final AsyncCallback<Account> callback) {
    run(callback, new Action<Account>() {
      public Account run(ReviewDb db) throws OrmException, Failure {
        final Account me = db.accounts().get(Common.getAccountId());
        final String oldUser = me.getSshUserName();
        me.setFullName(fullName);
        me.setPreferredEmail(emailAddr);
        if (Common.getGerritConfig().isUseContactInfo()) {
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
        if (!eq(oldUser, me.getSshUserName())) {
          uncacheSshKeys(oldUser);
          uncacheSshKeys(me.getSshUserName());
        }
        Common.getAccountCache().invalidate(me.getId());
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
            new AccountAgreement(new AccountAgreement.Key(
                Common.getAccountId(), id));
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
    final PersonIdent gi = server.newGerritPersonIdent();
    final HttpServletRequest req =
        GerritJsonServlet.getCurrentCall().getHttpServletRequest();
    try {
      final RegisterNewEmailSender sender;
      sender = this.emailSenderFactory.create(address);
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
    final String address;
    try {
      final ValidToken t =
          server.getEmailRegistrationToken().checkToken(token, null);
      if (t == null || t.getData() == null || "".equals(t.getData())) {
        callback.onFailure(new IllegalStateException("Invalid token"));
        return;
      }
      address = new String(Base64.decode(t.getData()), "UTF-8");
      if (!address.contains("@")) {
        callback.onFailure(new IllegalStateException("Invalid token"));
        return;
      }
    } catch (XsrfException e) {
      callback.onFailure(e);
      return;
    } catch (UnsupportedEncodingException e) {
      callback.onFailure(e);
      return;
    }

    run(callback, new Action<VoidResult>() {
      public VoidResult run(ReviewDb db) throws OrmException {
        final Account.Id me = Common.getAccountId();
        final List<AccountExternalId> exists =
            db.accountExternalIds().byAccountEmail(me, address).toList();
        if (!exists.isEmpty()) {
          return VoidResult.INSTANCE;
        }

        try {
          final AccountExternalId id =
              new AccountExternalId(new AccountExternalId.Key(me, "mailto:"
                  + address));
          id.setEmailAddress(address);
          db.accountExternalIds().insert(Collections.singleton(id));
        } catch (OrmDuplicateKeyException e) {
          // Ignore a duplicate registration
        }

        final Account a = db.accounts().get(me);
        a.setPreferredEmail(address);
        db.accounts().update(Collections.singleton(a));
        Common.getAccountCache().invalidate(me);
        return VoidResult.INSTANCE;
      }
    });
  }
}
