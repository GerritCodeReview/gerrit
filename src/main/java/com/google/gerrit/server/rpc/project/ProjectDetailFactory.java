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
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.rpc.Handler;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ProjectDetailFactory extends Handler<ProjectDetail> {
  interface Factory {
    ProjectDetailFactory create(@Assisted("name") Project.NameKey name);
  }

  private final ApprovalTypes approvalTypes;
  private final GroupCache groupCache;
  private final ProjectCache projectCache;
  private final Project.NameKey wildProject;
  private final Project.NameKey projectName;

  private ProjectDetail detail;
  private Map<AccountGroup.Id, AccountGroup> groups;

  @Inject
  ProjectDetailFactory(final ApprovalTypes approvalTypes,
      final GroupCache groupCache, final ProjectCache projectCache,
      @WildProjectName final Project.NameKey wp,
      @Assisted("name") final Project.NameKey name) {
    this.approvalTypes = approvalTypes;
    this.groupCache = groupCache;
    this.projectCache = projectCache;
    this.wildProject = wp;
    this.projectName = name;
  }

  @Override
  public ProjectDetail call() throws NoSuchEntityException {
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
    if (!wildProject.equals(e.getProject().getNameKey())) {
      for (final ProjectRight p : projectCache.getWildcardRights()) {
        rights.add(p);
        wantGroup(p.getAccountGroupId());
      }
    }
    loadGroups();

    Collections.sort(rights, new Comparator<ProjectRight>() {
      @Override
      public int compare(final ProjectRight a, final ProjectRight b) {
        int rc = categoryOf(a).compareTo(categoryOf(b));
        if (rc == 0) {
          rc = groupOf(a).compareTo(groupOf(b));
        }
        return rc;
      }

      private String categoryOf(final ProjectRight r) {
        final ApprovalType type =
            approvalTypes.getApprovalType(r.getApprovalCategoryId());
        if (type == null) {
          return r.getApprovalCategoryId().get();
        }
        return type.getCategory().getName();
      }

      private String groupOf(final ProjectRight r) {
        return groups.get(r.getAccountGroupId()).getName();
      }
    });

    detail.setRights(rights);
    detail.setGroups(groups);
    return detail;
  }

  private void wantGroup(final AccountGroup.Id id) {
    groups.put(id, null);
  }

  private void loadGroups() {
    final Set<AccountGroup.Id> toGet = groups.keySet();
    groups = new HashMap<AccountGroup.Id, AccountGroup>();
    for (AccountGroup.Id groupId : toGet) {
      groups.put(groupId, groupCache.get(groupId));
    }
  }
}
