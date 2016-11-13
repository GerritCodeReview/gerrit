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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Singleton
public class SuggestParentCandidates {
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final AllProjectsName allProject;

  @Inject
  SuggestParentCandidates(
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache,
      final AllProjectsName allProject) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.allProject = allProject;
  }

  public List<Project.NameKey> getNameKeys() throws NoSuchProjectException {
    List<Project> pList = getProjects();
    final List<Project.NameKey> nameKeys = new ArrayList<>(pList.size());
    for (Project p : pList) {
      nameKeys.add(p.getNameKey());
    }
    return nameKeys;
  }

  public List<Project> getProjects() throws NoSuchProjectException {
    Set<Project> projects =
        new TreeSet<>(
            new Comparator<Project>() {
              @Override
              public int compare(Project o1, Project o2) {
                return o1.getName().compareTo(o2.getName());
              }
            });
    for (Project.NameKey p : projectCache.all()) {
      try {
        final ProjectControl control = projectControlFactory.controlFor(p);
        final Project.NameKey parentK = control.getProject().getParent();
        if (parentK != null) {
          ProjectControl pControl = projectControlFactory.controlFor(parentK);
          if (pControl.isVisible() || pControl.isOwner()) {
            projects.add(pControl.getProject());
          }
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }
    projects.add(projectControlFactory.controlFor(allProject).getProject());
    return new ArrayList<>(projects);
  }
}
