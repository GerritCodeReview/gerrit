// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetElement;
import com.google.gerrit.reviewdb.ChangeSetInfo;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.List;

/**
 * Factory class creating ChangeSetInfo
 */
public class ChangeSetInfoFactory {

  interface Factory {
    ChangeSetInfoFactory create();
  }

  private final PatchSetInfoFactory psif;
  private final ReviewDb db;

  @Inject
  public ChangeSetInfoFactory(final PatchSetInfoFactory psif,
      final ReviewDb db) {
    this.db = db;
    this.psif = psif;
  }

  public ChangeSetInfo get(ChangeSet.Id csId) throws OrmException, NoSuchEntityException,
      ChangeSetInfoNotAvailableException {
    try {
      final List<ChangeSetElement> changeSetElements = db.changeSetElements().byChangeSet(csId).toList();
      // Our data source will be the last change in the ChangeSet
      //
      final Change.Id changeId = changeSetElements.get(changeSetElements.size() - 1).getChangeId();
      final Change change = db.changes().get(changeId);
      final PatchSet patchSet = db.patchSets().get(change.currentPatchSetId());

      final PatchSetInfo info = psif.get(patchSet.getId());

      ChangeSetInfo csi = new ChangeSetInfo(csId);
      csi.setAuthor(info.getAuthor());
      csi.setMessage(db.changeSets().get(csId).getMessage());
      return csi;
    } catch (PatchSetInfoNotAvailableException e) {
      throw new ChangeSetInfoNotAvailableException(e);
    }
  }
}
