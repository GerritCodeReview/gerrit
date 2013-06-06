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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;

public class Projects implements
    ChildCollection<AccountResource, AccountResource.Project> {

  private final DynamicMap<RestView<AccountResource.Project>> views;
  private final Provider<ProjectsCollection> projects;

  @Inject
  Projects(DynamicMap<RestView<AccountResource.Project>> views,
      Provider<ProjectsCollection> projects) {
    this.views = views;
    this.projects = projects;
  }

  @Override
  public RestView<AccountResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public AccountResource.Project parse(final AccountResource rsrc, IdString id)
      throws ResourceNotFoundException, IOException {
    ProjectResource project =
        projects.get().parse(TopLevelResource.INSTANCE, id, rsrc.getUser());
    return new AccountResource.Project(rsrc.getUser(), project.getControl());
  }

  @Override
  public DynamicMap<RestView<AccountResource.Project>> views() {
    return views;
  }
}
