// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.List;

public class PatchSetDetail {
  protected PatchSet patchSet;
  protected PatchSetInfo info;
  protected List<Patch> patches;

  public PatchSetDetail() {
  }

  public void load(final ReviewDb db, final PatchSet ps) throws OrmException {
    patchSet = ps;
    info = db.patchSetInfo().get(patchSet.getId());
    patches = db.patches().byPatchSet(patchSet.getId()).toList();
  }

  public PatchSet getPatchSet() {
    return patchSet;
  }

  public PatchSetInfo getInfo() {
    return info;
  }

  public List<Patch> getPatches() {
    return patches;
  }
}
