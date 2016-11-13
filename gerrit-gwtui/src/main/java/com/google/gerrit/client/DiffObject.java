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

import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;

/**
 * Represent an object that can be diffed. This can be either a regular patch set, the base of a
 * patch set, the parent of a merge, the auto-merge of a merge or an edit patch set.
 */
public class DiffObject {
  public static final String AUTO_MERGE = "AutoMerge";

  /**
   * Parses a string that represents a diff object.
   *
   * <p>The following string representations are supported:
   *
   * <ul>
   *   <li>a positive integer: represents a patch set
   *   <li>a negative integer: represents a parent of a merge patch set
   *   <li>'0': represents the edit patch set
   *   <li>empty string or null: represents the parent of a 1-parent patch set, also called base
   *   <li>'AutoMerge': represents the auto-merge of a merge patch set
   * </ul>
   *
   * @param changeId the ID of the change to which the diff object belongs
   * @param str the string representation of the diff object
   * @return the parsed diff object, {@code null} if str cannot be parsed as diff object
   */
  public static DiffObject parse(Change.Id changeId, String str) {
    if (str == null || str.isEmpty()) {
      return new DiffObject(false);
    }

    if (AUTO_MERGE.equals(str)) {
      return new DiffObject(true);
    }

    try {
      return new DiffObject(new PatchSet.Id(changeId, Integer.parseInt(str)));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Create a DiffObject that represents the parent of a 1-parent patch set. */
  public static DiffObject base() {
    return new DiffObject(false);
  }

  /** Create a DiffObject that represents the auto-merge for a merge patch set. */
  public static DiffObject autoMerge() {
    return new DiffObject(true);
  }

  /** Create a DiffObject that represents a patch set. */
  public static DiffObject patchSet(PatchSet.Id psId) {
    return new DiffObject(psId);
  }

  private final PatchSet.Id psId;
  private final boolean autoMerge;

  private DiffObject(PatchSet.Id psId) {
    this.psId = psId;
    this.autoMerge = false;
  }

  private DiffObject(boolean autoMerge) {
    this.psId = null;
    this.autoMerge = autoMerge;
  }

  public boolean isBase() {
    return psId == null && !autoMerge;
  }

  public boolean isAutoMerge() {
    return psId == null && autoMerge;
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
   * Returns the DiffObject as PatchSet.Id.
   *
   * @return PatchSet.Id with an id > 0 for a regular patch set; PatchSet.Id with an id < 0 for a
   *     parent of a merge; PatchSet.Id with id == 0 for an edit patch set; {@code null} for the
   *     base of a 1-parent patch set and for the auto-merge of a merge patch set
   */
  public PatchSet.Id asPatchSetId() {
    return psId;
  }

  /**
   * Returns the parent number for a parent of a merge.
   *
   * @return 1-based parent number, 0 if this DiffObject is not a parent of a merge
   */
  public int getParentNum() {
    if (!isParent()) {
      return 0;
    }

    return -psId.get();
  }

  /**
   * Returns a string representation of this DiffObject that can be used in URLs.
   *
   * <p>The following string representations are returned:
   *
   * <ul>
   *   <li>a positive integer for a patch set
   *   <li>a negative integer for a parent of a merge patch set
   *   <li>'0' for the edit patch set
   *   <li>{@code null} for the parent of a 1-parent patch set, also called base
   *   <li>'AutoMerge' for the auto-merge of a merge patch set
   * </ul>
   *
   * @return string representation of this DiffObject
   */
  public String asString() {
    if (autoMerge) {
      if (Gerrit.getUserPreferences().defaultBaseForMerges() != DefaultBase.AUTO_MERGE) {
        return AUTO_MERGE;
      }
      return null;
    }

    if (psId != null) {
      return psId.getId();
    }

    return null;
  }

  @Override
  public String toString() {
    if (isPatchSet()) {
      return "Patch Set " + psId.getId();
    }

    if (isParent()) {
      return "Parent " + psId.getId();
    }

    if (isEdit()) {
      return "Edit Patch Set";
    }

    if (isAutoMerge()) {
      return "Auto Merge";
    }

    return "Base";
  }
}
