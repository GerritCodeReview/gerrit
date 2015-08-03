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

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.gerrit.reviewdb.client.RefNames;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Store of GPG public keys in git notes.
 * <p>
 * Keys are stored in filenames based on their hex key ID, padded out to 40
 * characters to match the length of a SHA-1. (This is to easily reuse existing
 * fanout code in {@link NoteMap}, and may be changed later after an appropriate
 * transition.)
 * <p>
 * The contents of each file is an ASCII armored stream containing one or more
 * public key rings matching the ID. Multiple keys are supported because forging
 * a key ID is possible, but such a key cannot be used to verify signatures
 * produced with the correct key.
 * <p>
 * No additional checks are performed on the key after reading; callers should
 * only trust keys after checking with a {@link PublicKeyChecker}.
 */
public class PublicKeyStore implements AutoCloseable {
  private static final ObjectId EMPTY_TREE =
      ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904");

  private final Repository repo;
  private ObjectReader reader;
  private RevCommit tip;
  private NoteMap notes;
  private Map<Fingerprint, PGPPublicKeyRing> toAdd;
  private Set<Fingerprint> toRemove;

  /** @param repo repository to read keys from. */
  public PublicKeyStore(Repository repo) {
    this.repo = repo;
    toAdd = new HashMap<>();
    toRemove = new HashSet<>();
  }

  @Override
  public void close() {
    if (reader != null) {
      reader.close();
      reader = null;
      notes = null;
    }
  }

  private void load() throws IOException {
    close();
    reader = repo.newObjectReader();

    Ref ref = repo.getRefDatabase().exactRef(RefNames.REFS_GPG_KEYS);
    if (ref == null) {
      return;
    }
    try (RevWalk rw = new RevWalk(reader)) {
      tip = rw.parseCommit(ref.getObjectId().copy());
      notes = NoteMap.read(reader, tip);
    }
  }

  /**
   * Read public keys with the given key ID.
   * <p>
   * Keys should not be trusted unless checked with {@link PublicKeyChecker}.
   * <p>
   * Multiple calls to this method use the same state of the key ref; to reread
   * the ref, call {@link #close()} first.
   *
   * @param keyId key ID.
   * @return any keys found that could be successfully parsed.
   * @throws PGPException if an error occurred parsing the key data.
   * @throws IOException if an error occurred reading the repository data.
   */
  public PGPPublicKeyRingCollection get(long keyId)
      throws PGPException, IOException {
    if (reader == null) {
      load();
    }
    if (notes == null) {
      return empty();
    }
    Note note = notes.getNote(keyObjectId(keyId));
    if (note == null) {
      return empty();
    }

    List<PGPPublicKeyRing> keys = new ArrayList<>();
    try (InputStream in = reader.open(note.getData(), OBJ_BLOB).openStream()) {
      while (true) {
        @SuppressWarnings("unchecked")
        Iterator<Object> it =
            new BcPGPObjectFactory(new ArmoredInputStream(in)).iterator();
        if (!it.hasNext()) {
          break;
        }
        Object obj = it.next();
        if (obj instanceof PGPPublicKeyRing) {
          keys.add((PGPPublicKeyRing) obj);
        }
        checkState(!it.hasNext(),
            "expected one PGP object per ArmoredInputStream");
      }
      return new PGPPublicKeyRingCollection(keys);
    }
  }

  /**
   * Add a public key to the store.
   * <p>
   * Multiple calls may be made to buffer keys in memory, and they are not saved
   * until {@link #save(CommitBuilder)} is called.
   *
   * @param keyRing a key ring containing exactly one public master key.
   */
  public void add(PGPPublicKeyRing keyRing) {
    int numMaster = 0;
    for (PGPPublicKey key : keyRing) {
      if (key.isMasterKey()) {
        numMaster++;
      }
    }
    // We could have an additional sanity check to ensure all subkeys belong to
    // this master key, but that requires doing actual signature verification
    // here. The alternative is insane but harmless.
    if (numMaster != 1) {
      throw new IllegalArgumentException(
          "Exactly 1 master key is required, found " + numMaster);
    }
    Fingerprint fp = new Fingerprint(keyRing.getPublicKey().getFingerprint());
    toAdd.put(fp, keyRing);
    toRemove.remove(fp);
  }

  /**
   * Remove a public key from the store.
   * <p>
   * Multiple calls may be made to buffer deletes in memory, and they are not
   * saved until {@link #save(CommitBuilder)} is called.
   *
   * @param fingerprint the fingerprint of the key to remove.
   */
  public void remove(byte[] fingerprint) {
    Fingerprint fp = new Fingerprint(fingerprint);
    toAdd.remove(fp);
    toRemove.add(fp);
  }

  /**
   * Save pending keys to the store.
   * <p>
   * One commit is created and the ref updated. The pending list is cleared if
   * and only if the ref update fails, which allows for easy retries in case of
   * lock failure.
   *
   * @param cb commit builder with at least author and identity populated; tree
   *     and parent are ignored.
   * @return result of the ref update.
   */
  public RefUpdate.Result save(CommitBuilder cb)
      throws PGPException, IOException {
    if (toAdd.isEmpty() && toRemove.isEmpty()) {
      return RefUpdate.Result.NO_CHANGE;
    }
    if (reader == null) {
      load();
    }
    if (notes == null) {
      notes = NoteMap.newEmptyMap();
    }
    ObjectId newTip;
    try (ObjectReader reader = repo.newObjectReader();
        ObjectInserter ins = repo.newObjectInserter()) {
      for (PGPPublicKeyRing keyRing : toAdd.values()) {
        saveToNotes(ins, keyRing);
      }
      for (Fingerprint fp : toRemove) {
        deleteFromNotes(ins, fp);
      }
      cb.setTreeId(notes.writeTree(ins));
      if (cb.getTreeId().equals(
          tip != null ? tip.getTree() : EMPTY_TREE)) {
        return RefUpdate.Result.NO_CHANGE;
      }

      if (tip != null) {
        cb.setParentId(tip);
      }
      if (cb.getMessage() == null) {
        int n = toAdd.size() + toRemove.size();
        cb.setMessage(
            String.format("Update %d public key%s", n, n != 1 ? "s" : ""));
      }
      newTip = ins.insert(cb);
      ins.flush();
    }

    RefUpdate ru = repo.updateRef(RefNames.REFS_GPG_KEYS);
    ru.setExpectedOldObjectId(tip);
    ru.setNewObjectId(newTip);
    ru.setRefLogIdent(cb.getCommitter());
    ru.setRefLogMessage("Store public keys", true);
    RefUpdate.Result result = ru.update();
    close();
    switch (result) {
      case FAST_FORWARD:
      case NEW:
      case NO_CHANGE:
        toAdd.clear();
        toRemove.clear();
        break;
      default:
        break;
    }
    return result;
  }

  private void saveToNotes(ObjectInserter ins, PGPPublicKeyRing keyRing)
      throws PGPException, IOException {
    long keyId = keyRing.getPublicKey().getKeyID();
    PGPPublicKeyRingCollection existing = get(keyId);
    List<PGPPublicKeyRing> toWrite = new ArrayList<>(existing.size() + 1);
    boolean replaced = false;
    for (PGPPublicKeyRing kr : existing) {
      if (sameKey(keyRing, kr)) {
        toWrite.add(keyRing);
        replaced = true;
      } else {
        toWrite.add(kr);
      }
    }
    if (!replaced) {
      toWrite.add(keyRing);
    }
    notes.set(keyObjectId(keyId),
        ins.insert(OBJ_BLOB, keysToArmored(toWrite)));
  }

  private void deleteFromNotes(ObjectInserter ins, Fingerprint fp)
      throws PGPException, IOException {
    long keyId = fp.getId();
    PGPPublicKeyRingCollection existing = get(keyId);
    List<PGPPublicKeyRing> toWrite = new ArrayList<>(existing.size());
    for (PGPPublicKeyRing kr : existing) {
      if (!fp.equalsBytes(kr.getPublicKey().getFingerprint())) {
        toWrite.add(kr);
      }
    }
    if (toWrite.size() == existing.size()) {
      return;
    } else if (toWrite.size() > 0) {
      notes.set(keyObjectId(keyId),
          ins.insert(OBJ_BLOB, keysToArmored(toWrite)));
    } else {
      notes.remove(keyObjectId(keyId));
    }
  }

  private static boolean sameKey(PGPPublicKeyRing kr1, PGPPublicKeyRing kr2) {
    return Arrays.equals(kr1.getPublicKey().getFingerprint(),
        kr2.getPublicKey().getFingerprint());
  }

  private static byte[] keysToArmored(List<PGPPublicKeyRing> keys)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(4096 * keys.size());
    for (PGPPublicKeyRing kr : keys) {
      try (ArmoredOutputStream aout = new ArmoredOutputStream(out)) {
        kr.encode(aout);
      }
    }
    return out.toByteArray();
  }

  private static PGPPublicKeyRingCollection empty()
      throws PGPException, IOException {
    return new PGPPublicKeyRingCollection(
        Collections.<PGPPublicKeyRing> emptyList());
  }

  public static String keyToString(PGPPublicKey key) {
    @SuppressWarnings("unchecked")
    Iterator<String> it = key.getUserIDs();
    return String.format(
        "%s %s(%s)",
        keyIdToString(key.getKeyID()),
        it.hasNext() ? it.next() + " " : "",
        fingerprintToString(key.getFingerprint()));
  }

  public static String keyIdToString(long keyId) {
    // Match key ID format from gpg --list-keys.
    return String.format("%08X", (int) keyId);
  }

  public static String fingerprintToString(byte[] fingerprint) {
    if (fingerprint.length != 20) {
      throw new IllegalArgumentException();
    }
    ByteBuffer buf = ByteBuffer.wrap(fingerprint);
    return String.format(
        "%04X %04X %04X %04X %04X  %04X %04X %04X %04X %04X",
        buf.getShort(), buf.getShort(), buf.getShort(), buf.getShort(),
        buf.getShort(), buf.getShort(), buf.getShort(), buf.getShort(),
        buf.getShort(), buf.getShort());
  }

  static ObjectId keyObjectId(long keyId) {
    ByteBuffer buf = ByteBuffer.wrap(new byte[Constants.OBJECT_ID_LENGTH]);
    buf.putLong(keyId);
    return ObjectId.fromRaw(buf.array());
  }
}
