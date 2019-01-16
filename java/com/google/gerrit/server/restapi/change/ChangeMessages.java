// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.change.ChangeMessageResource;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ChangeMessages implements ChildCollection<ChangeResource, ChangeMessageResource> {
  private final DynamicMap<RestView<ChangeMessageResource>> views;
  private final ListChangeMessages listChangeMessages;

  @Inject
  ChangeMessages(
      DynamicMap<RestView<ChangeMessageResource>> views, ListChangeMessages listChangeMessages) {
    this.views = views;
    this.listChangeMessages = listChangeMessages;
  }

  @Override
  public DynamicMap<RestView<ChangeMessageResource>> views() {
    return views;
  }

  @Override
  public ListChangeMessages list() {
    return listChangeMessages;
  }

  @Override
  public ChangeMessageResource parse(ChangeResource parent, IdString id)
      throws StorageException, ResourceNotFoundException, PermissionBackendException {
    String uuid = id.get();

    List<ChangeMessageInfo> changeMessages = listChangeMessages.apply(parent);
    int index = -1;
    for (int i = 0; i < changeMessages.size(); ++i) {
      ChangeMessageInfo changeMessage = changeMessages.get(i);
      if (changeMessage.id.equals(uuid)) {
        index = i;
        break;
      }
    }

    if (index < 0) {
      throw new ResourceNotFoundException(String.format("change message %s not found", uuid));
    }

    return new ChangeMessageResource(parent, changeMessages.get(index), index);
  }
}
