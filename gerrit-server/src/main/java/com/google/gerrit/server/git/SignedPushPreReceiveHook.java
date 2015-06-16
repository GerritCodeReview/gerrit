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

package com.google.gerrit.server.git;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;
import org.eclipse.jgit.transport.PushCertificateIdent;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;

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
        rejectInvalid(commands);
        return;
      }
      verifySignature(cert, commands, msgOut);
    } catch (IOException e) {
      log.error("Error verifying push certificate", e);
      reject(commands, "push cert error");
    }
  }

  private void verifySignature(PushCertificate cert,
      Collection<ReceiveCommand> commands, Writer msgOut) throws IOException {
    PGPSignature sig = readSignature(cert);
    if (sig == null) {
      msgOut.write("Invalid signature format\n");
      rejectInvalid(commands);
      return;
    }
    PGPPublicKey key = readPublicKey(sig.getKeyID(), cert.getPusherIdent());
    if (key == null) {
      msgOut.write("No valid public key found for ID "
          + keyIdToString(sig.getKeyID()) + "\n");
      rejectInvalid(commands);
      return;
    }
    try {
      sig.init(new BcPGPContentVerifierBuilderProvider(), key);
      sig.verify();
      return;
    } catch (PGPException e) {
      msgOut.write(
          "Push certificate verification failed: " + e.getMessage() + "\n");
      rejectInvalid(commands);
      return;
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

  private PGPPublicKey readPublicKey(long keyId,
      PushCertificateIdent expectedIdent) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.getRefDatabase().exactRef(RefNames.REFS_PUBLIC_KEYS);
      if (ref == null) {
        return null;
      }
      NoteMap notes = NoteMap.read(
          rw.getObjectReader(), rw.parseCommit(ref.getObjectId()));
      Note note = notes.getNote(keyObjectId(keyId));
      if (note == null) {
        return null;
      }

      try (InputStream objIn =
              rw.getObjectReader().open(note.getData(), OBJ_BLOB).openStream();
          ArmoredInputStream in = new ArmoredInputStream(objIn)) {
        PGPObjectFactory factory = new BcPGPObjectFactory(in);
        PGPPublicKey matched = null;
        Object obj;
        while ((obj = factory.nextObject()) != null) {
          if (!(obj instanceof PGPPublicKeyRing)) {
            // TODO(dborowitz): Support assertions signed by a trusted key.
            log.info("Ignoring {} packet in {}",
                obj.getClass().getSimpleName(), note.getName());
            continue;
          }
          PGPPublicKeyRing keyRing = (PGPPublicKeyRing) obj;
          PGPPublicKey key = keyRing.getPublicKey(keyId);
          if (key == null) {
            log.warn("Public key ring in {} does not contain key ID {}",
                note.getName(), keyObjectId(keyId));
            continue;
          }
          if (matched != null) {
            // TODO(dborowitz): Try all keys.
            log.warn("Ignoring key with duplicate ID: {}", toString(key));
            continue;
          }
          if (!verifyPublicKey(key, expectedIdent)) {
            continue;
          }
          matched = key;
        }
        return matched;
      }
    }
  }

  private boolean verifyPublicKey(PGPPublicKey key,
      PushCertificateIdent ident) {
    @SuppressWarnings("unchecked")
    Iterator<PGPSignature> sigs = key.getSignaturesForID(ident.getUserId());
    if (sigs != null) {
      while (sigs.hasNext()) {
        PGPSignature sig = sigs.next();
        if (sig.getSignatureType() == PGPSignature.DEFAULT_CERTIFICATION
            && sig.getKeyID() == key.getKeyID()) {
          try {
            sig.init(new BcPGPContentVerifierBuilderProvider(), key);
            sig.verify();
            return true;
          } catch (PGPException e) {
            log.warn("Failed signature verification for public key", e);
          }
        }
      }
    }
    log.warn(
        "Key does not contain default certification for pusher identity {}: {}",
        ident.getUserId(), toString(key));
    return false;
  }

  static ObjectId keyObjectId(long keyId) {
    // Right-pad key IDs in network byte order to ObjectId length. This allows
    // us to reuse the fanout code in NoteMap for free. (If we ever fix the
    // fanout code to work with variable-length byte strings, we will need to
    // fall back to this key format during a transition period.)
    ByteBuffer buf = ByteBuffer.wrap(new byte[Constants.OBJECT_ID_LENGTH]);
    buf.putLong(keyId);
    return ObjectId.fromRaw(buf.array());
  }

  static String toString(PGPPublicKey key) {
    StringBuilder sb = new StringBuilder()
        .append(keyIdToString(key.getKeyID())).append(' ');
    @SuppressWarnings("unchecked")
    Iterator<String> it = key.getUserIDs();
    if (it.hasNext()) {
      sb.append(it.next());
    }
    sb.append(" (");
    ByteBuffer buf = ByteBuffer.wrap(key.getFingerprint());
    // Match fingerprint format from gpg --fingerprint.
    for (int i = 0; i < 10; i++) {
      int s = 0xffff & buf.getShort();
      sb.append(Integer.toHexString(s).toUpperCase());
      switch (i) {
        default:
          sb.append(' ');
          break;
        case 4:
          sb.append("  ");
          break;
        case 9:
          break;
      }
    }
    return sb.append(')').toString();
  }

  private static void reject(Collection<ReceiveCommand> commands,
      String reason) {
    for (ReceiveCommand cmd : commands) {
      if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
        cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, reason);
      }
    }
  }

  static String keyIdToString(long keyId) {
    // Match key ID format from gpg --list-keys.
    return Integer.toHexString((int) keyId).toUpperCase();
  }

  private static void rejectInvalid(Collection<ReceiveCommand> commands) {
    reject(commands, "invalid push cert");
  }
}
