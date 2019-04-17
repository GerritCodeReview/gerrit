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
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PatchSetParser {
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;
  private final ChangeFinder changeFinder;

  @Inject
  PatchSetParser(
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      ChangeFinder changeFinder) {
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.changeFinder = changeFinder;
  }

  public PatchSet parsePatchSet(String token, ProjectState projectState, String branch)
      throws UnloggedFailure {
    // By commit?
    //
    if (token.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      InternalChangeQuery query = queryProvider.get();
      List<ChangeData> cds;
      if (projectState != null) {
        Project.NameKey p = projectState.getNameKey();
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
        if (!(inProject(c, projectState) && inBranch(c, branch))) {
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
      ChangeNotes notes = getNotes(projectState, patchSetId.getParentKey());
      PatchSet patchSet = psUtil.get(notes, patchSetId);
      if (patchSet == null) {
        throw error("\"" + token + "\" no such patch set");
      }
      if (projectState != null || branch != null) {
        Change change = notes.getChange();
        if (!inProject(change, projectState)) {
          throw error("change " + change.getId() + " not in project " + projectState.getName());
        }
        if (!inBranch(change, branch)) {
          throw error("change " + change.getId() + " not in branch " + branch);
        }
      }
      return patchSet;
    }

    throw error("\"" + token + "\" is not a valid patch set");
  }

  private ChangeNotes getNotes(@Nullable ProjectState projectState, Change.Id changeId)
      throws UnloggedFailure {
    if (projectState != null) {
      return notesFactory.create(projectState.getNameKey(), changeId);
    }
    try {
      ChangeNotes notes = changeFinder.findOne(changeId);
      return notesFactory.create(notes.getProjectName(), changeId);
    } catch (NoSuchChangeException e) {
      throw error("\"" + changeId + "\" no such change");
    }
  }

  private static boolean inProject(Change change, ProjectState projectState) {
    if (projectState == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectState.getNameKey().equals(change.getProject());
  }

  private static boolean inBranch(Change change, String branch) {
    if (branch == null) {
      // No --branch option, so they want every branch.
      return true;
    }
    return change.getDest().branch().equals(branch);
  }

  public static UnloggedFailure error(String msg) {
    return new UnloggedFailure(1, msg);
  }
}
