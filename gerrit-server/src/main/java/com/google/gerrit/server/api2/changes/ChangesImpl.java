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

package com.google.gerrit.server.api2.changes;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api2.changes.ChangeApi;
import com.google.gerrit.extensions.api2.changes.Changes;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

public class ChangesImpl implements Changes {
  private final ChangesCollection changes;
  private final ChangeApiImpl.Factory api;

  @Inject
  ChangesImpl(ChangesCollection changes, ChangeApiImpl.Factory api) {
    this.changes = changes;
    this.api = api;
  }

  @Override
  public ChangeApi id(int id) throws RestApiException {
    return id(String.valueOf(id));
  }

  @Override
  public ChangeApi id(String project, String branch, String id)
      throws RestApiException {
    return id(Joiner.on('~').join(ImmutableList.of(
        Url.encode(project),
        Url.encode(branch),
        Url.encode(id))));
  }

  @Override
  public ChangeApi id(String id) throws RestApiException {
    try {
      return api.create(changes.parse(
          TopLevelResource.INSTANCE,
          IdString.fromUrl(id)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot parse change", e);
    }
  }
}
