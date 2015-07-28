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

import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyToString;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.PostGpgKeys.Input;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Singleton
public class PostGpgKeys implements RestModifyView<AccountResource, Input> {
  public static class Input {
    public List<String> add;
  }

  private final Provider<PersonIdent> serverIdent;
  private final Provider<ReviewDb> db;
  private final Provider<PublicKeyStore> storeProvider;
  private final PublicKeyChecker checker;

  @Inject
  PostGpgKeys(@GerritPersonIdent Provider<PersonIdent> serverIdent,
      Provider<ReviewDb> db,
      Provider<PublicKeyStore> storeProvider,
      PublicKeyChecker checker) {
    this.serverIdent = serverIdent;
    this.db = db;
    this.storeProvider = storeProvider;
    this.checker = checker;
  }

  @Override
  public Map<String, GpgKeyInfo> apply(AccountResource rsrc, Input input)
      throws ResourceNotFoundException, BadRequestException,
      ResourceConflictException, PGPException, OrmException, IOException {
    GpgKeys.checkEnabled();

    List<PGPPublicKeyRing> newKeys = readKeys(input);
    List<AccountExternalId> newExtIds = new ArrayList<>(newKeys.size());

    for (PGPPublicKeyRing keyRing : newKeys) {
      PGPPublicKey key = keyRing.getPublicKey();
      AccountExternalId.Key extIdKey = new AccountExternalId.Key(
          AccountExternalId.SCHEME_GPGKEY,
          BaseEncoding.base16().encode(key.getFingerprint()));
      AccountExternalId existing = db.get().accountExternalIds().get(extIdKey);
      if (existing != null) {
        if (!existing.getAccountId().equals(rsrc.getUser().getAccountId())) {
          throw new ResourceConflictException(
              "GPG key already associated with another account");
        }
      } else {
        newExtIds.add(
            new AccountExternalId(rsrc.getUser().getAccountId(), extIdKey));
      }
    }

    storeKeys(rsrc, newKeys);
    if (!newExtIds.isEmpty()) {
      db.get().accountExternalIds().insert(newExtIds);
    }
    return toJson(newKeys);
  }

  private List<PGPPublicKeyRing> readKeys(Input input)
      throws BadRequestException, IOException {
    if (input.add == null || input.add.isEmpty()) {
      return ImmutableList.of();
    }
    List<PGPPublicKeyRing> keyRings = new ArrayList<>(input.add.size());
    for (String armored : input.add) {
      try (InputStream in = new ByteArrayInputStream(armored.getBytes(UTF_8));
          ArmoredInputStream ain = new ArmoredInputStream(in)) {
        @SuppressWarnings("unchecked")
        List<Object> objs = Lists.newArrayList(new BcPGPObjectFactory(ain));
        if (objs.size() != 1 || !(objs.get(0) instanceof PGPPublicKeyRing)) {
          throw new BadRequestException("Expected exactly one PUBLIC KEY BLOCK");
        }
        keyRings.add((PGPPublicKeyRing) objs.get(0));
      }
    }
    return keyRings;
  }

  private void storeKeys(AccountResource rsrc, List<PGPPublicKeyRing> keyRings)
      throws BadRequestException, ResourceConflictException, PGPException,
      IOException {
    try (PublicKeyStore store = storeProvider.get()) {
      for (PGPPublicKeyRing keyRing : keyRings) {
        PGPPublicKey key = keyRing.getPublicKey();
        CheckResult result = checker.check(key);
        if (!result.isOk()) {
          throw new BadRequestException(String.format(
              "Problems with public key %s:\n%s",
              keyToString(key), Joiner.on('\n').join(result.getProblems())));
        }
        store.add(keyRing);
      }
      CommitBuilder cb = new CommitBuilder();
      PersonIdent committer = serverIdent.get();
      cb.setAuthor(rsrc.getUser().newCommitterIdent(
          committer.getWhen(), committer.getTimeZone()));
      cb.setCommitter(committer);

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

  private static Map<String, GpgKeyInfo> toJson(
      Collection<PGPPublicKeyRing> keyRings) throws IOException {
    Map<String, GpgKeyInfo> infos =
        Maps.newHashMapWithExpectedSize(keyRings.size());
    for (PGPPublicKeyRing keyRing : keyRings) {
      GpgKeyInfo info = GpgKeys.toJson(keyRing);
      infos.put(info.id, info);
      info.id = null;
    }
    return infos;
  }
}
