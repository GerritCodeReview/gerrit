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


import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gwtorm.client.Column;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

public class PatchList {
  private static final Comparator<PatchListEntry> PATCH_CMP =
      new Comparator<PatchListEntry>() {
        @Override
        public int compare(final PatchListEntry a, final PatchListEntry b) {
          return a.getNewName().compareTo(b.getNewName());
        }
      };

  @Nullable
  @Column(id = 1)
  protected String oldIdName;

  @Column(id = 2)
  protected String newIdName;

  @Column(id = 3)
  protected boolean intralineDifference;

  @Column(id = 4)
  protected List<PatchListEntry> patches;

  private volatile ObjectId oldId;
  private volatile ObjectId newId;

  protected PatchList() {
  }

  PatchList(@Nullable final AnyObjectId oldId, final AnyObjectId newId,
      final boolean intralineDifference, final PatchListEntry[] patches) {
    this.oldId = oldId != null ? oldId.copy() : null;
    oldIdName = oldId != null ? oldId.name() : null;
    this.newId = newId.copy();
    newIdName = newId.name();
    this.intralineDifference = intralineDifference;

    Arrays.sort(patches, PATCH_CMP);
    this.patches = Arrays.asList(patches);
  }

  /** Old side tree or commit; null only if this is a combined diff. */
  @Nullable
  public ObjectId getOldId() {
    if (oldId == null && oldIdName != null) {
      oldId = ObjectId.fromString(oldIdName);
    }
    return oldId;
  }

  /** New side commit. */
  public ObjectId getNewId() {
    if (newId == null) {
      newId = ObjectId.fromString(newIdName);
    }
    return newId;
  }

  /** Get a sorted, unmodifiable list of all files in this list. */
  public List<PatchListEntry> getPatches() {
    return Collections.unmodifiableList(patches);
  }

  /** @return true if this list was computed with intraline difference enabled. */
  public boolean hasIntralineDifference() {
    return intralineDifference;
  }

  /**
   * Get a sorted, modifiable list of all files in this list.
   * <p>
   * The returned list items do not populate:
   * <ul>
   * <li>{@link Patch#getCommentCount()}
   * <li>{@link Patch#getDraftCount()}
   * <li>{@link Patch#isReviewedByCurrentUser()}
   * </ul>
   *
   * @param setId the patch set identity these patches belong to. This really
   *        should not need to be specified, but is a current legacy artifact of
   *        how the cache is keyed versus how the database is keyed.
   */
  public List<Patch> toPatchList(final PatchSet.Id setId) {
    final ArrayList<Patch> r = new ArrayList<Patch>(patches.size());
    for (final PatchListEntry e : patches) {
      r.add(e.toPatch(setId));
    }
    return r;
  }

  /** Find an entry by name, returning an empty entry if not present. */
  public PatchListEntry get(final String fileName) {
    final int index = search(fileName);
    return 0 <= index ? patches.get(index) : PatchListEntry.empty(fileName);
  }

  private int search(final String fileName) {
    int high = patches.size();
    int low = 0;
    while (low < high) {
      final int mid = (low + high) >>> 1;
      final int cmp = patches.get(mid).getNewName().compareTo(fileName);
      if (cmp < 0)
        low = mid + 1;
      else if (cmp == 0)
        return mid;
      else
        high = mid;
    }
    return -(low + 1);
  }
}
