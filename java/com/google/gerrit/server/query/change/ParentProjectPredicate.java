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

import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.index.query.OrPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ChildProjects;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectAccessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParentProjectPredicate extends OrPredicate<ChangeData> {
  private static final Logger log = LoggerFactory.getLogger(ParentProjectPredicate.class);

  protected final String value;

  public ParentProjectPredicate(
      ProjectAccessor.Factory projectAccessorFactory, ChildProjects childProjects, String value) {
    super(predicates(projectAccessorFactory, childProjects, value));
    this.value = value;
  }

  protected static List<Predicate<ChangeData>> predicates(
      ProjectAccessor.Factory projectAccessorFactory, ChildProjects childProjects, String value) {
    ProjectAccessor projectAccessor;
    try {
      projectAccessor = projectAccessorFactory.create(new Project.NameKey(value));
    } catch (IOException | NoSuchProjectException e) {
      // Already logged by ProjectCacheImpl.
      return Collections.emptyList();
    }

    List<Predicate<ChangeData>> r = new ArrayList<>();
    r.add(new ProjectPredicate(projectAccessor.getName()));
    try {
      for (ProjectInfo p : childProjects.list(projectAccessor.getNameKey())) {
        r.add(new ProjectPredicate(p.name));
      }
    } catch (PermissionBackendException e) {
      log.warn("cannot check permissions to expand child projects", e);
    }
    return r;
  }

  @Override
  public String toString() {
    return ChangeQueryBuilder.FIELD_PARENTPROJECT + ":" + value;
  }
}
