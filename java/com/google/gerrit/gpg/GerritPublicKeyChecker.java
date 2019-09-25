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

import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GPGKEY;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.UrlFormatter;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.PushCertificateIdent;

/**
 * Checker for GPG public keys including Gerrit-specific checks.
 *
 * <p>For Gerrit, keys must contain a self-signed user ID certification matching a trusted external
 * ID in the database, or an email address thereof.
 */
public class GerritPublicKeyChecker extends PublicKeyChecker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class Factory {
    private final Provider<InternalAccountQuery> accountQueryProvider;
    private final DynamicItem<UrlFormatter> urlFormatter;
    private final IdentifiedUser.GenericFactory userFactory;
    private final int maxTrustDepth;
    private final ImmutableMap<Long, Fingerprint> trusted;

    @Inject
    Factory(
        @GerritServerConfig Config cfg,
        Provider<InternalAccountQuery> accountQueryProvider,
        IdentifiedUser.GenericFactory userFactory,
        DynamicItem<UrlFormatter> urlFormatter) {
      this.accountQueryProvider = accountQueryProvider;
      this.urlFormatter = urlFormatter;
      this.userFactory = userFactory;
      this.maxTrustDepth = cfg.getInt("receive", null, "maxTrustDepth", 0);

      String[] strs = cfg.getStringList("receive", null, "trustedKey");
      if (strs.length != 0) {
        Map<Long, Fingerprint> fps = Maps.newHashMapWithExpectedSize(strs.length);
        for (String str : strs) {
          str = CharMatcher.whitespace().removeFrom(str).toUpperCase();
          Fingerprint fp = new Fingerprint(BaseEncoding.base16().decode(str));
          fps.put(fp.getId(), fp);
        }
        trusted = ImmutableMap.copyOf(fps);
      } else {
        trusted = null;
      }
    }

    public GerritPublicKeyChecker create() {
      return new GerritPublicKeyChecker(this);
    }

    public GerritPublicKeyChecker create(IdentifiedUser expectedUser, PublicKeyStore store) {
      GerritPublicKeyChecker checker = new GerritPublicKeyChecker(this);
      checker.setExpectedUser(expectedUser);
      checker.setStore(store);
      return checker;
    }
  }

  private final Provider<InternalAccountQuery> accountQueryProvider;
  private final DynamicItem<UrlFormatter> urlFormatter;
  private final IdentifiedUser.GenericFactory userFactory;

  private IdentifiedUser expectedUser;

  private GerritPublicKeyChecker(Factory factory) {
    this.accountQueryProvider = factory.accountQueryProvider;
    this.urlFormatter = factory.urlFormatter;
    this.userFactory = factory.userFactory;
    if (factory.trusted != null) {
      enableTrust(factory.maxTrustDepth, factory.trusted);
    }
  }

  /**
   * Set the expected user for this checker.
   *
   * <p>If set, the top-level key passed to {@link #check(PGPPublicKey)} must belong to the given
   * user. (Other keys checked in the course of verifying the web of trust are checked against the
   * set of identities in the database belonging to the same user as the key.)
   */
  public GerritPublicKeyChecker setExpectedUser(IdentifiedUser expectedUser) {
    this.expectedUser = expectedUser;
    return this;
  }

  @Override
  public CheckResult checkCustom(PGPPublicKey key, int depth) {
    try {
      if (depth == 0 && expectedUser != null) {
        return checkIdsForExpectedUser(key);
      }
      return checkIdsForArbitraryUser(key);
    } catch (PGPException | RuntimeException e) {
      String msg = "Error checking user IDs for key";
      logger.atWarning().withCause(e).log("%s %s", msg, keyIdToString(key.getKeyID()));
      return CheckResult.bad(msg);
    }
  }

  private CheckResult checkIdsForExpectedUser(PGPPublicKey key) throws PGPException {
    Set<String> allowedUserIds = getAllowedUserIds(expectedUser);
    if (allowedUserIds.isEmpty()) {
      Optional<String> settings = urlFormatter.get().getSettingsUrl("Identities");
      return CheckResult.bad(
          "No identities found for user"
              + (settings.isPresent() ? "; check " + settings.get() : ""));
    }
    if (hasAllowedUserId(key, allowedUserIds)) {
      return CheckResult.trusted();
    }
    return CheckResult.bad(missingUserIds(allowedUserIds));
  }

  private CheckResult checkIdsForArbitraryUser(PGPPublicKey key) throws PGPException {
    List<AccountState> accountStates = accountQueryProvider.get().byExternalId(toExtIdKey(key));
    if (accountStates.isEmpty()) {
      return CheckResult.bad("Key is not associated with any users");
    }
    if (accountStates.size() > 1) {
      return CheckResult.bad("Key is associated with multiple users");
    }
    IdentifiedUser user = userFactory.create(accountStates.get(0));

    Set<String> allowedUserIds = getAllowedUserIds(user);
    if (allowedUserIds.isEmpty()) {
      return CheckResult.bad("No identities found for user");
    }
    if (hasAllowedUserId(key, allowedUserIds)) {
      return CheckResult.trusted();
    }
    return CheckResult.bad("Key does not contain any valid certifications for user's identities");
  }

  private boolean hasAllowedUserId(PGPPublicKey key, Set<String> allowedUserIds)
      throws PGPException {
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

  private Iterator<PGPSignature> getSignaturesForId(PGPPublicKey key, String userId) {
    Iterator<PGPSignature> result = key.getSignaturesForID(userId);
    return result != null ? result : Collections.emptyIterator();
  }

  private Set<String> getAllowedUserIds(IdentifiedUser user) {
    Set<String> result = new HashSet<>();
    result.addAll(user.getEmailAddresses());
    for (ExternalId extId : user.state().externalIds()) {
      if (extId.isScheme(SCHEME_GPGKEY)) {
        continue; // Omit GPG keys.
      }
      result.add(extId.key().get());
    }
    return result;
  }

  private static boolean isAllowed(String userId, Set<String> allowedUserIds) {
    return allowedUserIds.contains(userId)
        || allowedUserIds.contains(PushCertificateIdent.parse(userId).getEmailAddress());
  }

  private static boolean isValidCertification(PGPPublicKey key, PGPSignature sig, String userId)
      throws PGPException {
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
    StringBuilder sb =
        new StringBuilder(
            "Key must contain a valid certification for one of the following identities:\n");
    Iterator<String> sorted = allowedUserIds.stream().sorted().iterator();
    while (sorted.hasNext()) {
      sb.append("  ").append(sorted.next());
      if (sorted.hasNext()) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  static ExternalId.Key toExtIdKey(PGPPublicKey key) {
    return ExternalId.Key.create(SCHEME_GPGKEY, BaseEncoding.base16().encode(key.getFingerprint()));
  }
}
