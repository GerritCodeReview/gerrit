// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.index.query.OrPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ChildProjects;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParentProjectPredicate extends OrPredicate<ChangeData> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final String value;

  public ParentProjectPredicate(
      ProjectCache projectCache, ChildProjects childProjects, String value) {
    super(predicates(projectCache, childProjects, value));
    this.value = value;
  }

  protected static List<Predicate<ChangeData>> predicates(
      ProjectCache projectCache, ChildProjects childProjects, String value) {
    ProjectState projectState = projectCache.get(Project.nameKey(value));
    if (projectState == null) {
      return Collections.emptyList();
    }

    List<Predicate<ChangeData>> r = new ArrayList<>();
    r.add(new ProjectPredicate(projectState.getName()));
    try {
      for (ProjectInfo p : childProjects.list(projectState.getNameKey())) {
        r.add(new ProjectPredicate(p.name));
      }
    } catch (PermissionBackendException e) {
      logger.atWarning().withCause(e).log("cannot check permissions to expand child projects");
    }
    return r;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_PARENTPROJECT + ":" + value;
  }
}
