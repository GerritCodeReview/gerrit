// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;

/**
 * Represent an object that can be diffed. This can be either a regular patch
 * set, the base of a patch set, the parent of a merge, the auto-merge of a
 * merge or an edit patch set.
 */
public class DiffObject {

  /**
   * Parses a string that represents a diff object.
   *
   * @param changeId the ID of the change to which the diff object belongs
   * @param str the string representation of the diff object
   * @return the parsed diff object, {@code null} if str cannot be parsed as
   *         diff object
   */
  public static DiffObject parse(Change.Id changeId, String str) {
    if (str == null || str.isEmpty()) {
      return new DiffObject(null);
    }

    try {
      return new DiffObject(new PatchSet.Id(changeId, Integer.parseInt(str)));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static DiffObject base() {
    return new DiffObject(null);
  }

  public static DiffObject autoMerge() {
    return new DiffObject(null);
  }

  public static DiffObject patchSet(PatchSet.Id psId) {
    return new DiffObject(psId);
  }

  private final PatchSet.Id psId;

  private DiffObject(PatchSet.Id psId) {
    this.psId = psId;
  }

  public boolean isBaseOrAutoMerge() {
    return psId == null;
  }

  public boolean isPatchSet() {
    return psId != null && psId.get() > 0;
  }

  public boolean isParent() {
    return psId != null && psId.get() < 0;
  }

  public boolean isEdit() {
    return psId != null && psId.get() == 0;
  }

  /**
   * Returns the diff object as PatchSet.Id.
   *
   * @return PatchSet.Id with an id > 0 for a regular patch set; PatchSet.Id
   *         with an id < 0 for a parent of a merge; PatchSet.Id with id == 0
   *         for an edit patch set; otherwise {@code null}
   */
  public PatchSet.Id asPatchSetId() {
    return psId;
  }

  /**
   * Returns the parent number for a parent of a merge.
   *
   * @return 1-based parent number, 0 if this diff object is not a parent of a
   *         merge
   */
  public int getParentNum() {
    if (!isParent()) {
      return 0;
    }

    return -psId.get();
  }

  @Override
  public String toString() {
    if (psId != null) {
      return psId.getId();
    }

    return null;
  }
}
