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

import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.inject.Inject;
import com.google.inject.Singleton;

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
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;

/**
 * Pre-receive hook to validate signed pushes.
 * <p>
 * If configured, prior to processing any push using {@link ReceiveCommits},
 * requires that any push certificate present must be valid.
 */
@Singleton
public class SignedPushPreReceiveHook implements PreReceiveHook {
  private static final Logger log =
      LoggerFactory.getLogger(SignedPushPreReceiveHook.class);

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;

  @Inject
  public SignedPushPreReceiveHook(
      GitRepositoryManager repoManager,
      AllUsersName allUsers) {
    this.repoManager = repoManager;
    this.allUsers = allUsers;
  }

  @Override
  public void onPreReceive(ReceivePack rp,
      Collection<ReceiveCommand> commands) {
    try (Writer msgOut = new OutputStreamWriter(rp.getMessageOutputStream())) {
      PushCertificate cert = rp.getPushCertificate();
      if (cert == null) {
        return;
      }
      if (cert.getNonceStatus() != NonceStatus.OK) {
        msgOut.write("Invalid nonce\n");
        rejectInvalid(commands);
        return;
      }

      PGPSignature sig = readSignature(cert);
      if (sig == null) {
        msgOut.write("Invalid signature format\n");
        rejectInvalid(commands);
        return;
      }

      try (Repository repo = repoManager.openRepository(allUsers);
          PublicKeyStore store = new PublicKeyStore(repo)) {
        String err = verifySignature(sig, cert, readKeys(sig.getKeyID()));
        if (err != null) {
          msgOut.write(err);
          rejectInvalid(commands);
        }
      }
    } catch (PGPException | IOException e) {
      log.error("Error verifying push certificate", e);
      reject(commands, "push cert error");
    }
  }

  private String verifySignature(PGPSignature sig, PushCertificate cert,
      PGPPublicKeyRingCollection keys) {
    StringBuilder deferredProblems = new StringBuilder();
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
          deferredProblems.append(
              "Signature not valid with public key: " + keyToString(k));
          continue;
        }
        PublicKeyVerifier.Result result = PublicKeyVerifier.verify(
            k, sig.getKeyID(), cert.getPusherIdent().getUserId());
        if (result.isValid()) {
          return null;
        }
        StringBuilder err = new StringBuilder("Invalid public key (")
            .append(keyToString(k))
            .append(":\n");
        for (String problem : result.getProblems()) {
          err.append("  ").append(problem).append('\n');
        }
        return err.toString();
      } catch (PGPException e) {
        String msg = "Error verifying signature with public key (" +
            keyToString(k) + "): " + e.getMessage();
        deferredProblems.append(msg);
        log.warn(msg, e);
      }
    }
    if (!anyKeys) {
      return "No public keys found for Key ID " + keyIdToString(sig.getKeyID())
        + "\n";
    }
    return deferredProblems.toString();
  }

  private PGPPublicKeyRingCollection readKeys(long keyId)
      throws PGPException, IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        PublicKeyStore store = new PublicKeyStore(repo)) {
      return store.get(keyId);
    }
  }

  private PGPSignature readSignature(PushCertificate cert) throws IOException {
    ArmoredInputStream in = new ArmoredInputStream(
        new ByteArrayInputStream(Constants.encode(cert.getSignature())));
    PGPObjectFactory factory = new BcPGPObjectFactory(in);
    PGPSignature sig = null;

    Object obj;
    while ((obj = factory.nextObject()) != null) {
      if (!(obj instanceof PGPSignatureList)) {
        log.error("Unexpected packet in push cert: {}",
            obj.getClass().getSimpleName());
        return null;
      }
      if (sig != null) {
        log.error("Multiple signature packets found in push cert");
        return null;
      }
      PGPSignatureList sigs = (PGPSignatureList) obj;
      if (sigs.size() != 1) {
        log.error("Expected 1 signature in push cert, found {}", sigs.size());
        return null;
      }
      sig = sigs.get(0);
    }
    return sig;
  }

  private static void reject(Collection<ReceiveCommand> commands,
      String reason) {
    for (ReceiveCommand cmd : commands) {
      if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
        cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, reason);
      }
    }
  }

  private static void rejectInvalid(Collection<ReceiveCommand> commands) {
    reject(commands, "invalid push cert");
  }
}
