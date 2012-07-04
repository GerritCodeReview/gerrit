// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks the user's rights before delivering a StreamEvent to him. */
public interface StreamEventAuth {
  boolean isVisibleTo(IdentifiedUser user);

  public class ChangeAuth implements StreamEventAuth {
    private static final Logger log = LoggerFactory.getLogger(ChangeAuth.class);

    private Change change;
    private ReviewDb db;
    private ProjectCache projectCache;

    public ChangeAuth(Change change, ReviewDb db, ProjectCache projectCache) {
      this.change = change;
      this.db = db;
      this.projectCache = projectCache;
    }

    public boolean isVisibleTo(IdentifiedUser user) {
      final ProjectState pe = projectCache.get(change.getProject());
      if (pe == null) {
        return false;
      }
      final ProjectControl pc = pe.controlFor(user);
      try {
        return pc.controlFor(change).isVisible(db);
      } catch (OrmException e) {
        log.error("It can't check change visibility to a user.", e);
        return false;
      }
    }
  }

  public class BranchAuth implements StreamEventAuth {
    private Branch.NameKey branchName;
    private ProjectCache projectCache;

    public BranchAuth(Branch.NameKey branchName, ProjectCache projectCache) {
      this.branchName = branchName;
      this.projectCache = projectCache;
    }

    public boolean isVisibleTo(IdentifiedUser user) {
      final ProjectState pe = projectCache.get(branchName.getParentKey());
      if (pe == null) {
        return false;
      }
      final ProjectControl pc = pe.controlFor(user);
      return pc.controlForRef(branchName).isVisible();
    }
  }
}
