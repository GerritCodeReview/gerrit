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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class ChildProjectsCollection implements
    ChildCollection<ProjectResource, ChildProjectResource> {
  private final Provider<ListChildProjects> list;
  private final ProjectsCollection projectsCollection;
  private final DynamicMap<RestView<ChildProjectResource>> views;

  @Inject
  ChildProjectsCollection(Provider<ListChildProjects> list,
      ProjectsCollection projectsCollection,
      DynamicMap<RestView<ChildProjectResource>> views) {
    this.list = list;
    this.projectsCollection = projectsCollection;
    this.views = views;
  }

  @Override
  public ListChildProjects list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public ChildProjectResource parse(ProjectResource parent, IdString id)
      throws ResourceNotFoundException, IOException {
    ProjectResource p =
        projectsCollection.parse(TopLevelResource.INSTANCE, id);
    for (ProjectState pp : p.getControl().getProjectState().parents()) {
      if (parent.getNameKey().equals(pp.getProject().getNameKey())) {
        return new ChildProjectResource(parent, p.getControl());
      }
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<ChildProjectResource>> views() {
    return views;
  }
}
