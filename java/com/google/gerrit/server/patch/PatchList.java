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
import static org.eclipse.jgit.lib.ObjectIdSerializer.read;
import static org.eclipse.jgit.lib.ObjectIdSerializer.readWithoutMarker;
import static org.eclipse.jgit.lib.ObjectIdSerializer.write;
import static org.eclipse.jgit.lib.ObjectIdSerializer.writeWithoutMarker;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.git.ObjectIds;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

  @VisibleForTesting
  static final Comparator<String> FILE_PATH_CMP =
      Comparator.comparing(Patch::isMagic).reversed().thenComparing(Comparator.naturalOrder());

  /**
   * We use the ChangeType comparator for a rare case when PatchList contains two entries for the
   * same file, e.g. {ADDED, DELETED}. We return a single entry according to the following order.
   * Check the following bug for an example case:
   * https://issues.gerritcodereview.com/issues/40013315.
   */
  @VisibleForTesting
  static class ChangeTypeCmp implements Comparator<ChangeType> {
    static final List<ChangeType> order =
        ImmutableList.of(
            ChangeType.ADDED,
            ChangeType.RENAMED,
            ChangeType.MODIFIED,
            ChangeType.COPIED,
            ChangeType.REWRITE,
            ChangeType.DELETED);

    @Override
    public int compare(ChangeType o1, ChangeType o2) {
      int idx1 = priority(o1);
      int idx2 = priority(o2);
      return idx1 - idx2;
    }

    private int priority(ChangeType changeType) {
      int idx = order.indexOf(changeType);
      // Return least priority if the element is not in the order list.
      return idx == -1 ? order.size() : idx;
    }
  }

  @VisibleForTesting static final Comparator<ChangeType> CHANGE_TYPE_CMP = new ChangeTypeCmp();

  private static final Comparator<PatchListEntry> PATCH_CMP =
      Comparator.comparing(PatchListEntry::getNewName, FILE_PATH_CMP)
          .thenComparing(PatchListEntry::getChangeType, CHANGE_TYPE_CMP);

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
    this.oldId = ObjectIds.copyOrNull(oldId);
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

  /** Returns the comparison type */
  public ComparisonType getComparisonType() {
    return comparisonType;
  }

  /** Returns total number of new lines added. */
  public int getInsertions() {
    return insertions;
  }

  /** Returns total number of lines removed. */
  public int getDeletions() {
    return deletions;
  }

  /** Find an entry by name, returning an empty entry if not present. */
  public PatchListEntry get(String fileName) {
    int index = search(fileName);
    if (index >= 0) {
      return patches[index];
    }
    // If index is negative, it marks the insertion point of the object in the list.
    // index = (-(insertion point) - 1).
    // Since we use the ChangeType in the comparison, the object that we are using in the lookup
    // (which has a ADDED ChangeType) may have a different ChangeType than the object in the list.
    // For this reason, we look at the file name of the object at the insertion point and return it
    // if it has the same name.
    index = -1 * (index + 1);
    if (index < patches.length && patches[index].getNewName().equals(fileName)) {
      return patches[index];
    }
    return PatchListEntry.empty(fileName);
  }

  private int search(String fileName) {
    PatchListEntry want = PatchListEntry.empty(fileName, ChangeType.ADDED);
    return Arrays.binarySearch(patches, 0, patches.length, want, PATCH_CMP);
  }

  private void writeObject(ObjectOutputStream output) throws IOException {
    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try (DeflaterOutputStream out = new DeflaterOutputStream(buf)) {
      write(out, oldId);
      writeWithoutMarker(out, newId);
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
      oldId = read(in);
      newId = readWithoutMarker(in);
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
