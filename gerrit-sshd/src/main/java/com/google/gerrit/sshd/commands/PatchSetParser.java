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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeFinder;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PatchSetParser {
  private final Provider<ReviewDb> db;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;
  private final ChangeFinder changeFinder;
  private final Provider<CurrentUser> self;

  @Inject
  PatchSetParser(
      Provider<ReviewDb> db,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      ChangeFinder changeFinder,
      Provider<CurrentUser> self) {
    this.db = db;
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.changeFinder = changeFinder;
    this.self = self;
  }

  public PatchSet parsePatchSet(String token, ProjectControl projectControl, String branch)
      throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (token.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      InternalChangeQuery query = queryProvider.get();
      List<ChangeData> cds;
      if (projectControl != null) {
        Project.NameKey p = projectControl.getProject().getNameKey();
        if (branch != null) {
          cds = query.byBranchCommit(p.get(), branch, token);
        } else {
          cds = query.byProjectCommit(p, token);
        }
      } else {
        cds = query.byCommit(token);
      }
      List<PatchSet> matches = new ArrayList<>(cds.size());
      for (ChangeData cd : cds) {
        Change c = cd.change();
        if (!(inProject(c, projectControl) && inBranch(c, branch))) {
          continue;
        }
        for (PatchSet ps : cd.patchSets()) {
          if (ps.getRevision().matches(token)) {
            matches.add(ps);
          }
        }
      }

      switch (matches.size()) {
        case 1:
          return matches.iterator().next();
        case 0:
          throw error("\"" + token + "\" no such patch set");
        default:
          throw error("\"" + token + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (token.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(token);
      } catch (IllegalArgumentException e) {
        throw error("\"" + token + "\" is not a valid patch set");
      }
      ChangeNotes notes = getNotes(projectControl, patchSetId.getParentKey());
      PatchSet patchSet = psUtil.get(db.get(), notes, patchSetId);
      if (patchSet == null) {
        throw error("\"" + token + "\" no such patch set");
      }
      if (projectControl != null || branch != null) {
        Change change = notes.getChange();
        if (!inProject(change, projectControl)) {
          throw error(
              "change "
                  + change.getId()
                  + " not in project "
                  + projectControl.getProject().getName());
        }
        if (!inBranch(change, branch)) {
          throw error("change " + change.getId() + " not in branch " + branch);
        }
      }
      return patchSet;
    }

    throw error("\"" + token + "\" is not a valid patch set");
  }

  private ChangeNotes getNotes(@Nullable ProjectControl projectControl, Change.Id changeId)
      throws OrmException, UnloggedFailure {
    if (projectControl != null) {
      return notesFactory.create(db.get(), projectControl.getProject().getNameKey(), changeId);
    }
    try {
      ChangeControl ctl = changeFinder.findOne(changeId, self.get());
      return notesFactory.create(db.get(), ctl.getProject().getNameKey(), changeId);
    } catch (NoSuchChangeException e) {
      throw error("\"" + changeId + "\" no such change");
    }
  }

  private static boolean inProject(Change change, ProjectControl projectControl) {
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
