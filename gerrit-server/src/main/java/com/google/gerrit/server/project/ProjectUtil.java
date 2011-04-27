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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectParent;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectUtil {

  public static Set<Project.NameKey> getParents(final ReviewDb db, final Project p)
      throws OrmException {
    List<ProjectParent> parents =
      db.projectParents().byProject(p.getNameKey()).toList();
    Set<Project.NameKey> keys = new HashSet<Project.NameKey>(parents.size());
    for (ProjectParent pa: parents) {
      keys.add(pa.getParentKey());
    }
    return keys;
  }

  private ReviewDb db;
  private ProjectCache projectCache;
  private Project.NameKey wildProject;

  @Inject
  public ProjectUtil(ReviewDb db, ProjectCache projectCache,
      @WildProjectName Project.NameKey wildProject) {
    this.db = db;
    this.projectCache = projectCache;
    this.wildProject = wildProject;
  }

  public String addParent(ProjectControl cCtrl, ProjectControl pCtrl)
      throws OrmException {
    final Project.NameKey cKey = cCtrl.getProject().getNameKey();
    final Project.NameKey pKey = pCtrl.getProject().getNameKey();
    final String cname = cCtrl.getProject().getName();
    final String pname = pCtrl.getProject().getName();

    if (cKey.equals(pKey)) {
      return "error: Cannot set parent of '" + cname + "' to itself\n";
    }

    if (wildProject.equals(cKey)) {
      // Don't allow the wild card project to have a parent.
      //
      return "error: Cannot set parent of '" + cname + "'\n";
    }

    Set<Project.NameKey> ancestors = new HashSet<Project.NameKey>();
    for (List<Project.NameKey> alist : projectCache.get(pKey).getAncestorLines()) {
      ancestors.addAll(alist);
    }

    if (ancestors.contains(cKey)) {
      // Try to avoid creating a cycle in the parent pointers.
      //
      return "error: Cycle exists between '" + cname + "' and '" + pname + "'\n";
    }

    final Project child = db.projects().get(cKey);
    if (child == null) {
      // Race condition? Its in the cache, but not the database.
      //
      return "error: Project '" + cname + "' not found\n";
    }

    ProjectParent pp = new ProjectParent(child.getNameKey(), pKey);
    try {
      db.projectParents().insert(Collections.singleton(pp));
    } catch (OrmDuplicateKeyException e) {
      return "error: Project '" + pname + "' already parent of '" + cname + "'\n";
    }
    return "";
  }
}
