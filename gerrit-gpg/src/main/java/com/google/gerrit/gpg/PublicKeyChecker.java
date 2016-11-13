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

import static com.google.gerrit.extensions.common.GpgKeyInfo.Status.BAD;
import static com.google.gerrit.extensions.common.GpgKeyInfo.Status.OK;
import static com.google.gerrit.extensions.common.GpgKeyInfo.Status.TRUSTED;
import static com.google.gerrit.gpg.PublicKeyStore.keyIdToString;
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;
import static org.bouncycastle.bcpg.SignatureSubpacketTags.REVOCATION_KEY;
import static org.bouncycastle.bcpg.SignatureSubpacketTags.REVOCATION_REASON;
import static org.bouncycastle.bcpg.sig.RevocationReasonTags.KEY_COMPROMISED;
import static org.bouncycastle.bcpg.sig.RevocationReasonTags.KEY_RETIRED;
import static org.bouncycastle.bcpg.sig.RevocationReasonTags.KEY_SUPERSEDED;
import static org.bouncycastle.bcpg.sig.RevocationReasonTags.NO_REASON;
import static org.bouncycastle.openpgp.PGPSignature.DIRECT_KEY;
import static org.bouncycastle.openpgp.PGPSignature.KEY_REVOCATION;

import com.google.gerrit.extensions.common.GpgKeyInfo.Status;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.bcpg.SignatureSubpacket;
import org.bouncycastle.bcpg.SignatureSubpacketTags;
import org.bouncycastle.bcpg.sig.RevocationKey;
import org.bouncycastle.bcpg.sig.RevocationReason;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checker for GPG public keys for use in a push certificate. */
public class PublicKeyChecker {
  private static final Logger log = LoggerFactory.getLogger(PublicKeyChecker.class);

  // https://tools.ietf.org/html/rfc4880#section-5.2.3.13
  private static final int COMPLETE_TRUST = 120;

  private PublicKeyStore store;
  private Map<Long, Fingerprint> trusted;
  private int maxTrustDepth;
  private Date effectiveTime = new Date();

  /**
   * Enable web-of-trust checks.
   *
   * <p>If enabled, a store must be set with {@link #setStore(PublicKeyStore)}. (These methods are
   * separate since the store is a closeable resource that may not be available when reading trusted
   * keys from a config.)
   *
   * @param maxTrustDepth maximum depth to search while looking for a trusted key.
   * @param trusted ultimately trusted key fingerprints, keyed by fingerprint; may not be empty. To
   *     construct a map, see {@link Fingerprint#byId(Iterable)}.
   * @return a reference to this object.
   */
  public PublicKeyChecker enableTrust(int maxTrustDepth, Map<Long, Fingerprint> trusted) {
    if (maxTrustDepth <= 0) {
      throw new IllegalArgumentException("maxTrustDepth must be positive, got: " + maxTrustDepth);
    }
    if (trusted == null || trusted.isEmpty()) {
      throw new IllegalArgumentException("at least one trusted key is required");
    }
    this.maxTrustDepth = maxTrustDepth;
    this.trusted = trusted;
    return this;
  }

  /** Disable web-of-trust checks. */
  public PublicKeyChecker disableTrust() {
    trusted = null;
    return this;
  }

  /** Set the public key store for reading keys referenced in signatures. */
  public PublicKeyChecker setStore(PublicKeyStore store) {
    if (store == null) {
      throw new IllegalArgumentException("PublicKeyStore is required");
    }
    this.store = store;
    return this;
  }

  /**
   * Set the effective time for checking the key.
   *
   * <p>If set, check whether the key should be considered valid (e.g. unexpired) as of this time.
   *
   * @param effectiveTime effective time.
   * @return a reference to this object.
   */
  public PublicKeyChecker setEffectiveTime(Date effectiveTime) {
    this.effectiveTime = effectiveTime;
    return this;
  }

  protected Date getEffectiveTime() {
    return effectiveTime;
  }

  /**
   * Check a public key.
   *
   * @param key the public key.
   * @return the result of the check.
   */
  public final CheckResult check(PGPPublicKey key) {
    if (store == null) {
      throw new IllegalStateException("PublicKeyStore is required");
    }
    return check(key, 0, true, trusted != null ? new HashSet<Fingerprint>() : null);
  }

  /**
   * Perform custom checks.
   *
   * <p>Default implementation reports no problems, but may be overridden by subclasses.
   *
   * @param key the public key.
   * @param depth the depth from the initial key passed to {@link #check( PGPPublicKey)}: 0 if this
   *     was the initial key, up to a maximum of {@code maxTrustDepth}.
   * @return the result of the custom check.
   */
  public CheckResult checkCustom(PGPPublicKey key, int depth) {
    return CheckResult.ok();
  }

  private CheckResult check(PGPPublicKey key, int depth, boolean expand, Set<Fingerprint> seen) {
    CheckResult basicResult = checkBasic(key, effectiveTime);
    CheckResult customResult = checkCustom(key, depth);
    CheckResult trustResult = checkWebOfTrust(key, store, depth, seen);
    if (!expand && !trustResult.isTrusted()) {
      trustResult = CheckResult.create(trustResult.getStatus(), "Key is not trusted");
    }

    List<String> problems =
        new ArrayList<>(
            basicResult.getProblems().size()
                + customResult.getProblems().size()
                + trustResult.getProblems().size());
    problems.addAll(basicResult.getProblems());
    problems.addAll(customResult.getProblems());
    problems.addAll(trustResult.getProblems());

    Status status;
    if (basicResult.getStatus() == BAD
        || customResult.getStatus() == BAD
        || trustResult.getStatus() == BAD) {
      // Any BAD result and the final result is BAD.
      status = BAD;
    } else if (trustResult.getStatus() == TRUSTED) {
      // basicResult is BAD or OK, whereas trustResult is BAD or TRUSTED. If
      // TRUSTED, we trust the final result.
      status = TRUSTED;
    } else {
      // All results were OK or better, but trustResult was not TRUSTED. Don't
      // let subclasses bypass checkWebOfTrust by returning TRUSTED; just return
      // OK here.
      status = OK;
    }
    return CheckResult.create(status, problems);
  }

  private CheckResult checkBasic(PGPPublicKey key, Date now) {
    List<String> problems = new ArrayList<>(2);
    gatherRevocationProblems(key, now, problems);

    long validMs = key.getValidSeconds() * 1000;
    if (validMs != 0) {
      long msSinceCreation = now.getTime() - key.getCreationTime().getTime();
      if (msSinceCreation > validMs) {
        problems.add("Key is expired");
      }
    }
    return CheckResult.create(problems);
  }

  private void gatherRevocationProblems(PGPPublicKey key, Date now, List<String> problems) {
    try {
      List<PGPSignature> revocations = new ArrayList<>();
      Map<Long, RevocationKey> revokers = new HashMap<>();
      PGPSignature selfRevocation = scanRevocations(key, now, revocations, revokers);
      if (selfRevocation != null) {
        RevocationReason reason = getRevocationReason(selfRevocation);
        if (isRevocationValid(selfRevocation, reason, now)) {
          problems.add(reasonToString(reason));
        }
      } else {
        checkRevocations(key, revocations, revokers, problems);
      }
    } catch (PGPException | IOException e) {
      problems.add("Error checking key revocation");
    }
  }

  private static boolean isRevocationValid(
      PGPSignature revocation, RevocationReason reason, Date now) {
    // RFC4880 states:
    // "If a key has been revoked because of a compromise, all signatures
    // created by that key are suspect. However, if it was merely superseded or
    // retired, old signatures are still valid."
    //
    // Note that GnuPG does not implement this correctly, as it does not
    // consider the revocation reason and timestamp when checking whether a
    // signature (data or certification) is valid.
    return reason.getRevocationReason() == KEY_COMPROMISED
        || revocation.getCreationTime().before(now);
  }

  private PGPSignature scanRevocations(
      PGPPublicKey key, Date now, List<PGPSignature> revocations, Map<Long, RevocationKey> revokers)
      throws PGPException {
    @SuppressWarnings("unchecked")
    Iterator<PGPSignature> allSigs = key.getSignatures();
    while (allSigs.hasNext()) {
      PGPSignature sig = allSigs.next();
      switch (sig.getSignatureType()) {
        case KEY_REVOCATION:
          if (sig.getKeyID() == key.getKeyID()) {
            sig.init(new BcPGPContentVerifierBuilderProvider(), key);
            if (sig.verifyCertification(key)) {
              return sig;
            }
          } else {
            RevocationReason reason = getRevocationReason(sig);
            if (reason != null && isRevocationValid(sig, reason, now)) {
              revocations.add(sig);
            }
          }
          break;
        case DIRECT_KEY:
          RevocationKey r = getRevocationKey(key, sig);
          if (r != null) {
            revokers.put(Fingerprint.getId(r.getFingerprint()), r);
          }
          break;
      }
    }
    return null;
  }

  private RevocationKey getRevocationKey(PGPPublicKey key, PGPSignature sig) throws PGPException {
    if (sig.getKeyID() != key.getKeyID()) {
      return null;
    }
    SignatureSubpacket sub = sig.getHashedSubPackets().getSubpacket(REVOCATION_KEY);
    if (sub == null) {
      return null;
    }
    sig.init(new BcPGPContentVerifierBuilderProvider(), key);
    if (!sig.verifyCertification(key)) {
      return null;
    }

    return new RevocationKey(sub.isCritical(), sub.isLongLength(), sub.getData());
  }

  private void checkRevocations(
      PGPPublicKey key,
      List<PGPSignature> revocations,
      Map<Long, RevocationKey> revokers,
      List<String> problems)
      throws PGPException, IOException {
    for (PGPSignature revocation : revocations) {
      RevocationKey revoker = revokers.get(revocation.getKeyID());
      if (revoker == null) {
        continue; // Not a designated revoker.
      }
      byte[] rfp = revoker.getFingerprint();
      PGPPublicKeyRing revokerKeyRing = store.get(rfp);
      if (revokerKeyRing == null) {
        // Revoker is authorized and there is a revocation signature by this
        // revoker, but the key is not in the store so we can't verify the
        // signature.
        log.info(
            "Key "
                + Fingerprint.toString(key.getFingerprint())
                + " is revoked by "
                + Fingerprint.toString(rfp)
                + ", which is not in the store. Assuming revocation is valid.");
        problems.add(reasonToString(getRevocationReason(revocation)));
        continue;
      }
      PGPPublicKey rk = revokerKeyRing.getPublicKey();
      if (rk.getAlgorithm() != revoker.getAlgorithm()) {
        continue;
      }
      if (!checkBasic(rk, revocation.getCreationTime()).isOk()) {
        // Revoker's key was expired or revoked at time of revocation, so the
        // revocation is invalid.
        continue;
      }
      revocation.init(new BcPGPContentVerifierBuilderProvider(), rk);
      if (revocation.verifyCertification(key)) {
        problems.add(reasonToString(getRevocationReason(revocation)));
      }
    }
  }

  private static RevocationReason getRevocationReason(PGPSignature sig) {
    if (sig.getSignatureType() != KEY_REVOCATION) {
      throw new IllegalArgumentException(
          "Expected KEY_REVOCATION signature, got " + sig.getSignatureType());
    }
    SignatureSubpacket sub = sig.getHashedSubPackets().getSubpacket(REVOCATION_REASON);
    if (sub == null) {
      return null;
    }
    return new RevocationReason(sub.isCritical(), sub.isLongLength(), sub.getData());
  }

  private static String reasonToString(RevocationReason reason) {
    StringBuilder r = new StringBuilder("Key is revoked (");
    if (reason == null) {
      return r.append("no reason provided)").toString();
    }
    switch (reason.getRevocationReason()) {
      case NO_REASON:
        r.append("no reason code specified");
        break;
      case KEY_SUPERSEDED:
        r.append("superseded");
        break;
      case KEY_COMPROMISED:
        r.append("key material has been compromised");
        break;
      case KEY_RETIRED:
        r.append("retired and no longer valid");
        break;
      default:
        r.append("reason code ").append(Integer.toString(reason.getRevocationReason())).append(')');
        break;
    }
    r.append(')');
    String desc = reason.getRevocationDescription();
    if (!desc.isEmpty()) {
      r.append(": ").append(desc);
    }
    return r.toString();
  }

  private CheckResult checkWebOfTrust(
      PGPPublicKey key, PublicKeyStore store, int depth, Set<Fingerprint> seen) {
    if (trusted == null) {
      // Trust checking not configured, server trusts all OK keys.
      return CheckResult.trusted();
    }
    Fingerprint fp = new Fingerprint(key.getFingerprint());
    if (seen.contains(fp)) {
      return CheckResult.ok("Key is trusted in a cycle");
    }
    seen.add(fp);

    Fingerprint trustedFp = trusted.get(key.getKeyID());
    if (trustedFp != null && trustedFp.equals(fp)) {
      return CheckResult.trusted(); // Directly trusted.
    } else if (depth >= maxTrustDepth) {
      return CheckResult.ok("No path of depth <= " + maxTrustDepth + " to a trusted key");
    }

    List<CheckResult> signerResults = new ArrayList<>();
    @SuppressWarnings("unchecked")
    Iterator<String> userIds = key.getUserIDs();
    while (userIds.hasNext()) {
      String userId = userIds.next();

      // Don't check the timestamp of these certifications. This allows admins
      // to correct untrusted keys by signing them with a trusted key, such that
      // older signatures created by those keys retroactively appear valid.
      Iterator<PGPSignature> sigs = key.getSignaturesForID(userId);

      while (sigs.hasNext()) {
        PGPSignature sig = sigs.next();
        // TODO(dborowitz): Handle CERTIFICATION_REVOCATION.
        if (sig.getSignatureType() != PGPSignature.DEFAULT_CERTIFICATION
            && sig.getSignatureType() != PGPSignature.POSITIVE_CERTIFICATION) {
          continue; // Not a certification.
        }

        PGPPublicKey signer = getSigner(store, sig, userId, key, signerResults);
        // TODO(dborowitz): Require self certification.
        if (signer == null || Arrays.equals(signer.getFingerprint(), key.getFingerprint())) {
          continue;
        }
        String subpacketProblem = checkTrustSubpacket(sig, depth);
        if (subpacketProblem == null) {
          CheckResult signerResult = check(signer, depth + 1, false, seen);
          if (signerResult.isTrusted()) {
            return CheckResult.trusted();
          }
        }
        signerResults.add(
            CheckResult.ok(
                "Certification by " + keyToString(signer) + " is valid, but key is not trusted"));
      }
    }

    List<String> problems = new ArrayList<>();
    problems.add("No path to a trusted key");
    for (CheckResult signerResult : signerResults) {
      problems.addAll(signerResult.getProblems());
    }
    return CheckResult.create(OK, problems);
  }

  private static PGPPublicKey getSigner(
      PublicKeyStore store,
      PGPSignature sig,
      String userId,
      PGPPublicKey key,
      List<CheckResult> results) {
    try {
      PGPPublicKeyRingCollection signers = store.get(sig.getKeyID());
      if (!signers.getKeyRings().hasNext()) {
        results.add(
            CheckResult.ok(
                "Key "
                    + keyIdToString(sig.getKeyID())
                    + " used for certification is not in store"));
        return null;
      }
      PGPPublicKey signer = PublicKeyStore.getSigner(signers, sig, userId, key);
      if (signer == null) {
        results.add(
            CheckResult.ok("Certification by " + keyIdToString(sig.getKeyID()) + " is not valid"));
        return null;
      }
      return signer;
    } catch (PGPException | IOException e) {
      results.add(
          CheckResult.ok("Error checking certification by " + keyIdToString(sig.getKeyID())));
      return null;
    }
  }

  private String checkTrustSubpacket(PGPSignature sig, int depth) {
    SignatureSubpacket trustSub =
        sig.getHashedSubPackets().getSubpacket(SignatureSubpacketTags.TRUST_SIG);
    if (trustSub == null || trustSub.getData().length != 2) {
      return "Certification is missing trust information";
    }
    byte amount = trustSub.getData()[1];
    if (amount < COMPLETE_TRUST) {
      return "Certification does not fully trust key";
    }
    byte level = trustSub.getData()[0];
    int required = depth + 1;
    if (level < required) {
      return "Certification trusts to depth " + level + ", but depth " + required + " is required";
    }
    return null;
  }
}
