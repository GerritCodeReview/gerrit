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

package com.google.gerrit.server.http;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.cache.Cache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.spearce.jgit.util.Base64;
import org.spearce.jgit.util.NB;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Singleton
class WebSessionManager {
  static final String CACHE_NAME = "web_sessions";

  private final int tokenLen;
  private final SecureRandom prng;
  private final Cache<Key, Val> self;

  @Inject
  WebSessionManager(@Named(CACHE_NAME) final Cache<Key, Val> cache) {
    tokenLen = 40 - 4;
    prng = new SecureRandom();
    self = cache;
  }

  void updateRefreshCookieAt(final Val val) {
    final long now = System.currentTimeMillis();
    val.refreshCookieAt = now + self.getTimeToIdle(TimeUnit.MILLISECONDS) / 2;
  }

  Key createKey(final Account.Id who) {
    final int accountId = who.get();
    final byte[] rnd = new byte[tokenLen];
    prng.nextBytes(rnd);

    final byte[] buf = new byte[4 + tokenLen];
    NB.encodeInt32(buf, 0, accountId);
    System.arraycopy(rnd, 0, buf, 4, rnd.length);

    return new Key(Base64.encodeBytes(rnd, Base64.DONT_BREAK_LINES));
  }

  Val createVal(final Key key, final Account.Id who) {
    final Val val = new Val(who);
    self.put(key, val);
    return val;
  }

  Val get(final Key key) {
    return self.get(key);
  }

  void destroy(final Key key) {
    self.remove(key);
  }

  static final class Key implements Serializable {
    static final long serialVersionUID = 1L;

    transient String token;

    Key(final String t) {
      token = t;
    }

    @Override
    public int hashCode() {
      return token.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Key && token.equals(((Key) obj).token);
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
      out.writeUTF(token);
    }

    private void readObject(final ObjectInputStream in) throws IOException {
      token = in.readUTF();
    }
  }

  static final class Val implements Serializable {
    static final long serialVersionUID = Key.serialVersionUID;

    transient Account.Id accountId;
    transient long refreshCookieAt;

    Val(final Account.Id accountId) {
      this.accountId = accountId;
    }

    int getCookieAge() {
      return (int) ((refreshCookieAt - System.currentTimeMillis()) / 1000L);
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
      out.writeInt(accountId.get());
      out.writeLong(refreshCookieAt);
    }

    private void readObject(final ObjectInputStream in) throws IOException {
      accountId = new Account.Id(in.readInt());
      refreshCookieAt = in.readLong();
    }
  }
}
