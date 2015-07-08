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
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
  private final Repository repo;
  private ObjectReader reader;
  private NoteMap notes;

  /** @param repo repository to read keys from. */
  public PublicKeyStore(Repository repo) {
    this.repo = repo;
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
      notes = NoteMap.read(reader, rw.parseCommit(ref.getObjectId()));
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

  // TODO(dborowitz): put method.

  private static PGPPublicKeyRingCollection empty()
      throws PGPException, IOException {
    return new PGPPublicKeyRingCollection(
        Collections.<PGPPublicKeyRing> emptyList());
  }

  static String keyToString(PGPPublicKey key) {
    @SuppressWarnings("unchecked")
    Iterator<String> it = key.getUserIDs();
    ByteBuffer buf = ByteBuffer.wrap(key.getFingerprint());
    return String.format(
        "%s %s(%04X %04X %04X %04X %04X  %04X %04X %04X %04X %04X)",
        keyIdToString(key.getKeyID()),
        it.hasNext() ? it.next() + " " : "",
        buf.getShort(), buf.getShort(), buf.getShort(), buf.getShort(),
        buf.getShort(), buf.getShort(), buf.getShort(), buf.getShort(),
        buf.getShort(), buf.getShort());
  }

  static String keyIdToString(long keyId) {
    // Match key ID format from gpg --list-keys.
    return String.format("%08X", (int) keyId);
  }

  static ObjectId keyObjectId(long keyId) {
    ByteBuffer buf = ByteBuffer.wrap(new byte[Constants.OBJECT_ID_LENGTH]);
    buf.putLong(keyId);
    return ObjectId.fromRaw(buf.array());
  }
}
