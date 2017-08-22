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

import com.google.common.base.Joiner;
import com.google.gerrit.extensions.common.GpgKeyInfo.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checker for push certificates. */
public abstract class PushCertificateChecker {
  private static final Logger log = LoggerFactory.getLogger(PushCertificateChecker.class);

  public static class Result {
    private final PGPPublicKey key;
    private final CheckResult checkResult;

    private Result(PGPPublicKey key, CheckResult checkResult) {
      this.key = key;
      this.checkResult = checkResult;
    }

    public PGPPublicKey getPublicKey() {
      return key;
    }

    public CheckResult getCheckResult() {
      return checkResult;
    }
  }

  private final PublicKeyChecker publicKeyChecker;

  private boolean checkNonce;

  protected PushCertificateChecker(PublicKeyChecker publicKeyChecker) {
    this.publicKeyChecker = publicKeyChecker;
    checkNonce = true;
  }

  /** Set whether to check the status of the nonce; defaults to true. */
  public PushCertificateChecker setCheckNonce(boolean checkNonce) {
    this.checkNonce = checkNonce;
    return this;
  }

  /**
   * Check a push certificate.
   *
   * @return result of the check.
   */
  public final Result check(PushCertificate cert) {
    if (checkNonce && cert.getNonceStatus() != NonceStatus.OK) {
      return new Result(null, CheckResult.bad("Invalid nonce"));
    }
    List<CheckResult> results = new ArrayList<>(2);
    Result sigResult = null;
    try {
      PGPSignature sig = readSignature(cert);
      if (sig != null) {
        @SuppressWarnings("resource")
        Repository repo = getRepository();
        try (PublicKeyStore store = new PublicKeyStore(repo)) {
          sigResult = checkSignature(sig, cert, store);
          results.add(checkCustom(repo));
        } finally {
          if (shouldClose(repo)) {
            repo.close();
          }
        }
      } else {
        results.add(CheckResult.bad("Invalid signature format"));
      }
    } catch (PGPException | IOException e) {
      String msg = "Internal error checking push certificate";
      log.error(msg, e);
      results.add(CheckResult.bad(msg));
    }

    return combine(sigResult, results);
  }

  private static Result combine(Result sigResult, List<CheckResult> results) {
    // Combine results:
    //  - If any input result is BAD, the final result is bad.
    //  - If sigResult is TRUSTED and no other result is BAD, the final result
    //    is TRUSTED.
    //  - Otherwise, the result is OK.
    List<String> problems = new ArrayList<>();
    boolean bad = false;
    for (CheckResult result : results) {
      problems.addAll(result.getProblems());
      bad |= result.getStatus() == BAD;
    }
    Status status = bad ? BAD : OK;

    PGPPublicKey key;
    if (sigResult != null) {
      key = sigResult.getPublicKey();
      CheckResult cr = sigResult.getCheckResult();
      problems.addAll(cr.getProblems());
      if (cr.getStatus() == BAD) {
        status = BAD;
      } else if (!bad && cr.getStatus() == TRUSTED) {
        status = TRUSTED;
      }
    } else {
      key = null;
    }
    return new Result(key, CheckResult.create(status, problems));
  }

  /**
   * Get the repository that this checker should operate on.
   *
   * <p>This method is called once per call to {@link #check(PushCertificate)}.
   *
   * @return the repository.
   * @throws IOException if an error occurred reading the repository.
   */
  protected abstract Repository getRepository() throws IOException;

  /**
   * @param repo a repository previously returned by {@link #getRepository()}.
   * @return whether this repository should be closed before returning from {@link
   *     #check(PushCertificate)}.
   */
  protected abstract boolean shouldClose(Repository repo);

  /**
   * Perform custom checks.
   *
   * <p>Default implementation reports no problems, but may be overridden by subclasses.
   *
   * @param repo a repository previously returned by {@link #getRepository()}.
   * @return the result of the custom check.
   */
  protected CheckResult checkCustom(Repository repo) {
    return CheckResult.ok();
  }

  private PGPSignature readSignature(PushCertificate cert) throws IOException {
    ArmoredInputStream in =
        new ArmoredInputStream(new ByteArrayInputStream(Constants.encode(cert.getSignature())));
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

  private Result checkSignature(PGPSignature sig, PushCertificate cert, PublicKeyStore store)
      throws PGPException, IOException {
    PGPPublicKeyRingCollection keys = store.get(sig.getKeyID());
    if (!keys.getKeyRings().hasNext()) {
      return new Result(
          null,
          CheckResult.bad("No public keys found for key ID " + keyIdToString(sig.getKeyID())));
    }
    PGPPublicKey signer = PublicKeyStore.getSigner(keys, sig, Constants.encode(cert.toText()));
    if (signer == null) {
      return new Result(
          null, CheckResult.bad("Signature by " + keyIdToString(sig.getKeyID()) + " is not valid"));
    }
    CheckResult result =
        publicKeyChecker.setStore(store).setEffectiveTime(sig.getCreationTime()).check(signer);
    if (!result.getProblems().isEmpty()) {
      StringBuilder err =
          new StringBuilder("Invalid public key ")
              .append(keyToString(signer))
              .append(":\n  ")
              .append(Joiner.on("\n  ").join(result.getProblems()));
      return new Result(signer, CheckResult.create(result.getStatus(), err.toString()));
    }
    return new Result(signer, result);
  }
}
