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

import com.google.gerrit.reviewdb.client.PatchSet;

import org.eclipse.jgit.revwalk.RevCommit;

public class ChangeEditData {
  private final ChangeEdit edit;
  private final RevCommit editCommit;
  private final PatchSet basePatchSet;

  public ChangeEditData(ChangeEdit edit,
      RevCommit editCommit,
      PatchSet basePatchSet) {
    this.edit = edit;
    this.editCommit = editCommit;
    this.basePatchSet = basePatchSet;
  }

  public PatchSet getBasePatchSet() {
    return basePatchSet;
  }

  public ChangeEdit getEdit() {
    return edit;
  }

  public RevCommit getEditCommit() {
    return editCommit;
  }
}
