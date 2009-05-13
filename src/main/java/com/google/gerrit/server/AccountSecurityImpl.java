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
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.reviewdb.TrustedExternalId;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.ContactInformationStoreException;
import com.google.gerrit.client.rpc.InvalidSshKeyException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.ssh.SshUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

class AccountSecurityImpl extends BaseServiceImplementation implements
    AccountSecurity {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final GerritServer server;

  AccountSecurityImpl(final GerritServer gs) {
    server = gs;
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
    final SelfPopulatingCache c = server.getSshKeysCache();
    final Account a = Common.getAccountCache().get(me, db);
    if (a != null && a.getSshUserName() != null) {
      c.remove(a.getSshUserName());
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
        final SystemConfig.LoginType loginType =
            Common.getGerritConfig().getLoginType();
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
        me.setFullName(fullName);
        me.setPreferredEmail(emailAddr);
        if (Common.getGerritConfig().isUseContactInfo()) {
          if (ContactInformation.hasAddress(info)
              || (me.isContactFiled() && ContactInformation.hasData(info))) {
            me.setContactFiled();
          }
          if (ContactInformation.hasData(info)) {
            try {
              EncryptedContactStore.store(me, info);
            } catch (ContactInformationStoreException e) {
              throw new Failure(e);
            }
          }
        }
        db.accounts().update(Collections.singleton(me));
        Common.getAccountCache().invalidate(me.getId());
        return me;
      }
    });
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
    final StringBuffer url = req.getRequestURL();
    final StringBuilder m = new StringBuilder();

    url.setLength(url.lastIndexOf("/")); // cut "AccountSecurity"
    url.setLength(url.lastIndexOf("/")); // cut "rpc"
    url.setLength(url.lastIndexOf("/")); // cut "gerrit"
    url.append("/Gerrit#VE,");

    try {
      url.append(server.getEmailRegistrationToken().newToken(
          Base64.encodeBytes(address.getBytes("UTF-8"))));
    } catch (XsrfException e) {
      cb.onFailure(e);
      return;
    } catch (UnsupportedEncodingException e) {
      cb.onFailure(e);
      return;
    }

    m.append("Welcome to Gerrit Code Review at ");
    m.append(req.getServerName());
    m.append(".\n");

    m.append("\n");
    m.append("To add a verified email address to your user account, please\n");
    m.append("click on the following link:\n");
    m.append("\n");
    m.append(url);
    m.append("\n");

    m.append("\n");
    m.append("If you have received this mail in error,"
        + " you do not need to take any\n");
    m.append("action to cancel the account."
        + " The account will not be activated, and\n");
    m.append("you will not receive any further emails.\n");

    m.append("\n");
    m.append("If clicking the link above does not work,"
        + " copy and paste the URL in a\n");
    m.append("new browser window instead.\n");

    m.append("\n");
    m.append("This is a send-only email address."
        + "  Replies to this message will not\n");
    m.append("be read or answered.\n");

    final javax.mail.Session out = server.getOutgoingMail();
    if (out == null) {
      cb.onFailure(new IllegalStateException("Outgoing mail is disabled"));
      return;
    }
    try {
      final MimeMessage msg = new MimeMessage(out);
      msg.setFrom(new InternetAddress(gi.getEmailAddress(), gi.getName()));
      msg.setRecipients(Message.RecipientType.TO, address);
      msg.setSubject("[Gerrit Code Review] Email Verification");
      msg.setSentDate(new Date());
      msg.setText(m.toString());
      Transport.send(msg);
      cb.onSuccess(VoidResult.INSTANCE);
    } catch (MessagingException e) {
      log.error("Cannot send email verification message to " + address, e);
      cb.onFailure(e);
    } catch (UnsupportedEncodingException e) {
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
