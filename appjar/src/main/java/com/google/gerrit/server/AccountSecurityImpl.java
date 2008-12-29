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

package com.google.gerrit.server;

import com.google.gerrit.client.account.AccountSecurity;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gerrit.server.ssh.SshUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AccountSecurityImpl extends BaseServiceImplementation implements
    AccountSecurity {
  public AccountSecurityImpl(final SchemaFactory<ReviewDb> rdf) {
    super(rdf);
  }

  public void mySshKeys(final AsyncCallback<List<AccountSshKey>> callback) {
    run(callback, new Action<List<AccountSshKey>>() {
      public List<AccountSshKey> run(ReviewDb db) throws OrmException {
        return db.accountSshKeys().byAccount(RpcUtil.getAccountId()).toList();
      }
    });
  }

  public void addSshKey(final String keyText,
      final AsyncCallback<AccountSshKey> callback) {
    run(callback, new Action<AccountSshKey>() {
      public AccountSshKey run(final ReviewDb db) throws OrmException {
        int max = 0;
        final Account.Id me = RpcUtil.getAccountId();
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
          newKey.setInvalid();
        } catch (InvalidKeySpecException e) {
          newKey.setInvalid();
        } catch (NoSuchProviderException e) {
          newKey.setInvalid();
        }
        db.accountSshKeys().insert(Collections.singleton(newKey));
        return newKey;
      }
    });
  }

  public void deleteSshKeys(final Set<AccountSshKey.Id> ids,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Account.Id me = RpcUtil.getAccountId();
        for (final AccountSshKey.Id keyId : ids) {
          if (!me.equals(keyId.getParentKey()))
            throw new Failure(new NoSuchEntityException());
        }

        final List<AccountSshKey> k = db.accountSshKeys().get(ids).toList();
        if (!k.isEmpty()) {
          final Transaction txn = db.beginTransaction();
          db.accountSshKeys().delete(k, txn);
          txn.commit();
        }

        return VoidResult.INSTANCE;
      }
    });
  }
}
