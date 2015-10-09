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

package com.google.gerrit.gpg;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_GPGKEY;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Checker for GPG public keys including Gerrit-specific checks.
 * <p>
 * For Gerrit, keys must contain a self-signed user ID certification matching a
 * trusted external ID in the database, or an email address thereof.
 */
public class GerritPublicKeyChecker extends PublicKeyChecker {
  private static final Logger log =
      LoggerFactory.getLogger(GerritPublicKeyChecker.class);

  private final Provider<ReviewDb> db;
  private final String webUrl;
  private final IdentifiedUser.GenericFactory userFactory;
  private final IdentifiedUser expectedUser;

  @Singleton
  public static class Factory {
    private final Provider<ReviewDb> db;
    private final String webUrl;
    private final IdentifiedUser.GenericFactory userFactory;
    private final int maxTrustDepth;
    private final ImmutableList<Fingerprint> trusted;

    @Inject
    Factory(@GerritServerConfig Config cfg,
        Provider<ReviewDb> db,
        IdentifiedUser.GenericFactory userFactory,
        @CanonicalWebUrl String webUrl) {
      this.db = db;
      this.webUrl = webUrl;
      this.userFactory = userFactory;
      this.maxTrustDepth = cfg.getInt("receive", null, "maxTrustDepth", 0);

      String[] strs = cfg.getStringList("receive", null, "trustedKey");
      if (strs.length != 0) {
        List<Fingerprint> fps = new ArrayList<>(strs.length);
        for (String str : strs) {
          str = CharMatcher.WHITESPACE.removeFrom(str).toUpperCase();
          fps.add(new Fingerprint(BaseEncoding.base16().decode(str)));
        }
        trusted = ImmutableList.copyOf(fps);
      } else {
        trusted = null;
      }
    }

    /**
     * Create a checker that can check arbitrary public keys.
     * <p>
     * Each key is checked against the set of identities in the database
     * belonging to the same user as the key.
     *
     * @return a new checker.
     */
    public GerritPublicKeyChecker create() {
      return new GerritPublicKeyChecker(this, null);
    }

    /**
     * Create a checker for checking a single public key against a known user.
     * <p>
     * The top-level key passed to {@link #check(PGPPublicKey, PublicKeyStore)}
     * must belong to the given user. (Other keys checked in the course of
     * verifying the web of trust are checked against the set of identities in
     * the database belonging to the same user as the key.)
     *
     * @param expectedUser the user
     * @return a new checker.
     */
    public GerritPublicKeyChecker create(IdentifiedUser expectedUser) {
      checkNotNull(expectedUser);
      return new GerritPublicKeyChecker(this, expectedUser);
    }
  }

  private GerritPublicKeyChecker(Factory factory, IdentifiedUser expectedUser) {
    super(factory.maxTrustDepth, factory.trusted);
    this.db = factory.db;
    this.webUrl = factory.webUrl;
    this.userFactory = factory.userFactory;
    this.expectedUser = expectedUser;
  }

  @Override
  public CheckResult checkCustom(PGPPublicKey key, int depth) {
    try {
      if (depth == 0 && expectedUser != null) {
        return checkIdsForExpectedUser(key);
      } else {
        return checkIdsForArbitraryUser(key);
      }
    } catch (PGPException | OrmException e) {
      String msg = "Error checking user IDs for key";
      log.warn(msg + " " + keyIdToString(key.getKeyID()), e);
      return CheckResult.bad(msg);
    }
  }

  private CheckResult checkIdsForExpectedUser(PGPPublicKey key)
      throws PGPException {
    Set<String> allowedUserIds = getAllowedUserIds(expectedUser);
    if (allowedUserIds.isEmpty()) {
      return CheckResult.bad("No identities found for user; check "
          + webUrl + "#" + PageLinks.SETTINGS_WEBIDENT);
    }
    if (hasAllowedUserId(key, allowedUserIds)) {
      return CheckResult.trusted();
    }
    return CheckResult.bad(missingUserIds(allowedUserIds));
  }

  private CheckResult checkIdsForArbitraryUser(PGPPublicKey key)
      throws PGPException, OrmException {
    AccountExternalId extId = db.get().accountExternalIds().get(
        toExtIdKey(key));
    if (extId == null) {
      return CheckResult.bad("Key is not associated with any users");
    }
    IdentifiedUser user = userFactory.create(db, extId.getAccountId());
    Set<String> allowedUserIds = getAllowedUserIds(user);
    if (allowedUserIds.isEmpty()) {
      return CheckResult.bad("No identities found for user");
    }
    if (hasAllowedUserId(key, allowedUserIds)) {
      return CheckResult.trusted();
    }
    return CheckResult.bad(
        "Key does not contain any valid certifications for user's identities");
  }

  private boolean hasAllowedUserId(PGPPublicKey key, Set<String> allowedUserIds)
      throws PGPException {
    @SuppressWarnings("unchecked")
    Iterator<String> userIds = key.getUserIDs();
    while (userIds.hasNext()) {
      String userId = userIds.next();
      if (isAllowed(userId, allowedUserIds)) {
        Iterator<PGPSignature> sigs = getSignaturesForId(key, userId);
        while (sigs.hasNext()) {
          if (isValidCertification(key, sigs.next(), userId)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @SuppressWarnings("unchecked")
  private Iterator<PGPSignature> getSignaturesForId(PGPPublicKey key,
      String userId) {
    return MoreObjects.firstNonNull(
        key.getSignaturesForID(userId),
        Collections.emptyIterator());
  }

  private Set<String> getAllowedUserIds(IdentifiedUser user) {
    Set<String> result = new HashSet<>();
    result.addAll(user.getEmailAddresses());
    for (AccountExternalId extId : user.state().getExternalIds()) {
      if (extId.isScheme(SCHEME_GPGKEY)) {
        continue; // Omit GPG keys.
      }
      result.add(extId.getExternalId());
    }
    return result;
  }

  private static boolean isAllowed(String userId, Set<String> allowedUserIds) {
    return allowedUserIds.contains(userId)
        || allowedUserIds.contains(
            PushCertificateIdent.parse(userId).getEmailAddress());
  }

  private static boolean isValidCertification(PGPPublicKey key,
      PGPSignature sig, String userId) throws PGPException {
    if (sig.getSignatureType() != PGPSignature.DEFAULT_CERTIFICATION
        && sig.getSignatureType() != PGPSignature.POSITIVE_CERTIFICATION) {
      return false;
    }
    if (sig.getKeyID() != key.getKeyID()) {
      return false;
    }
    // TODO(dborowitz): Handle certification revocations:
    // - Is there a revocation by either this key or another key trusted by the
    //   server?
    // - Does such a revocation postdate all other valid certifications?

    sig.init(new BcPGPContentVerifierBuilderProvider(), key);
    return sig.verifyCertification(userId, key);
  }

  private static String missingUserIds(Set<String> allowedUserIds) {
    StringBuilder sb = new StringBuilder("Key must contain a valid"
        + " certification for one of the following identities:\n");
    Iterator<String> sorted = FluentIterable.from(allowedUserIds)
        .toSortedList(Ordering.natural())
        .iterator();
    while (sorted.hasNext()) {
      sb.append("  ").append(sorted.next());
      if (sorted.hasNext()) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  static AccountExternalId.Key toExtIdKey(PGPPublicKey key) {
    return new AccountExternalId.Key(
        SCHEME_GPGKEY,
        BaseEncoding.base16().encode(key.getFingerprint()));
  }
}
