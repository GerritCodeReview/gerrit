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

import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.entities.Account;
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
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.util.NB;

public class PublicKeyStoreUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ExternalIds externalIds;
  private final Provider<PublicKeyStore> storeProvider;

  @Inject
  PublicKeyStoreUtil(ExternalIds externalIds, Provider<PublicKeyStore> storeProvider) {
    this.externalIds = externalIds;
    this.storeProvider = storeProvider;
  }

  public static byte[] parseFingerprint(ExternalId gpgKeyExtId) {
    return BaseEncoding.base16().decode(gpgKeyExtId.key().id());
  }

  public static long keyIdFromFingerprint(byte[] fp) {
    return NB.decodeInt64(fp, fp.length - 8);
  }

  public boolean hasInitializedPublicKeyStore() {
    try {
      return storeProvider.get() != null;
    } catch (Exception e) {
      return false;
    }
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

  public RefUpdate.Result deletePgpKey(PGPPublicKey key, PersonIdent committer, PersonIdent author)
      throws PGPException, IOException {
    return deletePgpKeys(ImmutableList.of(key), committer, author).get(0);
  }

  public List<RefUpdate.Result> deletePgpKeys(
      List<PGPPublicKey> keys, PersonIdent committer, PersonIdent author)
      throws IOException, PGPException {
    List<RefUpdate.Result> res = new ArrayList<>();
    try (PublicKeyStore store = storeProvider.get()) {
      for (PGPPublicKey key : keys) {
        store.remove(key.getFingerprint());

        CommitBuilder cb = new CommitBuilder();
        cb.setAuthor(author);
        cb.setCommitter(committer);
        cb.setMessage("Delete public key " + keyIdToString(key.getKeyID()));

        RefUpdate.Result saveResult = store.save(cb);
        res.add(saveResult);
      }
    }
    return res;
  }

  public List<RefUpdate.Result> deleteAllPgpKeysForUser(
      Account.Id id, PersonIdent committer, PersonIdent author) throws PGPException, IOException {
    return deletePgpKeys(listGpgKeysForUser(id), committer, author);
  }
}
