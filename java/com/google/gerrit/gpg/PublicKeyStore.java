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

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.ObjectIds;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
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
import org.eclipse.jgit.util.NB;

/**
 * Store of GPG public keys in git notes.
 *
 * <p>Keys are stored in filenames based on their hex key ID, padded out to 40 characters to match
 * the length of a SHA-1. (This is to easily reuse existing fanout code in {@link NoteMap}, and may
 * be changed later after an appropriate transition.)
 *
 * <p>The contents of each file is an ASCII armored stream containing one or more public key rings
 * matching the ID. Multiple keys are supported because forging a key ID is possible, but such a key
 * cannot be used to verify signatures produced with the correct key.
 *
 * <p>Subkeys are mapped to the master GPG key in the same NoteMap.
 *
 * <p>No additional checks are performed on the key after reading; callers should only trust keys
 * after checking with a {@link PublicKeyChecker}.
 */
public class PublicKeyStore implements AutoCloseable {
  /** Ref where GPG public keys are stored. */
  public static final String REFS_GPG_KEYS = "refs/meta/gpg-keys";

  /**
   * Choose the public key that produced a signature.
   *
   * <p>
   *
   * @param keyRings candidate keys.
   * @param sig signature object.
   * @param data signed payload.
   * @return the key chosen from {@code keyRings} that was able to verify the signature, or {@code
   *     null} if none was found.
   * @throws PGPException if an error occurred verifying the signature.
   */
  public static PGPPublicKey getSigner(
      Iterable<PGPPublicKeyRing> keyRings, PGPSignature sig, byte[] data) throws PGPException {
    for (PGPPublicKeyRing kr : keyRings) {
      // Possibly return a signing subkey in case it differs from the master public key
      PGPPublicKey k = kr.getPublicKey(sig.getKeyID());
      if (k == null) {
        throw new IllegalStateException(
            "No public key found for ID: " + keyIdToString(sig.getKeyID()));
      }
      sig.init(new BcPGPContentVerifierBuilderProvider(), k);
      sig.update(data);
      if (sig.verify()) {
        // If the signature was made using a subkey, return the main public key.
        // This enables further validity checks, like user ID checks, that can only
        // be performed using the master public key.
        return kr.getPublicKey();
      }
    }
    return null;
  }

  /**
   * Choose the public key that produced a certification.
   *
   * <p>
   *
   * @param keyRings candidate keys.
   * @param sig signature object.
   * @param userId user ID being certified.
   * @param key key being certified.
   * @return the key chosen from {@code keyRings} that was able to verify the certification, or
   *     {@code null} if none was found.
   * @throws PGPException if an error occurred verifying the certification.
   */
  public static PGPPublicKey getSigner(
      Iterable<PGPPublicKeyRing> keyRings, PGPSignature sig, String userId, PGPPublicKey key)
      throws PGPException {
    for (PGPPublicKeyRing kr : keyRings) {
      PGPPublicKey k = kr.getPublicKey();
      sig.init(new BcPGPContentVerifierBuilderProvider(), k);
      if (sig.verifyCertification(userId, key)) {
        return k;
      }
    }
    return null;
  }

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
    reset();
  }

  private void reset() {
    if (reader != null) {
      reader.close();
      reader = null;
      notes = null;
    }
  }

  private void load() throws IOException {
    reset();
    reader = repo.newObjectReader();

    Ref ref = repo.getRefDatabase().exactRef(REFS_GPG_KEYS);
    if (ref == null) {
      return;
    }
    try (RevWalk rw = new RevWalk(reader)) {
      tip = rw.parseCommit(ref.getObjectId());
      notes = NoteMap.read(reader, tip);
    }
  }

  /**
   * Read public keys with the given key ID.
   *
   * <p>Keys should not be trusted unless checked with {@link PublicKeyChecker}.
   *
   * <p>Multiple calls to this method use the same state of the key ref; to reread the ref, call
   * {@link #close()} first.
   *
   * @param keyId key ID.
   * @return any keys found that could be successfully parsed.
   * @throws PGPException if an error occurred parsing the key data.
   * @throws IOException if an error occurred reading the repository data.
   */
  public PGPPublicKeyRingCollection get(long keyId) throws PGPException, IOException {
    return new PGPPublicKeyRingCollection(get(keyId, null));
  }

  /**
   * Read public key with the given fingerprint.
   *
   * <p>Keys should not be trusted unless checked with {@link PublicKeyChecker}.
   *
   * <p>Multiple calls to this method use the same state of the key ref; to reread the ref, call
   * {@link #close()} first.
   *
   * @param fingerprint key fingerprint.
   * @return the key if found, or {@code null}.
   * @throws PGPException if an error occurred parsing the key data.
   * @throws IOException if an error occurred reading the repository data.
   */
  public PGPPublicKeyRing get(byte[] fingerprint) throws PGPException, IOException {
    List<PGPPublicKeyRing> keyRings = get(Fingerprint.getId(fingerprint), fingerprint);
    return !keyRings.isEmpty() ? keyRings.get(0) : null;
  }

  private List<PGPPublicKeyRing> get(long keyId, byte[] fp) throws IOException {
    if (reader == null) {
      load();
    }
    if (notes == null) {
      return Collections.emptyList();
    }

    return get(keyObjectId(keyId), fp);
  }

  private List<PGPPublicKeyRing> get(ObjectId keyObjectId, byte[] fp) throws IOException {
    Note note = notes.getNote(keyObjectId);
    if (note == null) {
      return Collections.emptyList();
    }

    return readKeysFromNote(note, fp);
  }

  private List<PGPPublicKeyRing> readKeysFromNote(Note note, byte[] fp)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    boolean foundAtLeastOneKey = false;
    List<PGPPublicKeyRing> keys = new ArrayList<>();
    ObjectId data = note.getData();
    try (InputStream stream = reader.open(data, OBJ_BLOB).openStream()) {
      byte[] bytes = ByteStreams.toByteArray(stream);
      InputStream in = new ByteArrayInputStream(bytes);
      while (true) {
        @SuppressWarnings("unchecked")
        Iterator<Object> it = new BcPGPObjectFactory(new ArmoredInputStream(in)).iterator();
        if (!it.hasNext()) {
          break;
        }
        foundAtLeastOneKey = true;
        Object obj = it.next();
        if (obj instanceof PGPPublicKeyRing) {
          PGPPublicKeyRing kr = (PGPPublicKeyRing) obj;
          if (fp == null || Arrays.equals(fp, kr.getPublicKey().getFingerprint())) {
            keys.add(kr);
          }
        }
        checkState(!it.hasNext(), "expected one PGP object per ArmoredInputStream");
      }

      if (foundAtLeastOneKey) {
        return keys;
      }

      // Subkey handling
      String id = new String(bytes, UTF_8);
      Preconditions.checkArgument(ObjectId.isId(id), "Not valid SHA1: " + id);
      return get(ObjectId.fromString(id), fp);
    }
  }

  public void rebuildSubkeyMasterKeyMap()
      throws MissingObjectException, IncorrectObjectTypeException, IOException, PGPException {
    if (reader == null) {
      load();
    }
    if (notes != null) {
      try (ObjectInserter ins = repo.newObjectInserter()) {
        for (Note note : notes) {
          for (PGPPublicKeyRing keyRing :
              new PGPPublicKeyRingCollection(readKeysFromNote(note, null))) {
            long masterKeyId = keyRing.getPublicKey().getKeyID();
            ObjectId masterKeyObjectId = keyObjectId(masterKeyId);
            saveSubkeyMapping(ins, keyRing, masterKeyId, masterKeyObjectId);
          }
        }
      }
    }
  }

  /**
   * Add a public key to the store.
   *
   * <p>Multiple calls may be made to buffer keys in memory, and they are not saved until {@link
   * #save(CommitBuilder)} is called.
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
      throw new IllegalArgumentException("Exactly 1 master key is required, found " + numMaster);
    }
    Fingerprint fp = new Fingerprint(keyRing.getPublicKey().getFingerprint());
    toAdd.put(fp, keyRing);
    toRemove.remove(fp);
  }

  /**
   * Remove a public key from the store.
   *
   * <p>Multiple calls may be made to buffer deletes in memory, and they are not saved until {@link
   * #save(CommitBuilder)} is called.
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
   *
   * <p>One commit is created and the ref updated. The pending list is cleared if and only if the
   * ref update succeeds, which allows for easy retries in case of lock failure.
   *
   * @param cb commit builder with at least author and identity populated; tree and parent are
   *     ignored.
   * @return result of the ref update.
   */
  public RefUpdate.Result save(CommitBuilder cb) throws PGPException, IOException {
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
    try (ObjectInserter ins = repo.newObjectInserter()) {
      for (PGPPublicKeyRing keyRing : toAdd.values()) {
        saveToNotes(ins, keyRing);
      }
      for (Fingerprint fp : toRemove) {
        deleteFromNotes(ins, fp);
      }
      cb.setTreeId(notes.writeTree(ins));
      if (cb.getTreeId().equals(tip != null ? tip.getTree() : EMPTY_TREE_ID)) {
        return RefUpdate.Result.NO_CHANGE;
      }

      if (tip != null) {
        cb.setParentId(tip);
      }
      if (cb.getMessage() == null) {
        int n = toAdd.size() + toRemove.size();
        cb.setMessage(String.format("Update %d public key%s", n, n != 1 ? "s" : ""));
      }
      newTip = ins.insert(cb);
      ins.flush();
    }

    RefUpdate ru = repo.updateRef(PublicKeyStore.REFS_GPG_KEYS);
    ru.setExpectedOldObjectId(tip);
    ru.setNewObjectId(newTip);
    ru.setRefLogIdent(cb.getCommitter());
    ru.setRefLogMessage("Store public keys", true);
    RefUpdate.Result result = ru.update();
    reset();
    switch (result) {
      case FAST_FORWARD:
      case NEW:
      case NO_CHANGE:
        toAdd.clear();
        toRemove.clear();
        break;
      case LOCK_FAILURE:
        throw new LockFailureException("Failed to store public keys", ru);
      case FORCED:
      case IO_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        break;
    }
    return result;
  }

  private void saveToNotes(ObjectInserter ins, PGPPublicKeyRing keyRing)
      throws PGPException, IOException {
    long masterKeyId = keyRing.getPublicKey().getKeyID();
    PGPPublicKeyRingCollection existing = get(masterKeyId);
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

    ObjectId masterKeyObjectId = keyObjectId(masterKeyId);
    notes.set(masterKeyObjectId, ins.insert(OBJ_BLOB, keysToArmored(toWrite)));

    saveSubkeyMapping(ins, keyRing, masterKeyId, masterKeyObjectId);
  }

  private void saveSubkeyMapping(
      ObjectInserter ins, PGPPublicKeyRing keyRing, long masterKeyId, ObjectId masterKeyObjectId)
      throws IOException {
    // Subkey handling
    byte[] masterKeyBytes = masterKeyObjectId.name().getBytes(UTF_8);
    ObjectId masterKeyObject = null;
    for (PGPPublicKey key : keyRing) {
      long subKeyId = key.getKeyID();
      // Skip master public key
      if (masterKeyId == subKeyId) {
        continue;
      }

      // Insert master key object only once for all subkeys
      if (masterKeyObject == null) {
        masterKeyObject = ins.insert(OBJ_BLOB, masterKeyBytes);
      }

      ObjectId subkeyObjectId = keyObjectId(subKeyId);
      Preconditions.checkArgument(
          notes.get(subkeyObjectId) == null || notes.get(subkeyObjectId).equals(masterKeyObject),
          "Master key differs for subkey: " + subkeyObjectId.name());
      notes.set(subkeyObjectId, masterKeyObject);
    }
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
    }

    ObjectId keyObjectId = keyObjectId(keyId);
    if (!toWrite.isEmpty()) {
      notes.set(keyObjectId, ins.insert(OBJ_BLOB, keysToArmored(toWrite)));
    } else {
      PGPPublicKeyRing keyRing = get(fp.get());

      for (PGPPublicKey key : keyRing) {
        long subKeyId = key.getKeyID();
        // Skip master public key
        if (keyId == subKeyId) {
          continue;
        }
        notes.remove(keyObjectId(subKeyId));
      }

      notes.remove(keyObjectId);
    }
  }

  private static boolean sameKey(PGPPublicKeyRing kr1, PGPPublicKeyRing kr2) {
    return Arrays.equals(kr1.getPublicKey().getFingerprint(), kr2.getPublicKey().getFingerprint());
  }

  private static byte[] keysToArmored(List<PGPPublicKeyRing> keys) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(4096 * keys.size());
    for (PGPPublicKeyRing kr : keys) {
      try (ArmoredOutputStream aout = new ArmoredOutputStream(out)) {
        kr.encode(aout);
      }
    }
    return out.toByteArray();
  }

  public static String keyToString(PGPPublicKey key) {
    Iterator<String> it = key.getUserIDs();
    return String.format(
        "%s %s(%s)",
        keyIdToString(key.getKeyID()),
        it.hasNext() ? it.next() + " " : "",
        Fingerprint.toString(key.getFingerprint()));
  }

  public static String keyIdToString(long keyId) {
    // Match key ID format from gpg --list-keys.
    return String.format("%08X", (int) keyId);
  }

  static ObjectId keyObjectId(long keyId) {
    byte[] buf = new byte[ObjectIds.LEN];
    NB.encodeInt64(buf, 0, keyId);
    return ObjectId.fromRaw(buf);
  }
}
