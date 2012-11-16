// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class ProjectsCollection implements
    RestCollection<TopLevelResource, ProjectResource> {
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<ListProjects> list;
  private final ProjectControl.GenericFactory controlFactory;
  private final Provider<CurrentUser> user;

  @Inject
  ProjectsCollection(DynamicMap<RestView<ProjectResource>> views,
      Provider<ListProjects> list,
      ProjectControl.GenericFactory controlFactory,
      Provider<CurrentUser> user) {
    this.views = views;
    this.list = list;
    this.controlFactory = controlFactory;
    this.user = user;
  }

  @Override
  public RestView<TopLevelResource> list() {
    return list.get().setFormat(OutputFormat.JSON);
  }

  @Override
  public ProjectResource parse(TopLevelResource parent, String id)
      throws ResourceNotFoundException {
    ProjectControl ctl;
    try {
      ctl = controlFactory.controlFor(decode(id), user.get());
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(id);
    }
    if (!ctl.isVisible() && !ctl.isOwner()) {
      throw new ResourceNotFoundException(id);
    }
    return new ProjectResource(ctl);
  }

  private static Project.NameKey decode(String id) {
    try {
      return new Project.NameKey(URLDecoder.decode(id, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("JVM does not support UTF-8", e);
    }
  }

  @Override
  public DynamicMap<RestView<ProjectResource>> views() {
    return views;
  }
}
