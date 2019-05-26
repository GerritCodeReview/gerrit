// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A single user's edit for a change.
 *
 * <p>There is max. one edit per user per change. Edits are stored on refs:
 * refs/users/UU/UUUU/edit-CCCC/P where UU/UUUU is sharded representation of user account, CCCC is
 * change number and P is the patch set number it is based on.
 */
public class ChangeEdit {
  private final Change change;
  private final String editRefName;
  private final RevCommit editCommit;
  private final PatchSet basePatchSet;

  public ChangeEdit(
      Change change, String editRefName, RevCommit editCommit, PatchSet basePatchSet) {
    this.change = requireNonNull(change);
    this.editRefName = requireNonNull(editRefName);
    this.editCommit = requireNonNull(editCommit);
    this.basePatchSet = requireNonNull(basePatchSet);
  }

  public Change getChange() {
    return change;
  }

  public String getRefName() {
    return editRefName;
  }

  public RevCommit getEditCommit() {
    return editCommit;
  }

  public PatchSet getBasePatchSet() {
    return basePatchSet;
  }
}
