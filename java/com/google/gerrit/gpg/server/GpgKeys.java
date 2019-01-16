// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.gpg.server;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.gpg.BouncyCastleUtil;
import com.google.gerrit.gpg.CheckResult;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.GerritPublicKeyChecker;
import com.google.gerrit.gpg.PublicKeyChecker;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.util.NB;

@Singleton
public class GpgKeys implements ChildCollection<AccountResource, GpgKey> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DynamicMap<RestView<GpgKey>> views;
  private final Provider<CurrentUser> self;
  private final Provider<PublicKeyStore> storeProvider;
  private final GerritPublicKeyChecker.Factory checkerFactory;
  private final ExternalIds externalIds;

  @Inject
  GpgKeys(
      DynamicMap<RestView<GpgKey>> views,
      Provider<CurrentUser> self,
      Provider<PublicKeyStore> storeProvider,
      GerritPublicKeyChecker.Factory checkerFactory,
      ExternalIds externalIds) {
    this.views = views;
    this.self = self;
    this.storeProvider = storeProvider;
    this.checkerFactory = checkerFactory;
    this.externalIds = externalIds;
  }

  @Override
  public ListGpgKeys list() throws ResourceNotFoundException, AuthException {
    return new ListGpgKeys();
  }

  @Override
  public GpgKey parse(AccountResource parent, IdString id)
      throws ResourceNotFoundException, PGPException, StorageException, IOException {
    checkVisible(self, parent);

    ExternalId gpgKeyExtId = findGpgKey(id.get(), getGpgExtIds(parent));
    byte[] fp = parseFingerprint(gpgKeyExtId);
    try (PublicKeyStore store = storeProvider.get()) {
      long keyId = keyId(fp);
      for (PGPPublicKeyRing keyRing : store.get(keyId)) {
        PGPPublicKey key = keyRing.getPublicKey();
        if (Arrays.equals(key.getFingerprint(), fp)) {
          return new GpgKey(parent.getUser(), keyRing);
        }
      }
    }

    throw new ResourceNotFoundException(id);
  }

  static ExternalId findGpgKey(String str, Iterable<ExternalId> existingExtIds)
      throws ResourceNotFoundException {
    str = CharMatcher.whitespace().removeFrom(str).toUpperCase();
    if ((str.length() != 8 && str.length() != 40)
        || !CharMatcher.anyOf("0123456789ABCDEF").matchesAllOf(str)) {
      throw new ResourceNotFoundException(str);
    }
    ExternalId gpgKeyExtId = null;
    for (ExternalId extId : existingExtIds) {
      String fpStr = extId.key().id();
      if (!fpStr.endsWith(str)) {
        continue;
      } else if (gpgKeyExtId != null) {
        throw new ResourceNotFoundException("Multiple keys found for " + str);
      }
      gpgKeyExtId = extId;
      if (str.length() == 40) {
        break;
      }
    }
    if (gpgKeyExtId == null) {
      throw new ResourceNotFoundException(str);
    }
    return gpgKeyExtId;
  }

  static byte[] parseFingerprint(ExternalId gpgKeyExtId) {
    return BaseEncoding.base16().decode(gpgKeyExtId.key().id());
  }

  @Override
  public DynamicMap<RestView<GpgKey>> views() {
    return views;
  }

  public class ListGpgKeys implements RestReadView<AccountResource> {
    @Override
    public Map<String, GpgKeyInfo> apply(AccountResource rsrc)
        throws StorageException, PGPException, IOException, ResourceNotFoundException {
      checkVisible(self, rsrc);
      Map<String, GpgKeyInfo> keys = new HashMap<>();
      try (PublicKeyStore store = storeProvider.get()) {
        for (ExternalId extId : getGpgExtIds(rsrc)) {
          byte[] fp = parseFingerprint(extId);
          boolean found = false;
          for (PGPPublicKeyRing keyRing : store.get(keyId(fp))) {
            if (Arrays.equals(keyRing.getPublicKey().getFingerprint(), fp)) {
              found = true;
              GpgKeyInfo info =
                  toJson(
                      keyRing.getPublicKey(), checkerFactory.create(rsrc.getUser(), store), store);
              keys.put(info.id, info);
              info.id = null;
              break;
            }
          }
          if (!found) {
            logger.atWarning().log(
                "No public key stored for fingerprint %s", Fingerprint.toString(fp));
          }
        }
      }
      return keys;
    }
  }

  @Singleton
  public static class Get implements RestReadView<GpgKey> {
    private final Provider<PublicKeyStore> storeProvider;
    private final GerritPublicKeyChecker.Factory checkerFactory;

    @Inject
    Get(Provider<PublicKeyStore> storeProvider, GerritPublicKeyChecker.Factory checkerFactory) {
      this.storeProvider = storeProvider;
      this.checkerFactory = checkerFactory;
    }

    @Override
    public GpgKeyInfo apply(GpgKey rsrc) throws IOException {
      try (PublicKeyStore store = storeProvider.get()) {
        return toJson(
            rsrc.getKeyRing().getPublicKey(),
            checkerFactory.create().setExpectedUser(rsrc.getUser()),
            store);
      }
    }
  }

  private Iterable<ExternalId> getGpgExtIds(AccountResource rsrc) throws IOException {
    return externalIds.byAccount(rsrc.getUser().getAccountId(), SCHEME_GPGKEY);
  }

  private static long keyId(byte[] fp) {
    return NB.decodeInt64(fp, fp.length - 8);
  }

  static void checkVisible(Provider<CurrentUser> self, AccountResource rsrc)
      throws ResourceNotFoundException {
    if (!BouncyCastleUtil.havePGP()) {
      throw new ResourceNotFoundException("GPG not enabled");
    }
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      throw new ResourceNotFoundException();
    }
  }

  public static GpgKeyInfo toJson(PGPPublicKey key, CheckResult checkResult) throws IOException {
    GpgKeyInfo info = new GpgKeyInfo();

    if (key != null) {
      info.id = PublicKeyStore.keyIdToString(key.getKeyID());
      info.fingerprint = Fingerprint.toString(key.getFingerprint());
      Iterator<String> userIds = key.getUserIDs();
      info.userIds = ImmutableList.copyOf(userIds);

      try (ByteArrayOutputStream out = new ByteArrayOutputStream(4096)) {
        try (ArmoredOutputStream aout = new ArmoredOutputStream(out)) {
          // This is not exactly the key stored in the store, but is equivalent. In
          // particular, it will have a Bouncy Castle version string. The armored
          // stream reader in PublicKeyStore doesn't give us an easy way to extract
          // the original ASCII armor.
          key.encode(aout);
        }
        info.key = new String(out.toByteArray(), UTF_8);
      }
    }

    info.status = checkResult.getStatus();
    info.problems = checkResult.getProblems();

    return info;
  }

  static GpgKeyInfo toJson(PGPPublicKey key, PublicKeyChecker checker, PublicKeyStore store)
      throws IOException {
    return toJson(key, checker.setStore(store).check(key));
  }

  public static void toJson(GpgKeyInfo info, CheckResult checkResult) {
    info.status = checkResult.getStatus();
    info.problems = checkResult.getProblems();
  }
}
