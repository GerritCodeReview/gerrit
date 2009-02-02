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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;

/**
 * Authenticates by public key through {@link AccountSshKey} entities.
 * <p>
 * The username supplied by the client must be the user's preferred email
 * address, as listed in their Account entity. Only keys listed under that
 * account as authorized keys are permitted to access the account.
 */
class DatabasePubKeyAuth implements PublickeyAuthenticator {
  public boolean hasKey(final String username, final PublicKey inkey,
      final ServerSession session) {
    AccountSshKey matched = null;

    for (final AccountSshKey k : SshUtil.keysFor(username)) {
      if (match(username, k, inkey)) {
        if (matched == null) {
          matched = k;

        } else if (!matched.getAccount().equals(k.getAccount())) {
          // Don't permit keys to authenticate to different accounts
          // that have the same username and public key.
          //
          // We'd have to pick one at random, yielding unpredictable
          // behavior for the end-user.
          //
          return false;
        }
      }
    }

    if (matched != null) {
      updateLastUsed(matched);
      session.setAttribute(SshUtil.CURRENT_ACCOUNT, matched.getAccount());
      return true;
    }
    return false;
  }

  private boolean match(final String username, final AccountSshKey k,
      final PublicKey inkey) {
    try {
      return SshUtil.parse(k).equals(inkey);
    } catch (NoSuchAlgorithmException e) {
      markInvalid(username, k);
      return false;
    } catch (InvalidKeySpecException e) {
      markInvalid(username, k);
      return false;
    } catch (NoSuchProviderException e) {
      markInvalid(username, k);
      return false;
    } catch (RuntimeException e) {
      markInvalid(username, k);
      return false;
    }
  }

  private void markInvalid(final String username, final AccountSshKey k) {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        k.setInvalid();
        db.accountSshKeys().update(Collections.singleton(k));
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      // TODO log mark invalid failure
    } finally {
      SshUtil.invalidate(username);
    }
  }

  private void updateLastUsed(final AccountSshKey k) {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        k.setLastUsedOn();
        db.accountSshKeys().update(Collections.singleton(k));
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      // TODO log update last used failure
    }
  }
}
