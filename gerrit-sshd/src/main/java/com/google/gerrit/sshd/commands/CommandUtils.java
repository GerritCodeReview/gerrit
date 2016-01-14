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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.gwtorm.server.OrmException;

import java.util.ArrayList;
import java.util.List;

public class CommandUtils {
  public static PatchSet parsePatchSet(String patchIdentity, ReviewDb db,
      InternalChangeQuery query, ProjectControl projectControl, String branch)
      throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      List<ChangeData> cds;
      if (projectControl != null) {
        Project.NameKey p = projectControl.getProject().getNameKey();
        if (branch != null) {
          cds = query.byBranchCommit(p.get(), branch, patchIdentity);
        } else {
          cds = query.byProjectCommit(p, patchIdentity);
        }
      } else {
        cds = query.byCommit(patchIdentity);
      }
      List<PatchSet> matches = new ArrayList<>(cds.size());
      for (ChangeData cd : cds) {
        Change c = cd.change();
        if (!(inProject(c, projectControl) && inBranch(c, branch))) {
          continue;
        }
        for (PatchSet ps : cd.patchSets()) {
          if (ps.getRevision().matches(patchIdentity)) {
            matches.add(ps);
          }
        }
      }

      switch (matches.size()) {
        case 1:
          return matches.iterator().next();
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      PatchSet patchSet = db.patchSets().get(patchSetId);
      if (patchSet == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null || branch != null) {
        Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change, projectControl)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
        if (!inBranch(change, branch)) {
          throw error("change " + change.getId() + " not in branch "
              + change.getDest().get());
        }
      }
      return patchSet;
    }

    throw error("\"" + patchIdentity + "\" is not a valid patch set");
  }

  private static boolean inProject(Change change,
      ProjectControl projectControl) {
    if (projectControl == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
  }

  private static boolean inBranch(Change change, String branch) {
    if (branch == null) {
      // No --branch option, so they want every branch.
      return true;
    }
    return change.getDest().get().equals(branch);
  }

  public static UnloggedFailure error(String msg) {
    return new UnloggedFailure(1, msg);
  }
}
