// Copyright (C) 2026 The Android Open Source Project
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

import static org.bouncycastle.bcpg.SignatureSubpacketTags.KEY_FLAGS;

import com.google.gerrit.common.Nullable;
import java.time.Instant;
import java.util.Iterator;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;

/** Checks the actual signing key, not just the primary key returned by a key-store lookup. */
class CommitSigningKeyChecker {
  static boolean isValid(PGPPublicKey primary, PGPPublicKey signingKey, Instant time) {
    if (creationTime(signingKey).isAfter(time) || !canSign(signingKey)) {
      return false;
    }
    try {
      PGPSignature policy = primaryPolicy(primary, time);
      if (policy == null) {
        return false;
      }
      if (signingKey.isMasterKey()) {
        return (!policy.getHashedSubPackets().hasSubpacket(KEY_FLAGS)
            || (policy.getHashedSubPackets().getKeyFlags() & KeyFlags.SIGN_DATA) != 0);
      }
      PGPSignature binding = null;
      Iterator<PGPSignature> signatures = signingKey.getSignatures();
      while (signatures.hasNext()) {
        PGPSignature signature = signatures.next();
        if (signature.getKeyID() != primary.getKeyID()) {
          continue;
        }
        if (signature.getSignatureType() == PGPSignature.SUBKEY_REVOCATION) {
          signature.init(new BcPGPContentVerifierBuilderProvider(), primary);
          if (signature.verifyCertification(primary, signingKey)) {
            var reason = signature.getHashedSubPackets().getRevocationReason();
            if (reason == null || PublicKeyChecker.isRevocationValid(signature, reason, time)) {
              return false;
            }
          }
        } else if (signature.getSignatureType() == PGPSignature.SUBKEY_BINDING
            && createdBy(signature, time)) {
          signature.init(new BcPGPContentVerifierBuilderProvider(), primary);
          if (signature.verifyCertification(primary, signingKey)) {
            binding = newer(binding, signature);
          }
        }
      }
      if (binding == null
          || !isCurrent(binding, time)
          || (binding.getHashedSubPackets().getKeyFlags() & KeyFlags.SIGN_DATA) == 0) {
        return false;
      }
      if (!unexpired(signingKey, binding, time)) {
        return false;
      }
      // Signing subkeys require the subkey's proof of possession of the primary binding.
      return hasBackSignature(binding.getHashedSubPackets(), primary, signingKey, time)
          || hasBackSignature(binding.getUnhashedSubPackets(), primary, signingKey, time);
    } catch (PGPException | RuntimeException e) {
      // This helper operates only on key packets, never on storage or account services.
      return false;
    }
  }

  /** Authenticated primary validity, also used for recursive certifier and revoker checks. */
  static boolean isPrimaryKeyValid(PGPPublicKey primary, Instant time) {
    try {
      return primaryPolicy(primary, time) != null;
    } catch (PGPException | RuntimeException e) {
      // This helper operates only on key packets, never on storage or account services.
      return false;
    }
  }

  @Nullable
  private static PGPSignature primaryPolicy(PGPPublicKey primary, Instant time)
      throws PGPException {
    if (creationTime(primary).isAfter(time)) {
      return null;
    }
    PGPSignature policy = null;
    Iterator<String> ids = primary.getUserIDs();
    while (ids.hasNext()) {
      String id = ids.next();
      Iterator<PGPSignature> signatures = primary.getSignaturesForID(id);
      while (signatures != null && signatures.hasNext()) {
        PGPSignature signature = signatures.next();
        if (createdBy(signature, time)
            && GerritPublicKeyChecker.isValidCertification(primary, signature, id)) {
          policy = newer(policy, signature);
        }
      }
    }
    Iterator<PGPSignature> directSignatures = primary.getSignaturesOfType(PGPSignature.DIRECT_KEY);
    while (directSignatures.hasNext()) {
      PGPSignature signature = directSignatures.next();
      if (signature.getKeyID() == primary.getKeyID() && createdBy(signature, time)) {
        signature.init(new BcPGPContentVerifierBuilderProvider(), primary);
        if (signature.verifyCertification(primary)) {
          policy = newer(policy, signature);
        }
      }
    }
    return policy != null && isCurrent(policy, time) && unexpired(primary, policy, time)
        ? policy
        : null;
  }

  private static PGPSignature newer(@Nullable PGPSignature old, PGPSignature candidate) {
    return old == null
            || PushCertificateChecker.getCreationTime(candidate)
                .isAfter(PushCertificateChecker.getCreationTime(old))
        ? candidate
        : old;
  }

  private static boolean hasBackSignature(
      @Nullable PGPSignatureSubpacketVector packets,
      PGPPublicKey primary,
      PGPPublicKey signingKey,
      Instant time)
      throws PGPException {
    if (packets == null) {
      return false;
    }
    for (PGPSignature signature : packets.getEmbeddedSignatures()) {
      if (signature.getSignatureType() == PGPSignature.PRIMARYKEY_BINDING
          && signature.getKeyID() == signingKey.getKeyID()
          && isCurrent(signature, time)) {
        signature.init(new BcPGPContentVerifierBuilderProvider(), signingKey);
        if (signature.verifyCertification(primary, signingKey)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean createdBy(PGPSignature signature, Instant time) {
    return signature.getHashedSubPackets() != null
        && signature.getHashedSubPackets().getSignatureCreationTime() != null
        && !PushCertificateChecker.getCreationTime(signature).isAfter(time);
  }

  static boolean isCurrent(PGPSignature signature, Instant time) {
    if (!createdBy(signature, time)) {
      return false;
    }
    long validity = signature.getHashedSubPackets().getSignatureExpirationTime();
    return validity == 0
        || !PushCertificateChecker.getCreationTime(signature).plusSeconds(validity).isBefore(time);
  }

  private static boolean unexpired(PGPPublicKey key, PGPSignature policy, Instant time) {
    // PGPPublicKey.getValidSeconds() also reads unauthenticated and future certifications.
    long validity = policy.getHashedSubPackets().getKeyExpirationTime();
    return validity == 0 || !creationTime(key).plusSeconds(validity).isBefore(time);
  }

  @SuppressWarnings("deprecation")
  private static boolean canSign(PGPPublicKey key) {
    return switch (key.getAlgorithm()) {
      case PublicKeyAlgorithmTags.RSA_GENERAL,
          PublicKeyAlgorithmTags.RSA_SIGN,
          PublicKeyAlgorithmTags.DSA,
          PublicKeyAlgorithmTags.ECDSA,
          PublicKeyAlgorithmTags.EDDSA_LEGACY,
          PublicKeyAlgorithmTags.Ed25519,
          PublicKeyAlgorithmTags.Ed448 ->
          true;
      default -> false;
    };
  }

  @SuppressWarnings("JdkObsolete")
  private static Instant creationTime(PGPPublicKey key) {
    return key.getCreationTime().toInstant();
  }

  private CommitSigningKeyChecker() {}
}
