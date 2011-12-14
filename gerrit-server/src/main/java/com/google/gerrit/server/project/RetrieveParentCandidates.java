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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RetrieveParentCandidates {
  public interface Factory {
    RetrieveParentCandidates create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final AllProjectsName allProject;

  @Inject
  RetrieveParentCandidates(final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final AllProjectsName allProject) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.allProject = allProject;
  }

  public List<Project.NameKey> get() throws OrmException {
    final List<Project.NameKey> r = new ArrayList<Project.NameKey>();

    for (Project.NameKey p : projectCache.all()) {
      try {
        final ProjectControl project = projectControlFactory.controlFor(p);
        final Project.NameKey parent = project.getProject().getParent();

        if (parent != null) {
          ProjectControl c = projectControlFactory.controlFor(parent);
          if (c.isVisible() || c.isOwner()) {
            if (!r.contains(parent)) {
              r.add(parent);
            }
          }
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }

    if (!r.contains(allProject)) {
      r.add(allProject);
    }

    Collections.sort(r);
    return r;
  }
}
