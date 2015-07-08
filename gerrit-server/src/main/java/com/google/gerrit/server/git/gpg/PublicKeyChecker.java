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
import static com.google.gerrit.server.git.gpg.PublicKeyStore.keyToString;
import static org.bouncycastle.openpgp.PGPSignature.CERTIFICATION_REVOCATION;
import static org.bouncycastle.openpgp.PGPSignature.DEFAULT_CERTIFICATION;
import static org.bouncycastle.openpgp.PGPSignature.POSITIVE_CERTIFICATION;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Checker for GPG public keys for use in a push certificate. */
public class PublicKeyChecker {
  private static final Logger log =
      LoggerFactory.getLogger(PublicKeyChecker.class);

  /**
   * Check a public key.
   *
   * @param key the public key.
   * @param expectedKeyId the key ID that the caller expects.
   * @param expectedUserId a user ID that the caller expects to be present and
   *     correct.
   */
  public final CheckResult check(PGPPublicKey key, long expectedKeyId,
      String expectedUserId) {
    List<String> problems = new ArrayList<>();
    if (key.getKeyID() != expectedKeyId) {
      problems.add(
          "Public key does not match ID " + keyIdToString(expectedKeyId));
    }
    if (key.isRevoked()) {
      // TODO(dborowitz): isRevoked is overeager:
      // http://www.bouncycastle.org/jira/browse/BJB-45
      problems.add("Key is revoked");
    }

    long validSecs = key.getValidSeconds();
    if (validSecs != 0) {
      long createdSecs = key.getCreationTime().getTime() / 1000;
      long nowSecs = System.currentTimeMillis() / 1000;
      if (nowSecs - createdSecs > validSecs) {
        problems.add("Key is expired");
      }
    }
    checkCertifications(key, expectedUserId, problems);
    checkCustom(key, expectedKeyId, expectedUserId, problems);
    return new CheckResult(problems);
  }

  /**
   * Perform custom checks.
   * <p>
   * Default implementation does nothing, but may be overridden by subclasses.
   *
   * @param key the public key.
   * @param expectedKeyId the key ID that the caller expects.
   * @param expectedUserId a user ID that the caller expects to be present and
   *     correct.
   * @param problems list to which any problems should be added.
   */
  public void checkCustom(PGPPublicKey key, long expectedKeyId,
      String expectedUserId, List<String> problems) {
    // Default implementation does nothing.
  }

  // TODO(dborowitz): Remove some/all of these checks.
  private static void checkCertifications(PGPPublicKey key, String userId,
      List<String> problems) {
    @SuppressWarnings("unchecked")
    Iterator<PGPSignature> sigs = key.getSignaturesForID(userId);
    if (sigs == null) {
      sigs = Collections.emptyIterator();
    }
    boolean ok = false;
    boolean revoked = false;
    try {
      while (sigs.hasNext()) {
        PGPSignature sig = sigs.next();
        if (sig.getKeyID() != key.getKeyID()) {
          // TODO(dborowitz): Support certifications by other trusted keys?
          continue;
        } else if (sig.getSignatureType() != DEFAULT_CERTIFICATION
            && sig.getSignatureType() != POSITIVE_CERTIFICATION
            && sig.getSignatureType() != CERTIFICATION_REVOCATION) {
          continue;
        }
        sig.init(new BcPGPContentVerifierBuilderProvider(), key);
        if (sig.verifyCertification(userId, key)) {
          if (sig.getSignatureType() == CERTIFICATION_REVOCATION) {
            revoked = true;
          } else {
            ok = true;
          }
        } else {
          problems.add("Invalid signature for User ID " + userId);
        }
      }
    } catch (PGPException e) {
      problems.add("Error in certifications");
      log.warn("Error in certification verification for public key: "
          + keyToString(key), e);
    }

    if (revoked) {
      problems.add("User ID " + userId + " is revoked");
    } else if (!ok) {
      problems.add("No certification for User ID " + userId);
    }
  }
}
