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

import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.extensions.api.accounts.GpgKeysInput;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.gpg.CheckResult;
import com.google.gerrit.gpg.Fingerprint;
import com.google.gerrit.gpg.GerritPublicKeyChecker;
import com.google.gerrit.gpg.PublicKeyChecker;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.mail.send.AddKeySender;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPRuntimeOperationException;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PostGpgKeys implements RestModifyView<AccountResource, GpgKeysInput> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Provider<PersonIdent> serverIdent;
  private final Provider<CurrentUser> self;
  private final Provider<PublicKeyStore> storeProvider;
  private final GerritPublicKeyChecker.Factory checkerFactory;
  private final AddKeySender.Factory addKeyFactory;
  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final ExternalIds externalIds;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  PostGpgKeys(
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      Provider<CurrentUser> self,
      Provider<PublicKeyStore> storeProvider,
      GerritPublicKeyChecker.Factory checkerFactory,
      AddKeySender.Factory addKeyFactory,
      Provider<InternalAccountQuery> accountQueryProvider,
      ExternalIds externalIds,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.serverIdent = serverIdent;
    this.self = self;
    this.storeProvider = storeProvider;
    this.checkerFactory = checkerFactory;
    this.addKeyFactory = addKeyFactory;
    this.accountQueryProvider = accountQueryProvider;
    this.externalIds = externalIds;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Map<String, GpgKeyInfo> apply(AccountResource rsrc, GpgKeysInput input)
      throws ResourceNotFoundException, BadRequestException, ResourceConflictException,
          PGPException, OrmException, IOException, ConfigInvalidException {
    GpgKeys.checkVisible(self, rsrc);

    Collection<ExternalId> existingExtIds =
        externalIds.byAccount(rsrc.getUser().getAccountId(), SCHEME_GPGKEY);
    try (PublicKeyStore store = storeProvider.get()) {
      Map<ExternalId, Fingerprint> toRemove = readKeysToRemove(input, existingExtIds);
      Collection<Fingerprint> fingerprintsToRemove = toRemove.values();
      List<PGPPublicKeyRing> newKeys = readKeysToAdd(input, fingerprintsToRemove);
      List<ExternalId> newExtIds = new ArrayList<>(existingExtIds.size());

      for (PGPPublicKeyRing keyRing : newKeys) {
        PGPPublicKey key = keyRing.getPublicKey();
        ExternalId.Key extIdKey = toExtIdKey(key.getFingerprint());
        Account account = getAccountByExternalId(extIdKey);
        if (account != null) {
          if (!account.getId().equals(rsrc.getUser().getAccountId())) {
            throw new ResourceConflictException("GPG key already associated with another account");
          }
        } else {
          newExtIds.add(ExternalId.create(extIdKey, rsrc.getUser().getAccountId()));
        }
      }

      storeKeys(rsrc, newKeys, fingerprintsToRemove);

      accountsUpdateProvider
          .get()
          .update(
              "Update GPG Keys via API",
              rsrc.getUser().getAccountId(),
              u -> u.replaceExternalIds(toRemove.keySet(), newExtIds));
      return toJson(newKeys, fingerprintsToRemove, store, rsrc.getUser());
    }
  }

  private Map<ExternalId, Fingerprint> readKeysToRemove(
      GpgKeysInput input, Collection<ExternalId> existingExtIds) {
    if (input.delete == null || input.delete.isEmpty()) {
      return ImmutableMap.of();
    }
    Map<ExternalId, Fingerprint> fingerprints =
        Maps.newHashMapWithExpectedSize(input.delete.size());
    for (String id : input.delete) {
      try {
        ExternalId gpgKeyExtId = GpgKeys.findGpgKey(id, existingExtIds);
        fingerprints.put(gpgKeyExtId, new Fingerprint(GpgKeys.parseFingerprint(gpgKeyExtId)));
      } catch (ResourceNotFoundException e) {
        // Skip removal.
      }
    }
    return fingerprints;
  }

  private List<PGPPublicKeyRing> readKeysToAdd(GpgKeysInput input, Collection<Fingerprint> toRemove)
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
        PGPPublicKeyRing keyRing = (PGPPublicKeyRing) objs.get(0);
        if (toRemove.contains(new Fingerprint(keyRing.getPublicKey().getFingerprint()))) {
          throw new BadRequestException(
              "Cannot both add and delete key: " + keyToString(keyRing.getPublicKey()));
        }
        keyRings.add(keyRing);
      } catch (PGPRuntimeOperationException e) {
        throw new BadRequestException("Failed to parse GPG keys", e);
      }
    }
    return keyRings;
  }

  private void storeKeys(
      AccountResource rsrc, List<PGPPublicKeyRing> keyRings, Collection<Fingerprint> toRemove)
      throws BadRequestException, ResourceConflictException, PGPException, IOException {
    try (PublicKeyStore store = storeProvider.get()) {
      List<String> addedKeys = new ArrayList<>();
      for (PGPPublicKeyRing keyRing : keyRings) {
        PGPPublicKey key = keyRing.getPublicKey();
        // Don't check web of trust; admins can fill in certifications later.
        CheckResult result = checkerFactory.create(rsrc.getUser(), store).disableTrust().check(key);
        if (!result.isOk()) {
          throw new BadRequestException(
              String.format(
                  "Problems with public key %s:\n%s",
                  keyToString(key), Joiner.on('\n').join(result.getProblems())));
        }
        addedKeys.add(PublicKeyStore.keyToString(key));
        store.add(keyRing);
      }
      for (Fingerprint fp : toRemove) {
        store.remove(fp.get());
      }
      CommitBuilder cb = new CommitBuilder();
      PersonIdent committer = serverIdent.get();
      cb.setAuthor(rsrc.getUser().newCommitterIdent(committer.getWhen(), committer.getTimeZone()));
      cb.setCommitter(committer);

      RefUpdate.Result saveResult = store.save(cb);
      switch (saveResult) {
        case NEW:
        case FAST_FORWARD:
        case FORCED:
          try {
            addKeyFactory.create(rsrc.getUser(), addedKeys).send();
          } catch (EmailException e) {
            log.error(
                "Cannot send GPG key added message to "
                    + rsrc.getUser().getAccount().getPreferredEmail(),
                e);
          }
          break;
        case NO_CHANGE:
          break;
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case RENAMED:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        default:
          // TODO(dborowitz): Backoff and retry on LOCK_FAILURE.
          throw new ResourceConflictException("Failed to save public keys: " + saveResult);
      }
    }
  }

  private ExternalId.Key toExtIdKey(byte[] fp) {
    return ExternalId.Key.create(SCHEME_GPGKEY, BaseEncoding.base16().encode(fp));
  }

  private Account getAccountByExternalId(ExternalId.Key extIdKey) throws OrmException {
    List<AccountState> accountStates = accountQueryProvider.get().byExternalId(extIdKey);

    if (accountStates.isEmpty()) {
      return null;
    }

    if (accountStates.size() > 1) {
      StringBuilder msg = new StringBuilder();
      msg.append("GPG key ").append(extIdKey.get()).append(" associated with multiple accounts: ");
      Joiner.on(", ")
          .appendTo(msg, Lists.transform(accountStates, AccountState.ACCOUNT_ID_FUNCTION));
      log.error(msg.toString());
      throw new IllegalStateException(msg.toString());
    }

    return accountStates.get(0).getAccount();
  }

  private Map<String, GpgKeyInfo> toJson(
      Collection<PGPPublicKeyRing> keys,
      Collection<Fingerprint> deleted,
      PublicKeyStore store,
      IdentifiedUser user)
      throws IOException {
    // Unlike when storing keys, include web-of-trust checks when producing
    // result JSON, so the user at least knows of any issues.
    PublicKeyChecker checker = checkerFactory.create(user, store);
    Map<String, GpgKeyInfo> infos = Maps.newHashMapWithExpectedSize(keys.size() + deleted.size());
    for (PGPPublicKeyRing keyRing : keys) {
      PGPPublicKey key = keyRing.getPublicKey();
      CheckResult result = checker.check(key);
      GpgKeyInfo info = GpgKeys.toJson(key, result);
      infos.put(info.id, info);
      info.id = null;
    }
    for (Fingerprint fp : deleted) {
      infos.put(keyIdToString(fp.getId()), new GpgKeyInfo());
    }
    return infos;
  }
}
