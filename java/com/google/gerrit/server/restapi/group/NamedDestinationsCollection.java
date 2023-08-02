// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.restapi.group;

import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.NamedDestinationResource;
import com.google.inject.Inject;

public class NamedDestinationsCollection
    implements ChildCollection<GroupResource, NamedDestinationResource> {
  private final DynamicMap<RestView<NamedDestinationResource>> views;

  @Inject
  NamedDestinationsCollection(DynamicMap<RestView<NamedDestinationResource>> views) {
    this.views = views;
  }

  @Override
  public RestView<GroupResource> list() throws RestApiException {
    throw new UnsupportedOperationException("Cannot list destinations yet");
  }

  @Override
  public NamedDestinationResource parse(GroupResource parent, IdString id) throws Exception {
    if (id.isEmpty()) {
      throw new BadRequestException("Named destination cannot be empty");
    }
    if (!(parent.getGroup() instanceof GroupDescription.Internal)) {
      throw new NotInternalGroupException();
    }
    return new NamedDestinationResource(parent, id.get());
  }

  @Override
  public DynamicMap<RestView<NamedDestinationResource>> views() {
    return views;
  }
}
