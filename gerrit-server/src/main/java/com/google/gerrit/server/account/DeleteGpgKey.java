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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyIdToString;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountResource.GpgKey;
import com.google.gerrit.server.account.DeleteGpgKey.Input;
import com.google.gerrit.server.git.gpg.PublicKeyStore;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;

import java.io.IOException;
import java.util.Collections;

public class DeleteGpgKey implements RestModifyView<GpgKey, Input> {
  public static class Input {
  }

  private final Provider<PersonIdent> serverIdent;
  private final Provider<ReviewDb> db;
  private final Provider<PublicKeyStore> storeProvider;

  @Inject
  DeleteGpgKey(@GerritPersonIdent Provider<PersonIdent> serverIdent,
      Provider<ReviewDb> db,
      Provider<PublicKeyStore> storeProvider) {
    this.serverIdent = serverIdent;
    this.db = db;
    this.storeProvider = storeProvider;
  }

  @Override
  public Response<?> apply(GpgKey rsrc, Input input)
      throws ResourceConflictException, PGPException, OrmException,
      IOException {
    PGPPublicKey key = rsrc.getKeyRing().getPublicKey();
    AccountExternalId.Key extIdKey = new AccountExternalId.Key(
        AccountExternalId.SCHEME_GPGKEY,
        BaseEncoding.base16().encode(key.getFingerprint()));
    db.get().accountExternalIds().deleteKeys(Collections.singleton(extIdKey));

    try (PublicKeyStore store = storeProvider.get()) {
      store.remove(rsrc.getKeyRing().getPublicKey().getFingerprint());

      CommitBuilder cb = new CommitBuilder();
      PersonIdent committer = serverIdent.get();
      cb.setAuthor(rsrc.getUser().newCommitterIdent(
          committer.getWhen(), committer.getTimeZone()));
      cb.setCommitter(committer);
      cb.setMessage("Delete public key " + keyIdToString(key.getKeyID()));

      RefUpdate.Result saveResult = store.save(cb);
      switch (saveResult) {
        case NO_CHANGE:
        case FAST_FORWARD:
          break;
        default:
          throw new ResourceConflictException(
              "Failed to delete public key: " + saveResult);
      }
    }
    return Response.none();
  }
}
