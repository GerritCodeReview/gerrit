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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class RetrieveParentCandidatesImpl implements RetrieveParentCandidates {

  private final ProjectControl.Factory projectControlFactory;
  private final ReviewDb db;
  private final Project.NameKey wildProject;

  @Inject
  RetrieveParentCandidatesImpl(
      final ProjectControl.Factory projectControlFactory, final ReviewDb db,
      @WildProjectName final Project.NameKey wildProject) {
    this.projectControlFactory = projectControlFactory;
    this.db = db;
    this.wildProject = wildProject;
  }

  @Override
  public List<NameKey> getParentCandidates() throws OrmException {
    final List<Project.NameKey> r = new ArrayList<Project.NameKey>();

    for (final Project p : db.projects().getAllOrderedByParent()) {
      try {
        if (p.getParent() != null) {
          ProjectControl c = projectControlFactory.controlFor(p.getParent());
          if (c.isVisible() || c.isOwner()) {
            if (!r.contains(p.getParent())
                && !p.getParent().equals(wildProject)) {
              r.add(p.getParent());
            }
          }
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }

    return r;
  }
}
