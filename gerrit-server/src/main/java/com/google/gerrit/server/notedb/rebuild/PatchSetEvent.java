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
import com.google.gerrit.server.notedb.PatchSetState;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

class PatchSetEvent extends Event {
  private final Change change;
  private final PatchSet ps;
  private final RevWalk rw;
  boolean createChange;

  PatchSetEvent(Change change, PatchSet ps, RevWalk rw) {
    super(
        ps.getId(),
        ps.getUploader(),
        ps.getUploader(),
        ps.getCreatedOn(),
        change.getCreatedOn(),
        null);
    this.change = change;
    this.ps = ps;
    this.rw = rw;
  }

  @Override
  boolean uniquePerUpdate() {
    return true;
  }

  @Override
  void apply(ChangeUpdate update) throws IOException, OrmException {
    checkUpdate(update);
    if (createChange) {
      ChangeRebuilderImpl.createChange(update, change);
    } else {
      update.setSubject(change.getSubject());
      update.setSubjectForCommit("Create patch set " + ps.getPatchSetId());
    }
    setRevision(update, ps);
    List<String> groups = ps.getGroups();
    if (!groups.isEmpty()) {
      update.setGroups(ps.getGroups());
    }
    if (ps.isDraft()) {
      update.setPatchSetState(PatchSetState.DRAFT);
    }
  }

  private void setRevision(ChangeUpdate update, PatchSet ps) throws IOException {
    String rev = ps.getRevision().get();
    String cert = ps.getPushCertificate();
    ObjectId id;
    try {
      id = ObjectId.fromString(rev);
    } catch (InvalidObjectIdException e) {
      update.setRevisionForMissingCommit(rev, cert);
      return;
    }
    try {
      update.setCommit(rw, id, cert);
    } catch (MissingObjectException e) {
      update.setRevisionForMissingCommit(rev, cert);
      return;
    }
  }
}
