// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class TagsCollection implements
    ChildCollection<ProjectResource, TagResource> {
  private final DynamicMap<RestView<TagResource>> views;
  private final ListTags list;

  @Inject
  public TagsCollection(DynamicMap<RestView<TagResource>> views,
     ListTags list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    return list;
  }

  @Override
  public TagResource parse(ProjectResource resource, IdString id)
      throws ResourceNotFoundException, IOException {
    return new TagResource(resource.getControl(), list.get(resource, id));
  }

  @Override
  public DynamicMap<RestView<TagResource>> views() {
    return views;
  }
}
