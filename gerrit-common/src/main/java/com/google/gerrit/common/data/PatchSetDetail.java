// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;

import java.util.List;

public class PatchSetDetail {
  public interface PatchValidator {
    /**
     * Returns true if patch is valid
     *
     * @param patch
     * @return
     */
    boolean isValid(Patch patch);
  }

  protected PatchSet patchSet;
  protected PatchSetInfo info;
  protected List<Patch> patches;

  public PatchSetDetail() {
  }

  public PatchSet getPatchSet() {
    return patchSet;
  }

  public void setPatchSet(final PatchSet ps) {
    patchSet = ps;
  }

  public PatchSetInfo getInfo() {
    return info;
  }

  public void setInfo(final PatchSetInfo i) {
    info = i;
  }

  public List<Patch> getPatches() {
    return patches;
  }

  public void setPatches(final List<Patch> p) {
    patches = p;
  }

  /**
   * Gets the next patch
   *
   * @param currentIndex
   * @param validator
   * @param loopAround loops back around to the front and traverses if this is
   *        true
   * @return
   */
  public int getNextPatch(int currentIndex, boolean loopAround,
      PatchValidator... validators) {
    return getNextPatchHelper(currentIndex, loopAround, patches.size(),
        validators);
  }

  /**
   * Helper function for getNextPatch
   *
   * @param currentIndex
   * @param validator
   * @param loopAround
   * @param maxIndex will only traverse up to this index
   * @return
   */
  private int getNextPatchHelper(int currentIndex, boolean loopAround,
      int maxIndex, PatchValidator... validators) {
    for (int i = currentIndex + 1; i < maxIndex; i++) {
      Patch patch = patches.get(i);
      if (patch != null && patchIsValid(patch, validators)) {
        return i;
      }
    }

    if (loopAround) {
      return getNextPatchHelper(-1, false, currentIndex, validators);
    }

    return -1;
  }

  /**
   * @return the index to the next patch
   */
  public int getPreviousPatch(int currentIndex, PatchValidator... validators) {
    for (int i = currentIndex - 1; i >= 0; i--) {
      Patch patch = patches.get(i);
      if (patch != null && patchIsValid(patch, validators)) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Helper function that returns whether a patch is valid or not
   *
   * @param patch
   * @param validators
   * @return whether the patch is valid based on the validators
   */
  private boolean patchIsValid(Patch patch, PatchValidator... validators) {
    for (PatchValidator v : validators) {
      if (!v.isValid(patch)) return false;
    }
    return true;
  }
}
