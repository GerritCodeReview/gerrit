// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementTemplateResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Locale;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class SubmitRequirementTemplatesCollection
    implements ChildCollection<ProjectResource, SubmitRequirementTemplateResource> {
  private final SubmitRequirementTemplateLoader templateLoader;
  private final DynamicMap<RestView<SubmitRequirementTemplateResource>> views;
  private final Provider<ListSubmitRequirementTemplates> list;

  @Inject
  SubmitRequirementTemplatesCollection(
      SubmitRequirementTemplateLoader templateLoader,
      DynamicMap<RestView<SubmitRequirementTemplateResource>> views,
      Provider<ListSubmitRequirementTemplates> list) {
    this.templateLoader = templateLoader;
    this.views = views;
    this.list = list;
  }

  @Override
  public RestView<ProjectResource> list() throws RestApiException {
    return list.get();
  }

  @Override
  public SubmitRequirementTemplateResource parse(ProjectResource parent, IdString id)
      throws AuthException,
          ResourceNotFoundException,
          PermissionBackendException,
          IOException,
          ConfigInvalidException {
    SubmitRequirementTemplateResource resource =
        templateLoader.load(parent).get(id.get().toLowerCase(Locale.US));
    if (resource == null) {
      throw new ResourceNotFoundException(
          String.format("Submit requirement template '%s' does not exist", id));
    }
    return resource;
  }

  @Override
  public DynamicMap<RestView<SubmitRequirementTemplateResource>> views() {
    return views;
  }
}
