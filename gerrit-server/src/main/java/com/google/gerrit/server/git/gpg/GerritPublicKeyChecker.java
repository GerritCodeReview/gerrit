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

package com.google.gerrit.server.git.gpg;

import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyIdToString;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public interface Factory {
    public GerritPublicKeyChecker create(ReviewDb db, IdentifiedUser user);
  }

  private final String webUrl;
  private final ReviewDb db;
  private final IdentifiedUser user;

  @AssistedInject
  GerritPublicKeyChecker(
      @CanonicalWebUrl String webUrl,
      @Assisted ReviewDb db,
      @Assisted IdentifiedUser user) {
    this.webUrl = webUrl;
    this.db = db;
    this.user = user;
  }

  @Override
  public void checkCustom(PGPPublicKey key, long expectedKeyId,
      List<String> problems) {
    try {
      Set<String> allowedUserIds = getAllowedUserIds();
      if (allowedUserIds.isEmpty()) {
        problems.add("No identities found for user; check "
            + webUrl + "#settings/web-identities");
        return;
      }

      @SuppressWarnings("unchecked")
      Iterator<String> userIds = key.getUserIDs();
      while (userIds.hasNext()) {
        String userId = userIds.next();
        if (isAllowed(userId, allowedUserIds)) {
          @SuppressWarnings("unchecked")
          Iterator<PGPSignature> sigs = key.getSignaturesForID(userId);
          while (sigs.hasNext()) {
            if (isValidCertification(key, sigs.next(), userId)) {
              return;
            }
          }
        }
      }

      problems.add(missingUserIds(allowedUserIds));
    } catch (PGPException | OrmException e) {
      String msg = "Error checking user IDs for key";
      log.warn(msg + " " + keyIdToString(key.getKeyID()), e);
      problems.add(msg);
    }
  }

  private Set<String> getAllowedUserIds() throws OrmException {
    // TODO(dborowitz): Include preferred email?
    Set<String> result = new HashSet<>();
    for (AccountExternalId extId :
        db.accountExternalIds().byAccount(user.getAccountId())) {
      result.add(extId.getExternalId());
      if (!Strings.isNullOrEmpty(extId.getEmailAddress())) {
        result.add(extId.getEmailAddress());
      }
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
}
