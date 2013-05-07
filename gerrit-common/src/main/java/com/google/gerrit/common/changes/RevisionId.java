// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.common.changes;

import com.google.gerrit.reviewdb.client.PatchSet;

/**
 * Identifier for a revision.
 * <p>
 * A revision can either be just a patch set, or a series of in-progress edits
 * on top of a patch set, tied to a particular user.
 */
public class RevisionId {
  protected PatchSet.Id psid;
  protected boolean isEdit;

  public RevisionId(PatchSet.Id psid, boolean isEdit) {
    this.psid = psid;
    this.isEdit = isEdit;
  }

  protected RevisionId() {
  }

  public PatchSet.Id getPatchSetId() {
    return psid;
  }

  public boolean isEdit() {
    return isEdit;
  }
}
