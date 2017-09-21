// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.gerrit.server.ioutil.BasicSerialization.readBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeBytes;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeCanBeNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

public class PatchList implements Serializable {
  private static final long serialVersionUID = PatchListKey.serialVersionUID;

  private static final Comparator<PatchListEntry> PATCH_CMP =
      new Comparator<PatchListEntry>() {
        @Override
        public int compare(PatchListEntry a, PatchListEntry b) {
          return comparePaths(a.getNewName(), b.getNewName());
        }
      };

  @VisibleForTesting
  static int comparePaths(String a, String b) {
    int m1 = Patch.isMagic(a) ? (a.equals(Patch.MERGE_LIST) ? 2 : 1) : 3;
    int m2 = Patch.isMagic(b) ? (b.equals(Patch.MERGE_LIST) ? 2 : 1) : 3;

    if (m1 != m2) {
      return m1 - m2;
    } else if (m1 < 3) {
      return 0;
    }

    // m1 == m2 == 3: normal names.
    return a.compareTo(b);
  }

  @Nullable private transient ObjectId oldId;
  private transient ObjectId newId;
  private transient boolean isMerge;
  private transient ComparisonType comparisonType;
  private transient int insertions;
  private transient int deletions;
  private transient PatchListEntry[] patches;

  public PatchList(
      @Nullable AnyObjectId oldId,
      AnyObjectId newId,
      boolean isMerge,
      ComparisonType comparisonType,
      PatchListEntry[] patches) {
    this.oldId = oldId != null ? oldId.copy() : null;
    this.newId = newId.copy();
    this.isMerge = isMerge;
    this.comparisonType = comparisonType;

    Arrays.sort(patches, 0, patches.length, PATCH_CMP);

    // Skip magic files
    int i = 0;
    for (; i < patches.length; i++) {
      if (!Patch.isMagic(patches[i].getNewName())) {
        break;
      }
    }
    for (; i < patches.length; i++) {
      insertions += patches[i].getInsertions();
      deletions += patches[i].getDeletions();
    }

    this.patches = patches;
  }

  /** Old side tree or commit; null only if this is a combined diff. */
  @Nullable
  public ObjectId getOldId() {
    return oldId;
  }

  /** New side commit. */
  public ObjectId getNewId() {
    return newId;
  }

  /** Get a sorted, unmodifiable list of all files in this list. */
  public List<PatchListEntry> getPatches() {
    return Collections.unmodifiableList(Arrays.asList(patches));
  }

  /** @return the comparison type */
  public ComparisonType getComparisonType() {
    return comparisonType;
  }

  /** @return total number of new lines added. */
  public int getInsertions() {
    return insertions;
  }

  /** @return total number of lines removed. */
  public int getDeletions() {
    return deletions;
  }

  /**
   * Get a sorted, modifiable list of all files in this list.
   *
   * <p>The returned list items do not populate:
   *
   * <ul>
   *   <li>{@link Patch#getCommentCount()}
   *   <li>{@link Patch#getDraftCount()}
   *   <li>{@link Patch#isReviewedByCurrentUser()}
   * </ul>
   *
   * @param setId the patch set identity these patches belong to. This really should not need to be
   *     specified, but is a current legacy artifact of how the cache is keyed versus how the
   *     database is keyed.
   */
  public List<Patch> toPatchList(PatchSet.Id setId) {
    final ArrayList<Patch> r = new ArrayList<>(patches.length);
    for (PatchListEntry e : patches) {
      r.add(e.toPatch(setId));
    }
    return r;
  }

  /** Find an entry by name, returning an empty entry if not present. */
  public PatchListEntry get(String fileName) {
    final int index = search(fileName);
    return 0 <= index ? patches[index] : PatchListEntry.empty(fileName);
  }

  private int search(String fileName) {
    PatchListEntry want = PatchListEntry.empty(fileName);
    return Arrays.binarySearch(patches, 0, patches.length, want, PATCH_CMP);
  }

  private void writeObject(ObjectOutputStream output) throws IOException {
    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try (DeflaterOutputStream out = new DeflaterOutputStream(buf)) {
      writeCanBeNull(out, oldId);
      writeNotNull(out, newId);
      writeVarInt32(out, isMerge ? 1 : 0);
      comparisonType.writeTo(out);
      writeVarInt32(out, insertions);
      writeVarInt32(out, deletions);
      writeVarInt32(out, patches.length);
      for (PatchListEntry p : patches) {
        p.writeTo(out);
      }
    }
    writeBytes(output, buf.toByteArray());
  }

  private void readObject(ObjectInputStream input) throws IOException {
    final ByteArrayInputStream buf = new ByteArrayInputStream(readBytes(input));
    try (InflaterInputStream in = new InflaterInputStream(buf)) {
      oldId = readCanBeNull(in);
      newId = readNotNull(in);
      isMerge = readVarInt32(in) != 0;
      comparisonType = ComparisonType.readFrom(in);
      insertions = readVarInt32(in);
      deletions = readVarInt32(in);
      final int cnt = readVarInt32(in);
      final PatchListEntry[] all = new PatchListEntry[cnt];
      for (int i = 0; i < all.length; i++) {
        all[i] = PatchListEntry.readFrom(in);
      }
      patches = all;
    }
  }
}
