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

package com.google.gerrit.sshd;

import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Authenticates by public key through {@link AccountSshKey} entities.
 */
@Singleton
class DatabasePubKeyAuth implements PublickeyAuthenticator {
  private static final Logger log =
      LoggerFactory.getLogger(DatabasePubKeyAuth.class);

  private final SshKeyCacheImpl sshKeyCache;
  private final SshLog sshLog;
  private final IdentifiedUser.GenericFactory userFactory;
  private final PeerDaemonUser.Factory peerFactory;
  private final Config config;
  private final SshScope sshScope;
  private final Set<PublicKey> myHostKeys;
  private volatile PeerKeyCache peerKeyCache;

  @Inject
  DatabasePubKeyAuth(final SshKeyCacheImpl skc, final SshLog l,
      final IdentifiedUser.GenericFactory uf, final PeerDaemonUser.Factory pf,
      final SitePaths site, final KeyPairProvider hostKeyProvider,
      final @GerritServerConfig Config cfg, final SshScope s) {
    sshKeyCache = skc;
    sshLog = l;
    userFactory = uf;
    peerFactory = pf;
    config = cfg;
    sshScope = s;
    myHostKeys = myHostKeys(hostKeyProvider);
    peerKeyCache = new PeerKeyCache(site.peer_keys);
  }

  private static Set<PublicKey> myHostKeys(KeyPairProvider p) {
    final Set<PublicKey> keys = new HashSet<PublicKey>(2);
    addPublicKey(keys, p, KeyPairProvider.SSH_RSA);
    addPublicKey(keys, p, KeyPairProvider.SSH_DSS);
    return keys;
  }

  private static void addPublicKey(final Collection<PublicKey> out,
      final KeyPairProvider p, final String type) {
    final KeyPair pair = p.loadKey(type);
    if (pair != null && pair.getPublic() != null) {
      out.add(pair.getPublic());
    }
  }

  public boolean authenticate(String username,
      final PublicKey suppliedKey, final ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);

    if (PeerDaemonUser.USER_NAME.equals(username)) {
      if (myHostKeys.contains(suppliedKey)
          || getPeerKeys().contains(suppliedKey)) {
        PeerDaemonUser user = peerFactory.create(sd.getRemoteAddress());
        return SshUtil.success(username, session, sshScope, sshLog, sd, user);

      } else {
        sd.authenticationError(username, "no-matching-key");
        return false;
      }
    }

    if (config.getBoolean("auth", "userNameToLowerCase", false)) {
      username = username.toLowerCase(Locale.US);
    }

    final Iterable<SshKeyCacheEntry> keyList = sshKeyCache.get(username);
    final SshKeyCacheEntry key = find(keyList, suppliedKey);
    if (key == null) {
      final String err;
      if (keyList == SshKeyCacheImpl.NO_SUCH_USER) {
        err = "user-not-found";
      } else if (keyList == SshKeyCacheImpl.NO_KEYS) {
        err = "key-list-empty";
      } else {
        err = "no-matching-key";
      }
      sd.authenticationError(username, err);
      return false;
    }

    // Double check that all of the keys are for the same user account.
    // This should have been true when the cache factory method loaded
    // the list into memory, but we want to be extra paranoid about our
    // security check to ensure there aren't two users sharing the same
    // user name on the server.
    //
    for (final SshKeyCacheEntry otherKey : keyList) {
      if (!key.getAccount().equals(otherKey.getAccount())) {
        sd.authenticationError(username, "keys-cross-accounts");
        return false;
      }
    }

    if (!SshUtil.createUser(sd, userFactory, key.getAccount())
        .getAccount().isActive()) {
      sd.authenticationError(username, "inactive-account");
      return false;
    }

    return SshUtil.success(username, session, sshScope, sshLog, sd,
        SshUtil.createUser(sd, userFactory, key.getAccount()));
  }

  private Set<PublicKey> getPeerKeys() {
    PeerKeyCache p = peerKeyCache;
    if (!p.isCurrent()) {
      p = p.reload();
      peerKeyCache = p;
    }
    return p.keys;
  }

  private SshKeyCacheEntry find(final Iterable<SshKeyCacheEntry> keyList,
      final PublicKey suppliedKey) {
    for (final SshKeyCacheEntry k : keyList) {
      if (k.match(suppliedKey)) {
        return k;
      }
    }
    return null;
  }

  private static class PeerKeyCache {
    private final File path;
    private final long modified;
    final Set<PublicKey> keys;

    PeerKeyCache(final File path) {
      this.path = path;
      this.modified = path.lastModified();
      this.keys = read(path);
    }

    private static Set<PublicKey> read(File path) {
      try {
        final BufferedReader br = new BufferedReader(new FileReader(path));
        try {
          final Set<PublicKey> keys = new HashSet<PublicKey>();
          String line;
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) {
              continue;
            }

            try {
              byte[] bin = Base64.decodeBase64(line.getBytes("ISO-8859-1"));
              keys.add(new Buffer(bin).getRawPublicKey());
            } catch (RuntimeException e) {
              logBadKey(path, line, e);
            } catch (SshException e) {
              logBadKey(path, line, e);
            }
          }
          return Collections.unmodifiableSet(keys);
        } finally {
          br.close();
        }
      } catch (FileNotFoundException noFile) {
        return Collections.emptySet();

      } catch (IOException err) {
        log.error("Cannot read " + path, err);
        return Collections.emptySet();
      }
    }

    private static void logBadKey(File path, String line, Exception e) {
      log.warn("Invalid key in " + path + ":\n  " + line, e);
    }

    boolean isCurrent() {
      return path.lastModified() == modified;
    }

    PeerKeyCache reload() {
      return new PeerKeyCache(path);
    }
  }
}
