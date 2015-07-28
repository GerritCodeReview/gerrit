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
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyToString;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AddGpgKey.Input;
import com.google.gerrit.server.git.gpg.CheckResult;
import com.google.gerrit.server.git.gpg.PublicKeyChecker;
import com.google.gerrit.server.git.gpg.PublicKeyStore;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Singleton
public class AddGpgKey implements RestModifyView<AccountResource, Input> {
  public static class Input {
    public RawInput raw;
  }

  private final Provider<PersonIdent> serverIdent;
  private final Provider<ReviewDb> db;
  private final Provider<PublicKeyStore> storeProvider;
  private final PublicKeyChecker checker;

  @Inject
  AddGpgKey(@GerritPersonIdent Provider<PersonIdent> serverIdent,
      Provider<ReviewDb> db,
      Provider<PublicKeyStore> storeProvider,
      PublicKeyChecker checker) {
    this.serverIdent = serverIdent;
    this.db = db;
    this.storeProvider = storeProvider;
    this.checker = checker;
  }

  @Override
  public GpgKeyInfo apply(AccountResource rsrc, Input input)
      throws ResourceNotFoundException, BadRequestException,
      ResourceConflictException, PGPException, OrmException, IOException {
    GpgKeys.checkEnabled();
    PGPPublicKeyRing keyRing = readKey(input);
    PGPPublicKey key = keyRing.getPublicKey();

    AccountExternalId.Key extIdKey = new AccountExternalId.Key(
        AccountExternalId.SCHEME_GPGKEY,
        BaseEncoding.base16().encode(key.getFingerprint()));
    AccountExternalId existing = db.get().accountExternalIds().get(extIdKey);
    if (existing != null
        && !existing.getAccountId().equals(rsrc.getUser().getAccountId())) {
      throw new ResourceConflictException(
          "GPG key already associated with another account");
    }

    storeKey(rsrc, keyRing);
    if (existing == null) {
      db.get().accountExternalIds().insert(Collections.singleton(
          new AccountExternalId(rsrc.getUser().getAccountId(), extIdKey)));
    }
    return GpgKeys.toJson(key);
  }

  private PGPPublicKeyRing readKey(Input input)
      throws BadRequestException, IOException {
    try (InputStream in = input.raw.getInputStream();
        ArmoredInputStream ain = new ArmoredInputStream(in)) {
      @SuppressWarnings("unchecked")
      List<Object> objs = Lists.newArrayList(new BcPGPObjectFactory(ain));
      if (objs.size() != 1 || !(objs.get(0) instanceof PGPPublicKeyRing)) {
        throw new BadRequestException("Expected exactly one PUBLIC KEY BLOCK");
      }
      return (PGPPublicKeyRing) objs.get(0);
    }
  }

  private void storeKey(AccountResource rsrc, PGPPublicKeyRing keyRing)
      throws BadRequestException, ResourceConflictException, PGPException,
      IOException {
    PGPPublicKey key = keyRing.getPublicKey();
    CheckResult result = checker.check(key);
    if (!result.isOk()) {
      throw new BadRequestException(String.format(
          "Problems with public key %s:\n%s",
          keyToString(key), Joiner.on('\n').join(result.getProblems())));
    }
    try (PublicKeyStore store = storeProvider.get()) {
      store.add(keyRing);
      CommitBuilder cb = new CommitBuilder();
      PersonIdent committer = serverIdent.get();
      cb.setAuthor(rsrc.getUser().newCommitterIdent(
          committer.getWhen(), committer.getTimeZone()));
      cb.setCommitter(committer);
      cb.setMessage("Upload public key "
          + keyIdToString(keyRing.getPublicKey().getKeyID()));

      RefUpdate.Result saveResult = store.save(cb);
      switch (saveResult) {
        case NEW:
        case FAST_FORWARD:
        case FORCED:
          break;
        default:
          throw new ResourceConflictException(
              "Failed to save public key: " + saveResult);
      }
    }
  }
}
