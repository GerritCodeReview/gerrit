// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gwtorm.client.Column;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

class SshKeyCacheEntry {
  private final static PublicKey INVALID_KEY = new PublicKey() {
    @Override
    public String getAlgorithm() {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getEncoded() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getFormat() {
      throw new UnsupportedOperationException();
    }
  };

  @Column(id = 1)
  protected AccountSshKey.Id id;

  @Column(id = 2)
  protected AccountSshKey key;

  private transient volatile PublicKey publicKey;

  SshKeyCacheEntry(final AccountSshKey.Id i, final AccountSshKey k)
      throws NoSuchAlgorithmException, InvalidKeySpecException,
      NoSuchProviderException {
    id = i;
    key = k;
    publicKey = SshUtil.parse(k);
  }

  Account.Id getAccount() {
    return id.getParentKey();
  }

  boolean match(final PublicKey inkey) {
    if (publicKey == null) {
      try {
        publicKey = SshUtil.parse(key);
      } catch (OutOfMemoryError e) {
        // This is the only case where we assume the problem has nothing
        // to do with the key object, and instead we must abort this load.
        //
        throw e;
      } catch (Throwable e) {
        publicKey = INVALID_KEY;
      }
    }
    return publicKey.equals(inkey);
  }
}
