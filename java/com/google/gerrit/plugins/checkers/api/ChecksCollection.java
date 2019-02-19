// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checkers.api;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.checkers.Check;
import com.google.gerrit.plugins.checkers.CheckKey;
import com.google.gerrit.plugins.checkers.Checks;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class ChecksCollection implements ChildCollection<RevisionResource, CheckResource> {

  private final Checks checks;
  private final DynamicMap<RestView<CheckResource>> views;

  @Inject
  ChecksCollection(Checks checks, DynamicMap<RestView<CheckResource>> views) {
    this.checks = checks;
    this.views = views;
  }

  @Override
  public RestView<RevisionResource> list() throws RestApiException {
    return (RestReadView<RevisionResource>)
        resource -> checks.getChecks(resource.getProject(), resource.getPatchSet().getId());
  }

  @Override
  public CheckResource parse(RevisionResource parent, IdString id)
      throws AuthException, ResourceNotFoundException, PermissionBackendException, IOException,
          OrmException {
    CheckKey checkKey =
        CheckKey.create(parent.getProject(), parent.getPatchSet().getId(), id.get());
    Optional<Check> check = checks.getCheck(checkKey);
    if (!check.isPresent()) {
      throw new ResourceNotFoundException("Not found: " + id.get());
    }

    return new CheckResource(parent, check.get());
  }

  @Override
  public DynamicMap<RestView<CheckResource>> views() {
    return views;
  }
}
