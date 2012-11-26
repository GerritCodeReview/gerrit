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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthRequest;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.InvalidCredentialsException;
import com.google.gerrit.server.auth.MissingCredentialsException;
import com.google.gerrit.server.auth.UnknownUserException;
import com.google.gerrit.server.auth.UserNotAllowedException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.auth.SshAuthRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authenticates by public key through {@link AccountSshKey} entities. */
@Singleton
class DatabasePubKeyAuth implements AuthBackend {
  private static final Logger log = LoggerFactory.getLogger(DatabasePubKeyAuth.class);

  private final SshKeyCacheImpl sshKeyCache;
  private final Config config;
  private final Set<PublicKey> myHostKeys;
  private volatile PeerKeyCache peerKeyCache;

  @Inject
  DatabasePubKeyAuth(
      final SshKeyCacheImpl skc,
      final SitePaths site,
      final KeyPairProvider hostKeyProvider,
      final @GerritServerConfig Config cfg) {
    sshKeyCache = skc;
    config = cfg;
    myHostKeys = myHostKeys(hostKeyProvider);
    peerKeyCache = new PeerKeyCache(site.peer_keys);
  }

  private static Set<PublicKey> myHostKeys(KeyPairProvider p) {
    final Set<PublicKey> keys = new HashSet<>(6);
    addPublicKey(keys, p, KeyPairProvider.SSH_ED25519);
    addPublicKey(keys, p, KeyPairProvider.ECDSA_SHA2_NISTP256);
    addPublicKey(keys, p, KeyPairProvider.ECDSA_SHA2_NISTP384);
    addPublicKey(keys, p, KeyPairProvider.ECDSA_SHA2_NISTP521);
    addPublicKey(keys, p, KeyPairProvider.SSH_RSA);
    addPublicKey(keys, p, KeyPairProvider.SSH_DSS);
    return keys;
  }

  private static void addPublicKey(
      final Collection<PublicKey> out, KeyPairProvider p, String type) {
    final KeyPair pair = p.loadKey(type);
    if (pair != null && pair.getPublic() != null) {
      out.add(pair.getPublic());
    }
  }

  private Set<PublicKey> getPeerKeys() {
    PeerKeyCache p = peerKeyCache;
    if (!p.isCurrent()) {
      p = p.reload();
      peerKeyCache = p;
    }
    return p.keys;
  }

  private SshKeyCacheEntry find(Iterable<SshKeyCacheEntry> keyList, PublicKey suppliedKey) {
    for (SshKeyCacheEntry k : keyList) {
      if (k.match(suppliedKey)) {
        return k;
      }
    }
    return null;
  }

  private static class PeerKeyCache {
    private final Path path;
    private final long modified;
    final Set<PublicKey> keys;

    PeerKeyCache(Path path) {
      this.path = path;
      this.modified = FileUtil.lastModified(path);
      this.keys = read(path);
    }

    private static Set<PublicKey> read(Path path) {
      try (BufferedReader br = Files.newBufferedReader(path, UTF_8)) {
        final Set<PublicKey> keys = new HashSet<>();
        String line;
        while ((line = br.readLine()) != null) {
          line = line.trim();
          if (line.startsWith("#") || line.isEmpty()) {
            continue;
          }

          try {
            byte[] bin = Base64.decodeBase64(line.getBytes(ISO_8859_1));
            keys.add(new ByteArrayBuffer(bin).getRawPublicKey());
          } catch (RuntimeException | SshException e) {
            logBadKey(path, line, e);
          }
        }
        return Collections.unmodifiableSet(keys);
      } catch (NoSuchFileException noFile) {
        return Collections.emptySet();
      } catch (IOException err) {
        log.error("Cannot read " + path, err);
        return Collections.emptySet();
      }
    }

    private static void logBadKey(Path path, String line, Exception e) {
      log.warn("Invalid key in " + path + ":\n  " + line, e);
    }

    boolean isCurrent() {
      return modified == FileUtil.lastModified(path);
    }

    PeerKeyCache reload() {
      return new PeerKeyCache(path);
    }
  }

  @Override
  public String getDomain() {
    return "gerrit";
  }

  @Override
  public AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException, UnknownUserException,
          UserNotAllowedException {

    if (!(req instanceof SshAuthRequest)) {
      throw new MissingCredentialsException(
          String.format("Invalid authentication request type %s", req));
    }

    String username = req.getUsername().get();
    PublicKey suppliedKey = ((SshAuthRequest) req).getPubKey();

    if (PeerDaemonUser.USER_NAME.equals(username)) {
      if (myHostKeys.contains(suppliedKey) || getPeerKeys().contains(suppliedKey)) {
        return new AuthUser(AuthUser.UUID.create(getDomain(), username), username);
      }
      throw new InvalidCredentialsException(
          String.format("No matching key for user: %s", username));
    }

    if (config.getBoolean("auth", "userNameToLowerCase", false)) {
      username = username.toLowerCase(Locale.US);
    }

    final Iterable<SshKeyCacheEntry> keyList = sshKeyCache.get(username);
    final SshKeyCacheEntry key = find(keyList, suppliedKey);
    if (key == null) {
      if (keyList == SshKeyCacheImpl.NO_SUCH_USER) {
        throw new UnknownUserException(String.format("User '%s' not found", username));
      } else if (keyList == SshKeyCacheImpl.NO_KEYS) {
        throw new MissingCredentialsException(
            String.format("Key list is empty for user %s", username));
      } else {
        throw new InvalidCredentialsException(
            String.format("no-matching-key for user: %s", username));
      }
    }
    try {
      ((SshAuthRequest) req).checkPassword(key.getPublicKey());
    } catch (AuthException e) {
      throw new InvalidCredentialsException(e);
    }

    // Double check that all of the keys are for the same user account.
    // This should have been true when the cache factory method loaded
    // the list into memory, but we want to be extra paranoid about our
    // security check to ensure there aren't two users sharing the same
    // user name on the server.
    //
    for (final SshKeyCacheEntry otherKey : keyList) {
      if (!key.getAccount().equals(otherKey.getAccount())) {
        throw new InvalidCredentialsException("keys-cross-accounts");
      }
    }

    return new AuthUser(AuthUser.UUID.create(getDomain(), username), username);
  }
}
