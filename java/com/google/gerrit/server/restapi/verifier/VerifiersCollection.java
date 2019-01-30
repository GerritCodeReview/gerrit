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

package com.google.gerrit.server.restapi.verifier;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
public class VerifiersCollection implements RestCollection<TopLevelResource, VerifierResource> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Verifiers verifiers;
  private final DynamicMap<RestView<VerifierResource>> views;

  @Inject
  public VerifiersCollection(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      Verifiers verifiers,
      DynamicMap<RestView<VerifierResource>> views) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.verifiers = verifiers;
    this.views = views;
  }

  @Override
  public RestView<TopLevelResource> list() throws RestApiException {
    throw new ResourceNotFoundException();
  }

  @Override
  public VerifierResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException(id);
    }

    try {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_VERIFIERS);
    } catch (AuthException e) {
      throw new ResourceNotFoundException(id);
    }

    Optional<Verifier> verifier = verifiers.get(id.get());
    if (!verifier.isPresent()) {
      throw new ResourceNotFoundException(id);
    }

    return new VerifierResource(verifier.get());
  }

  @Override
  public DynamicMap<RestView<VerifierResource>> views() {
    return views;
  }
}
