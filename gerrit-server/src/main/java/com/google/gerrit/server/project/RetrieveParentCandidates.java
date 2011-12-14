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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

  public List<Project.NameKey> get() throws OrmException,
      NoSuchProjectException {
    final List<Project.NameKey> result = new ArrayList<Project.NameKey>();
    List<Project> pList = getParentCandidates();
    for (Project p : pList) {
      result.add(p.getNameKey());
    }
    return result;
  }

  public List<Project> getParentCandidates() throws OrmException,
      NoSuchProjectException {
    Set<Project> result = new TreeSet<Project>();
    for (Project.NameKey p : projectCache.all()) {
      try {
        final ProjectControl control = projectControlFactory.controlFor(p);
        final Project.NameKey parentK = control.getProject().getParent();
        if (parentK != null) {
          ProjectControl pControl = projectControlFactory.controlFor(parentK);
          if (pControl.isVisible() || pControl.isOwner()) {
            result.add(pControl.getProject());
          }
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }
    result.add(projectControlFactory.controlFor(allProject).getProject());
    return new ArrayList<Project>(result);
  }
}
