package com.google.gerrit.gpg;

import static com.google.common.base.Preconditions.checkArgument;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Deletes a public GPG primary key from the refs/meta/gpg-keys ref by rewriting the commit history.
 */
public class DeleteGpgKeyRewriter {

  private Fingerprint fingerprintToDelete;

  /** @param fingerprintToDelete the fingerprint of the key to delete. */
  public DeleteGpgKeyRewriter(Fingerprint fingerprintToDelete) {
    this.fingerprintToDelete = fingerprintToDelete;
  }

  /**
   * Rewrites the commit history.
   *
   * @param inserter a {@code ObjectInserter} instance.
   * @param reader a {@code ObjectReader} instance.
   * @param revWalk a {@code RevWalk} instance.
   * @param currentTip the {@code ObjectId} of the ref's tip commit.
   * @return the {@code ObjectId} of the ref's new tip commit.
   */
  public ObjectId rewriteHistory(
      ObjectInserter inserter, ObjectReader reader, RevWalk revWalk, RevCommit currentTip)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    checkArgument(!currentTip.equals(ObjectId.zeroId()));

    // Walk from the first commit of the branch.
    revWalk.reset();
    revWalk.markStart(revWalk.parseCommit(currentTip));
    revWalk.sort(RevSort.REVERSE);

    RevCommit newTipCommit = null;
    boolean rewrite = false;
    RevCommit originalCommit;
    while ((originalCommit = revWalk.next()) != null) {
      NoteMap notes = NoteMap.read(reader, originalCommit);
      ObjectId keyObjectId = PublicKeyStore.keyObjectId(fingerprintToDelete.getId());
      List<PGPPublicKeyRing> keys =
          PublicKeyStore.get(reader, notes, keyObjectId, fingerprintToDelete.get());

      if (!rewrite && containsKey(keys, fingerprintToDelete)) {
        rewrite = true;
      }

      if (!rewrite) {
        newTipCommit = originalCommit;
        continue;
      }

      deleteFromNotes(reader, inserter, notes, keys, fingerprintToDelete);
      AnyObjectId rewrittenCommit = rewriteCommit(originalCommit, newTipCommit, inserter, notes);
      newTipCommit = revWalk.parseCommit(rewrittenCommit);
    }

    return newTipCommit;
  }

  private AnyObjectId rewriteCommit(
      RevCommit originalCommit, RevCommit parentCommit, ObjectInserter inserter, NoteMap notes)
      throws IOException {
    CommitBuilder cb = new CommitBuilder();
    if (parentCommit != null) {
      cb.setParentId(parentCommit);
    }
    cb.setTreeId(notes.writeTree(inserter));
    cb.setMessage(originalCommit.getFullMessage());
    cb.setCommitter(originalCommit.getCommitterIdent());
    cb.setAuthor(originalCommit.getAuthorIdent());
    cb.setEncoding(originalCommit.getEncoding());
    return inserter.insert(cb);
  }

  private void deleteFromNotes(
      ObjectReader reader,
      ObjectInserter inserter,
      NoteMap notes,
      List<PGPPublicKeyRing> keys,
      Fingerprint fingerprintToDelete)
      throws IOException {
    long keyId = fingerprintToDelete.getId();
    List<PGPPublicKeyRing> existing = keys;
    List<PGPPublicKeyRing> toWrite = new ArrayList<>(existing.size());
    for (PGPPublicKeyRing kr : existing) {
      if (!fingerprintToDelete.equalsBytes(kr.getPublicKey().getFingerprint())) {
        toWrite.add(kr);
      }
    }
    if (toWrite.size() == existing.size()) {
      return;
    }

    ObjectId keyObjectId = PublicKeyStore.keyObjectId(keyId);
    if (!toWrite.isEmpty()) {
      notes.set(keyObjectId, inserter.insert(OBJ_BLOB, PublicKeyStore.keysToArmored(toWrite)));
    } else {
      List<PGPPublicKeyRing> keyRings =
          PublicKeyStore.get(reader, notes, keyObjectId, fingerprintToDelete.get());
      PGPPublicKeyRing keyRing = !keyRings.isEmpty() ? keyRings.get(0) : null;

      for (PGPPublicKey key : keyRing) {
        long subKeyId = key.getKeyID();
        // Skip master public key
        if (keyId == subKeyId) {
          continue;
        }
        notes.remove(PublicKeyStore.keyObjectId(subKeyId));
      }

      notes.remove(keyObjectId);
    }
  }

  private boolean containsKey(List<PGPPublicKeyRing> existing, Fingerprint fingerprintToDelete) {
    List<PGPPublicKeyRing> toWrite = new ArrayList<>(existing.size());
    for (PGPPublicKeyRing kr : existing) {
      if (!fingerprintToDelete.equalsBytes(kr.getPublicKey().getFingerprint())) {
        toWrite.add(kr);
      }
    }
    return toWrite.size() != existing.size();
  }
}
