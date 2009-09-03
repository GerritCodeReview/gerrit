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

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

class OwnedProjects extends Handler<List<Project>> {
  interface Factory {
    OwnedProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final IdentifiedUser user;
  private final ReviewDb db;

  @Inject
  OwnedProjects(final ProjectControl.Factory projectControlFactory,
      final IdentifiedUser user, final ReviewDb db) {
    this.projectControlFactory = projectControlFactory;
    this.user = user;
    this.db = db;
  }

  @Override
  public List<Project> call() throws OrmException {
    final List<Project> result;
    if (user.isAdministrator()) {
      result = db.projects().all().toList();

    } else {
      final HashSet<Project.NameKey> seen = new HashSet<Project.NameKey>();
      result = new ArrayList<Project>();
      for (final AccountGroup.Id groupId : user.getEffectiveGroups()) {
        for (final ProjectRight r : db.projectRights().byCategoryGroup(
            ApprovalCategory.OWN, groupId)) {
          final Project.NameKey name = r.getProjectNameKey();
          if (!seen.add(name)) {
            continue;
          }
          try {
            ProjectControl c = projectControlFactory.ownerFor(name);
            result.add(c.getProject());
          } catch (NoSuchProjectException e) {
            continue;
          }
        }
      }
    }
    Collections.sort(result, new Comparator<Project>() {
      public int compare(final Project a, final Project b) {
        return a.getName().compareTo(b.getName());
      }
    });
    return result;
  }
}
