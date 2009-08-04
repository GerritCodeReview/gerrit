// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.admin.ProjectDetail;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ProjectDetailFactory extends Handler<ProjectDetail> {
  interface Factory {
    ProjectDetailFactory create(Project.NameKey name);
  }

  private final ProjectCache projectCache;
  private final ReviewDb db;
  private final Project.NameKey projectName;

  private ProjectDetail detail;
  private Map<AccountGroup.Id, AccountGroup> groups;

  @Inject
  ProjectDetailFactory(final ProjectCache projectCache, final ReviewDb db,
      @Assisted final Project.NameKey name) {
    this.projectCache = projectCache;
    this.db = db;
    this.projectName = name;
  }

  @Override
  public ProjectDetail call() throws OrmException, NoSuchEntityException {
    final ProjectState e = projectCache.get(projectName);
    if (e == null) {
      throw new NoSuchEntityException();
    }

    detail = new ProjectDetail();
    detail.setProject(e.getProject());

    groups = new HashMap<AccountGroup.Id, AccountGroup>();

    final List<ProjectRight> rights = new ArrayList<ProjectRight>();
    for (final ProjectRight p : e.getRights()) {
      rights.add(p);
      wantGroup(p.getAccountGroupId());
    }
    if (!ProjectRight.WILD_PROJECT.equals(e.getProject().getId())) {
      for (final ProjectRight p : projectCache.getWildcardRights()) {
        rights.add(p);
        wantGroup(p.getAccountGroupId());
      }
    }

    loadGroups();
    detail.setRights(rights);
    detail.setGroups(groups);
    return detail;
  }

  private void wantGroup(final AccountGroup.Id id) {
    groups.put(id, null);
  }

  private void loadGroups() throws OrmException {
    final ResultSet<AccountGroup> r = db.accountGroups().get(groups.keySet());
    groups.clear();
    for (final AccountGroup g : r) {
      groups.put(g.getId(), g);
    }
  }
}
