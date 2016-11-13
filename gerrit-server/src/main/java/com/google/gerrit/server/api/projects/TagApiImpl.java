// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.api.projects;

import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.CreateTag;
import com.google.gerrit.server.project.ListTags;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;

public class TagApiImpl implements TagApi {
  interface Factory {
    TagApiImpl create(ProjectResource project, String ref);
  }

  private final ListTags listTags;
  private final CreateTag.Factory createTagFactory;
  private final String ref;
  private final ProjectResource project;

  @Inject
  TagApiImpl(
      ListTags listTags,
      CreateTag.Factory createTagFactory,
      @Assisted ProjectResource project,
      @Assisted String ref) {
    this.listTags = listTags;
    this.createTagFactory = createTagFactory;
    this.project = project;
    this.ref = ref;
  }

  @Override
  public TagApi create(TagInput input) throws RestApiException {
    try {
      createTagFactory.create(ref).apply(project, input);
      return this;
    } catch (IOException e) {
      throw new RestApiException("Cannot create tag", e);
    }
  }

  @Override
  public TagInfo get() throws RestApiException {
    try {
      return listTags.get(project, IdString.fromDecoded(ref));
    } catch (IOException e) {
      throw new RestApiException(e.getMessage());
    }
  }
}
