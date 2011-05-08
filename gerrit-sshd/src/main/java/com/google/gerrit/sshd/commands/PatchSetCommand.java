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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class PatchSetCommand extends BaseCommand {

  @Option(name = "--project", aliases = "-p", usage = "project containing the patch set")
  private ProjectControl projectControl;

  @Inject
  protected ReviewDb db;

  protected final Set<PatchSet.Id> parsePatchSetId(final String patchIdentity)
      throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      final RevId id = new RevId(patchIdentity);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      final Set<PatchSet.Id> matches = new HashSet<PatchSet.Id>();
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(ps.getId());
        }
      }

      switch (matches.size()) {
        case 1:
          return matches;
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      final PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      if (db.patchSets().get(patchSetId) == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null) {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
      }
      return Collections.singleton(patchSetId);
    }

    throw error("\"" + patchIdentity + "\" is not a valid patch set");
  }

  private boolean inProject(final Change change) {
    if (projectControl == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }

}
