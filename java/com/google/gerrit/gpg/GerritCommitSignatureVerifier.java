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

import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.IDENTITY_MISMATCH;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.INVALID_KEY;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.INVALID_SIGNATURE;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.MALFORMED_SIGNATURE;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.UNKNOWN_KEY;
import static com.google.gerrit.extensions.common.CommitSignatureInfo.Reason.UNSUPPORTED_FORMAT;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.extensions.common.CommitSignatureInfo.Reason;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.git.CommitSignatureVerifier;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushCertificateIdent;

/** OpenPGP verification of commit signatures. */
@Singleton
public class GerritCommitSignatureVerifier implements CommitSignatureVerifier {
  private final Provider<PublicKeyStore> storeProvider;
  private final GerritPublicKeyChecker.Factory checkerFactory;
  private final ExternalIds externalIds;
  private final ExternalIdKeyFactory externalIdKeyFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final ServerSigningKeys serverSigningKeys;

  @Inject
  GerritCommitSignatureVerifier(
      Provider<PublicKeyStore> storeProvider,
      GerritPublicKeyChecker.Factory checkerFactory,
      ExternalIds externalIds,
      ExternalIdKeyFactory externalIdKeyFactory,
      IdentifiedUser.GenericFactory userFactory,
      ServerSigningKeys serverSigningKeys) {
    this.storeProvider = storeProvider;
    this.checkerFactory = checkerFactory;
    this.externalIds = externalIds;
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.userFactory = userFactory;
    this.serverSigningKeys = serverSigningKeys;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public CommitSignatureInfo verify(byte[] rawCommit) throws IOException {
    CommitSignatureInfo info = new CommitSignatureInfo();
    SignedPayload signed;
    try {
      signed = extract(rawCommit);
    } catch (IllegalArgumentException e) {
      return unverified(info, MALFORMED_SIGNATURE, "The commit has malformed signature headers.");
    }
    if (signed.unsupportedHeader()) {
      return unverified(
          info, UNSUPPORTED_FORMAT, "The commit uses an unsupported signature header.");
    }
    if (signed.signature() == null) {
      info.status = CommitSignatureInfo.Status.UNSIGNED;
      return info;
    }
    if (!new String(signed.signature(), US_ASCII).startsWith("-----BEGIN PGP SIGNATURE-----")) {
      return unverified(info, UNSUPPORTED_FORMAT, "Only OpenPGP commit signatures are supported.");
    }

    PGPSignature signature;
    try {
      signature = readSignature(signed.signature());
    } catch (IOException | PGPException | IllegalArgumentException e) {
      return unverified(info, MALFORMED_SIGNATURE, "The OpenPGP signature cannot be parsed.");
    }
    if (signature.getSignatureType() != PGPSignature.BINARY_DOCUMENT) {
      return unverified(
          info, UNSUPPORTED_FORMAT, "An exact-payload OpenPGP signature is required.");
    }
    Instant time = PushCertificateChecker.getCreationTime(signature);

    var serverCandidates = serverSigningKeys.get(signature.getKeyID());
    PGPPublicKeyRing serverRing = findSigner(serverCandidates, signature, signed.payload());
    if (serverRing != null) {
      PGPPublicKey signingKey = serverRing.getPublicKey(signature.getKeyID());
      info.signingKeyFingerprint = fingerprint(signingKey);
      info.primaryKeyFingerprint = fingerprint(serverRing.getPublicKey());
      if (!serverSigningKeys.isValid(serverRing, signingKey, time)) {
        return unverified(
            info, INVALID_KEY, "The server attestation key is not valid at the signature time.");
      }
      info.status = CommitSignatureInfo.Status.VERIFIED;
      info.signerType = CommitSignatureInfo.SignerType.SERVER;
      info.message =
          "Verified server attestation. This signature does not identify the committer as the"
              + " signer.";
      return info;
    }

    try (PublicKeyStore store = storeProvider.get()) {
      PGPPublicKeyRingCollection candidates;
      try {
        candidates = store.get(signature.getKeyID());
      } catch (PGPException e) {
        throw new IOException("Cannot read registered public keys", e);
      }
      if (!candidates.getKeyRings().hasNext()) {
        if (serverCandidates.getKeyRings().hasNext()) {
          return unverified(
              info, INVALID_SIGNATURE, "The signature does not match the commit bytes.");
        }
        return unverified(
            info,
            UNKNOWN_KEY,
            "The signing key is neither registered nor configured for server attestation.");
      }
      PGPPublicKeyRing ring = findSigner(candidates, signature, signed.payload());
      if (ring == null) {
        return unverified(
            info, INVALID_SIGNATURE, "The signature does not match the commit bytes.");
      }
      PGPPublicKey primary = ring.getPublicKey();
      PGPPublicKey signingKey = ring.getPublicKey(signature.getKeyID());
      info.signingKeyFingerprint = fingerprint(signingKey);
      info.primaryKeyFingerprint = fingerprint(primary);
      if (!CommitSigningKeyChecker.isValid(primary, signingKey, time)) {
        return unverified(
            info,
            INVALID_KEY,
            "The signing key is not valid or authorized for signing at the signature time.");
      }

      // This is an internal association, not account discovery: never filter by visibility.
      Optional<ExternalId> registration =
          externalIds.get(
              externalIdKeyFactory.create(ExternalId.SCHEME_GPGKEY, fingerprint(primary)));
      if (registration.isEmpty()) {
        return unverified(
            info, IDENTITY_MISMATCH, "The key has no unique registered signing account.");
      }
      IdentifiedUser signer = userFactory.create(registration.get().accountId());
      CheckResult keyResult =
          checkerFactory
              .create(signer, store)
              .enableCommitSignatureChecks()
              .setFailOnError()
              .setEffectiveTime(time)
              .check(primary);
      if (!keyResult.isTrusted()) {
        // Do not return checker problems: they can contain private identities or arbitrary key
        // UIDs.
        return unverified(
            info, INVALID_KEY, "The key does not satisfy the server's validity and trust policy.");
      }
      PersonIdent committer = RevCommit.parse(rawCommit).getCommitterIdent();
      if (committer == null
          || !signer.getEmailAddresses().contains(committer.getEmailAddress())
          || !hasCertifiedEmail(primary, committer.getEmailAddress(), time)) {
        return unverified(
            info,
            IDENTITY_MISMATCH,
            "The committer email does not match the signing account and certified key identity.");
      }
      info.status = CommitSignatureInfo.Status.VERIFIED;
      info.signer = new AccountInfo(signer.getAccountId().get());
      info.signerType = CommitSignatureInfo.SignerType.USER;
      return info;
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static CommitSignatureInfo unverified(
      CommitSignatureInfo info, Reason reason, String message) {
    info.status = CommitSignatureInfo.Status.UNVERIFIED;
    info.reason = reason;
    info.message = message;
    return info;
  }

  private static String fingerprint(PGPPublicKey key) {
    return BaseEncoding.base16().encode(key.getFingerprint());
  }

  private static PGPSignature readSignature(byte[] bytes) throws IOException, PGPException {
    // ArmoredInputStream stops at the first armor block, ignoring any following blocks or data.
    String armor = new String(bytes, US_ASCII);
    String endMarker = "\n-----END PGP SIGNATURE-----";
    int end = armor.indexOf(endMarker);
    if (end < 0 || !armor.substring(end + endMarker.length()).isBlank()) {
      throw new PGPException("Expected a single complete signature armor block");
    }
    try (ArmoredInputStream in = new ArmoredInputStream(new ByteArrayInputStream(bytes))) {
      BcPGPObjectFactory factory = new BcPGPObjectFactory(in);
      if (!(factory.nextObject() instanceof PGPSignatureList signatures)
          || signatures.size() != 1
          || factory.nextObject() != null) {
        throw new PGPException("Expected a single detached signature");
      }
      PGPSignature signature = signatures.get(0);
      if (signature.getHashedSubPackets() == null
          || signature.getHashedSubPackets().getSignatureCreationTime() == null) {
        throw new PGPException("Missing authenticated signature creation time");
      }
      return signature;
    }
  }

  @Nullable
  private static PGPPublicKeyRing findSigner(
      PGPPublicKeyRingCollection candidates, PGPSignature signature, byte[] payload) {
    for (PGPPublicKeyRing ring : candidates) {
      PGPPublicKey key = ring.getPublicKey(signature.getKeyID());
      if (key == null) {
        continue;
      }
      try {
        signature.init(new BcPGPContentVerifierBuilderProvider(), key);
        signature.update(payload);
        if (signature.verify()) {
          return ring;
        }
      } catch (PGPException | IllegalArgumentException e) {
        // A malformed or incompatible candidate must not prevent trying a colliding key ID.
      }
    }
    return null;
  }

  private static boolean hasCertifiedEmail(PGPPublicKey primary, String email, Instant time) {
    Iterator<String> ids = primary.getUserIDs();
    while (ids.hasNext()) {
      String id = ids.next();
      if (!email.equals(PushCertificateIdent.parse(id).getEmailAddress())) {
        continue;
      }
      PGPSignature certification = null;
      Instant revoked = null;
      Iterator<PGPSignature> signatures = primary.getSignaturesForID(id);
      while (signatures != null && signatures.hasNext()) {
        try {
          PGPSignature signature = signatures.next();
          if (!CommitSigningKeyChecker.createdBy(signature, time)) {
            continue;
          }
          Instant created = PushCertificateChecker.getCreationTime(signature);
          if (signature.getSignatureType() == PGPSignature.CERTIFICATION_REVOCATION
              && signature.getKeyID() == primary.getKeyID()) {
            signature.init(new BcPGPContentVerifierBuilderProvider(), primary);
            if (signature.verifyCertification(id, primary)
                && (revoked == null || created.isAfter(revoked))) {
              revoked = created;
            }
          } else if (GerritPublicKeyChecker.isValidCertification(primary, signature, id)
              && (certification == null
                  || created.isAfter(PushCertificateChecker.getCreationTime(certification)))) {
            certification = signature;
          }
        } catch (PGPException | RuntimeException e) {
          // Invalid certification is not an identity binding.
        }
      }
      if (certification != null
          && CommitSigningKeyChecker.isCurrent(certification, time)
          && (revoked == null
              || PushCertificateChecker.getCreationTime(certification).isAfter(revoked))) {
        return true;
      }
    }
    return false;
  }

  record SignedPayload(byte[] payload, @Nullable byte[] signature, boolean unsupportedHeader) {}

  /** Removes only the signature header and its continuations, preserving all other bytes. */
  static SignedPayload extract(byte[] raw) {
    ByteArrayOutputStream payload = new ByteArrayOutputStream(raw.length);
    ByteArrayOutputStream signature = null;
    boolean signatureHeader = false;
    boolean unsupportedHeader = false;
    int start = 0;
    while (start < raw.length) {
      int end = start;
      while (end < raw.length && raw[end] != '\n') {
        end++;
      }
      if (end == start) {
        payload.write(raw, start, raw.length - start);
        break;
      }
      int next = end < raw.length ? end + 1 : end;
      if (raw[start] == ' ' && signatureHeader) {
        signature.write(raw, start + 1, end - start - 1);
        signature.write('\n');
      } else {
        String header = new String(raw, start, end - start, US_ASCII);
        if (header.equals("gpgsig") || header.startsWith("gpgsig ")) {
          if (signature != null || header.equals("gpgsig")) {
            throw new IllegalArgumentException("Duplicate or empty signature header");
          }
          signature = new ByteArrayOutputStream();
          signature.write(raw, start + 7, end - start - 7);
          signature.write('\n');
          signatureHeader = true;
        } else {
          signatureHeader = false;
          unsupportedHeader |= header.startsWith("gpgsig-");
          payload.write(raw, start, next - start);
        }
      }
      start = next;
    }
    return new SignedPayload(
        payload.toByteArray(),
        signature != null ? signature.toByteArray() : null,
        unsupportedHeader);
  }
}
