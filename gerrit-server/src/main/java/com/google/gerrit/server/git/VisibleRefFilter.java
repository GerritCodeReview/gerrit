// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.OrmException;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class VisibleRefFilter implements RefFilter {

  private static final Logger log =
    LoggerFactory.getLogger(VisibleRefFilter.class);

  private final ProjectControl projectControl;
  private final ReviewDb reviewDb;

  public VisibleRefFilter(final ProjectControl projectControl,
      final ReviewDb reviewDb) {
    this.projectControl = projectControl;
    this.reviewDb = reviewDb;
  }

  @Override
  public Map<String, Ref> filter(Map<String,Ref> refs) {

    RefControl refControl = projectControl.controlForRef("refs/*");
    if (refControl.isVisible()) {
      return refs;
    }

    Map<String, Ref> result = new HashMap<String, Ref>();
    Set<Change.Id> visibleChanges = new HashSet<Change.Id>();

    // We must first figure out which changes are visible to make a decision
    // for each ref that we received that represents a change in Gerrit.
    try {
      Iterable<Change> changes =
        reviewDb.changes().byProject(projectControl.getProject().getNameKey());
      for (Change change : changes) {
        ChangeControl ctl = projectControl.controlFor(change);
        if (ctl.isVisible()) {
          visibleChanges.add(change.getId());
        }
      }
    } catch (OrmException e) {
      log.error("Cannot load changes for project "
          + projectControl.getProject().getName(), e);
    }

    for (Ref ref : refs.values()) {
      if (PatchSet.isRef(ref.getName())) {
        // This is a reference to a change in Gerrit.
        Change.Id changeId = Change.Id.fromRef(ref.getName());
        if (visibleChanges.contains(changeId)) {
          result.put(ref.getName(), ref);
        }
      } else {
        RefControl ctl = projectControl.controlForRef(ref.getName());
        if (ctl.isVisible()) {
          result.put(ref.getName(), ref);
        }
      }
    }

    return result;
  }
}
