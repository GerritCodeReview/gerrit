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

package com.google.gerrit.server.index;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

import java.util.HashMap;
import java.util.Map;

class VisibleFilter extends ChangeFilter {
  private final ProjectCache projectCache;
  private final CurrentUser user;
  private final Map<Project.NameKey, ProjectControl> controls;

  VisibleFilter(Query query, ProjectCache projectCache, CurrentUser user) {
    this.projectCache = projectCache;
    this.user = user;
    this.controls = new HashMap<Project.NameKey, ProjectControl>();
    this.query = new QueryWrapperFilter(query);
  }

  @Override
  public String toString() {
    if (user.getUserName() != null) {
      return "visibleto:" + user.getUserName();
    } else if (user instanceof IdentifiedUser) {
      return "visibleto:" + ((IdentifiedUser) user).getAccountId();
    } else {
      return "visibleto:" + user;
    }
  }

  @Override
  boolean match(Change c) {
    ProjectControl ctl = controls.get(c.getProject());
    if (ctl == null) {
      ProjectState p = projectCache.get(c.getProject());
      if (p == null) {
        return false;
      }
      ctl = p.controlFor(user);
      controls.put(c.getProject(), ctl);
    }

    return ctl.controlFor(c).isVisible();
  }
}
