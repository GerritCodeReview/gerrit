// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.gpg;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.entities.Account;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.util.NB;

public class PublicKeyStoreUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ExternalIds externalIds;
  private final Provider<PublicKeyStore> storeProvider;

  @Inject
  public PublicKeyStoreUtil(ExternalIds externalIds, Provider<PublicKeyStore> storeProvider) {
    this.externalIds = externalIds;
    this.storeProvider = storeProvider;
  }

  public static byte[] parseFingerprint(ExternalId gpgKeyExtId) {
    return BaseEncoding.base16().decode(gpgKeyExtId.key().id());
  }

  public static long keyIdFromFingerprint(byte[] fp) {
    return NB.decodeInt64(fp, fp.length - 8);
  }

  public List<PGPPublicKey> listGpgKeysForUser(Account.Id id) throws PGPException, IOException {
    List<PGPPublicKey> keys = new ArrayList<>();
    try (PublicKeyStore store = storeProvider.get()) {
      for (ExternalId extId : getGpgExtIds(id)) {
        byte[] fp = parseFingerprint(extId);
        boolean found = false;
        for (PGPPublicKeyRing keyRing : store.get(keyIdFromFingerprint(fp))) {
          if (Arrays.equals(keyRing.getPublicKey().getFingerprint(), fp)) {
            found = true;
            keys.add(keyRing.getPublicKey());
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

  public Iterable<ExternalId> getGpgExtIds(Account.Id id) throws IOException {
    return externalIds.byAccount(id, SCHEME_GPGKEY);
  }
}
