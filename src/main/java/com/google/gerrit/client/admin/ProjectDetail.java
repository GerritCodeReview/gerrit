// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectDetail {
  protected Project project;
  protected Map<AccountGroup.Id, AccountGroup> groups;
  protected List<ProjectRight> rights;

  public ProjectDetail() {
  }

  public void load(final ReviewDb db, final ProjectCache.Entry g)
      throws OrmException {
    project = g.getProject();
    groups = new HashMap<AccountGroup.Id, AccountGroup>();
    wantGroup(project.getOwnerGroupId());

    rights = new ArrayList<ProjectRight>();
    for (final ProjectRight p : g.getRights()) {
      rights.add(p);
      wantGroup(p.getAccountGroupId());
    }
    if (!ProjectRight.WILD_PROJECT.equals(project.getId())) {
      for (final ProjectRight p : Common.getProjectCache().getWildcardRights()) {
        rights.add(p);
        wantGroup(p.getAccountGroupId());
      }
    }

    loadGroups(db);
  }

  private void wantGroup(final AccountGroup.Id id) {
    groups.put(id, null);
  }

  private void loadGroups(final ReviewDb db) throws OrmException {
    final ResultSet<AccountGroup> r = db.accountGroups().get(groups.keySet());
    groups.clear();
    for (final AccountGroup g : r) {
      groups.put(g.getId(), g);
    }
  }
}
