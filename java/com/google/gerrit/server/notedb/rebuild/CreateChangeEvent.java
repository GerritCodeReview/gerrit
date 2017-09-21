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

package com.google.gerrit.server.notedb.rebuild;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;

class CreateChangeEvent extends Event {
  private final Change change;

  private static PatchSet.Id psId(Change change, Integer minPsNum) {
    int n;
    if (minPsNum == null) {
      // There were no patch sets for the change at all, so something is very
      // wrong. Bail and use 1 as the patch set.
      n = 1;
    } else {
      n = minPsNum;
    }
    return new PatchSet.Id(change.getId(), n);
  }

  CreateChangeEvent(Change change, Integer minPsNum) {
    super(
        psId(change, minPsNum),
        change.getOwner(),
        change.getOwner(),
        change.getCreatedOn(),
        change.getCreatedOn(),
        null);
    this.change = change;
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @Override
  void apply(ChangeUpdate update) throws IOException, OrmException {
    checkUpdate(update);
    ChangeRebuilderImpl.createChange(update, change);
  }
}
