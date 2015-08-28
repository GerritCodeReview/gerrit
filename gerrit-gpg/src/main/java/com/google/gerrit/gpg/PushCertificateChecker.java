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
import static com.google.gerrit.gpg.PublicKeyStore.keyToString;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Checker for push certificates. */
public abstract class PushCertificateChecker {
  private final PublicKeyChecker publicKeyChecker;

  protected PushCertificateChecker(PublicKeyChecker publicKeyChecker) {
    this.publicKeyChecker = publicKeyChecker;
  }

  /**
   * Check a push certificate.
   *
   * @return result of the check.
   * @throws PGPException if an error occurred during GPG checks.
   * @throws IOException if an error occurred reading from the repository.
   */
  public final CheckResult check(PushCertificate cert) throws PGPException, IOException {
    if (cert.getNonceStatus() != NonceStatus.OK) {
      return new CheckResult("Invalid nonce");
    }
    PGPSignature sig = readSignature(cert);
    if (sig == null) {
      return new CheckResult("Invalid signature format");
    }
    Repository repo = getRepository();
    List<String> problems = new ArrayList<>();
    try (PublicKeyStore store = new PublicKeyStore(repo)) {
      checkSignature(sig, cert, store.get(sig.getKeyID()), problems);
      checkCustom(repo, problems);
      return new CheckResult(problems);
    } finally {
      if (shouldClose(repo)) {
        repo.close();
      }
    }
  }

  /**
   * Get the repository that this checker should operate on.
   * <p>
   * This method is called once per call to {@link #check(PushCertificate)}.
   *
   * @return the repository.
   * @throws IOException if an error occurred reading the repository.
   */
  protected abstract Repository getRepository() throws IOException;

  /**
   * @param repo a repository previously returned by {@link #getRepository()}.
   * @return whether this repository should be closed before returning from
   *     {@link #check(PushCertificate)}.
   */
  protected abstract boolean shouldClose(Repository repo);

  /**
   * Perform custom checks.
   * <p>
   * Default implementation does nothing, but may be overridden by subclasses.
   *
   * @param repo a repository previously returned by {@link #getRepository()}.
   * @param problems list to which any problems should be added.
   */
  protected void checkCustom(Repository repo, List<String> problems) {
    // Default implementation does nothing.
  }

  private PGPSignature readSignature(PushCertificate cert) throws IOException {
    ArmoredInputStream in = new ArmoredInputStream(
        new ByteArrayInputStream(Constants.encode(cert.getSignature())));
    PGPObjectFactory factory = new BcPGPObjectFactory(in);
    Object obj;
    while ((obj = factory.nextObject()) != null) {
      if (obj instanceof PGPSignatureList) {
        PGPSignatureList sigs = (PGPSignatureList) obj;
        if (!sigs.isEmpty()) {
          return sigs.get(0);
        }
      }
    }
    return null;
  }

  private void checkSignature(PGPSignature sig,
      PushCertificate cert, PGPPublicKeyRingCollection keys,
      List<String> problems) {
    List<String> deferredProblems = new ArrayList<>();
    boolean anyKeys = false;
    for (PGPPublicKeyRing kr : keys) {
      PGPPublicKey k = kr.getPublicKey();
      anyKeys = true;
      try {
        sig.init(new BcPGPContentVerifierBuilderProvider(), k);
        sig.update(Constants.encode(cert.toText()));
        if (!sig.verify()) {
          // TODO(dborowitz): Privacy issues with exposing fingerprint/user ID
          // of keys having the same ID as the pusher's key?
          deferredProblems.add(
              "Signature not valid with public key: " + keyToString(k));
          continue;
        }
        CheckResult result = publicKeyChecker.check(k, sig.getKeyID());
        if (result.isOk()) {
          return;
        }
        StringBuilder err = new StringBuilder("Invalid public key (")
            .append(keyToString(k))
            .append("):");
        for (int i = 0; i < result.getProblems().size(); i++) {
          err.append('\n').append("  ").append(result.getProblems().get(i));
        }
        problems.add(err.toString());
        return;
      } catch (PGPException e) {
        deferredProblems.add(
            "Error checking signature with public key (" + keyToString(k)
            + ": " + e.getMessage());
      }
    }
    if (!anyKeys) {
      problems.add(
          "No public keys found for Key ID " + keyIdToString(sig.getKeyID()));
    } else {
      problems.addAll(deferredProblems);
    }
  }
}
