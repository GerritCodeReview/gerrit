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
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.spearce.jgit.util.Base64;
import org.spearce.jgit.util.NB;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.SecureRandom;

@Singleton
class WebSessionManager {
  private final int tokenLen;
  private final SecureRandom prng;
  private final Cache self;

  @Inject
  WebSessionManager(final CacheManager mgr) {
    tokenLen = 40 - 4;
    prng = new SecureRandom();
    self = mgr.getCache("web_sessions");
  }

  Element create(final Account.Id who) {
    final int accountId = who.get();
    final byte[] rnd = new byte[tokenLen];
    prng.nextBytes(rnd);

    final byte[] buf = new byte[4 + tokenLen];
    NB.encodeInt32(buf, 0, accountId);
    System.arraycopy(rnd, 0, buf, 4, rnd.length);

    final String token = Base64.encodeBytes(rnd, Base64.DONT_BREAK_LINES);
    final Val v = new Val(who);
    final Element m = new Element(new Key(token), v);
    self.put(m);
    return m;
  }

  Element get(final String token) {
    if (token != null && !"".equals(token)) {
      try {
        return self.get(new Key(token));
      } catch (IllegalStateException e) {
        return null;
      } catch (CacheException e) {
        return null;
      }
    } else {
      return null;
    }
  }

  void destroy(final Element cacheEntry) {
    self.remove(cacheEntry.getKey());
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
