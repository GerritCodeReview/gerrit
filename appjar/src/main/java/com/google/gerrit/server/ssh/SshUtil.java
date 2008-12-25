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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.util.Buffer;
import org.spearce.jgit.lib.Constants;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Utilities to support SSH operations. */
public class SshUtil {
  /**
   * Parse a public key into its Java type.
   * 
   * @param key the account key to parse.
   * @return the valid public key object.
   * @throws InvalidKeySpecException the key supplied is not a valid SSH key.
   * @throws NoSuchAlgorithmException the JVM is missing the key algorithm.
   * @throws NoSuchProviderException the JVM is missing the provider.
   */
  public static PublicKey parse(final AccountSshKey key)
      throws NoSuchAlgorithmException, InvalidKeySpecException,
      NoSuchProviderException {
    final String s = key.getEncodedKey();
    if (s == null) {
      throw new InvalidKeySpecException("No key string");
    }
    final byte[] bin = Base64.decodeBase64(Constants.encodeASCII(s));
    return new Buffer(bin).getPublicKey();
  }

  private static final Map<String, List<AccountSshKey>> keys;

  static {
    keys = new LinkedHashMap<String, List<AccountSshKey>>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(
          final Entry<String, List<AccountSshKey>> eldest) {
        return 256 <= size();
      }
    };
  }

  /** Invalidate all cached keys for the given account. */
  public static void invalidate(final Account acct) {
    synchronized (keys) {
      keys.remove(acct.getPreferredEmail());
    }
  }

  /** Locate keys for the requested account whose email matches the name given. */
  public static List<AccountSshKey> keysFor(final SchemaFactory<ReviewDb> rdf,
      final String username) {
    synchronized (keys) {
      final List<AccountSshKey> r = keys.get(username);
      if (r != null) {
        return r;
      }
    }

    List<AccountSshKey> kl;
    try {
      final ReviewDb db = rdf.open();
      try {
        final List<Account> matches =
            db.accounts().byPreferredEmail(username).toList();
        if (matches.isEmpty()) {
          return Collections.<AccountSshKey> emptyList();
        } else if (matches.size() > 1) {
          // TODO log accounts with duplicate emails
          return Collections.<AccountSshKey> emptyList();
        }
        kl = db.accountSshKeys().valid(matches.get(0).getId()).toList();
      } finally {
        db.close();
      }
    } catch (OrmException err) {
      // TODO log database query error
      return Collections.<AccountSshKey> emptyList();
    }

    kl = Collections.unmodifiableList(kl);
    synchronized (keys) {
      keys.put(username, kl);
    }
    return kl;
  }
}
