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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.account.AccountResource.Capability;
import com.google.inject.Inject;
import com.google.inject.Provider;

class Capabilities implements
    ChildCollection<AccountResource, AccountResource.Capability> {
  private final DynamicMap<RestView<AccountResource.Capability>> views;
  private final Provider<GetCapabilities> get;

  @Inject
  Capabilities(
      DynamicMap<RestView<AccountResource.Capability>> views,
      Provider<GetCapabilities> get) {
    this.views = views;
    this.get = get;
  }

  @Override
  public GetCapabilities list() throws ResourceNotFoundException {
    return get.get();
  }

  @Override
  public Capability parse(AccountResource parent, String id)
      throws ResourceNotFoundException, Exception {
    CapabilityControl cap = parent.getUser().getCapabilities();
    if (cap.canPerform(id)
        || (cap.canAdministrateServer() && GlobalCapability.isCapability(id))) {
      return new AccountResource.Capability(parent.getUser(), id);
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<Capability>> views() {
    return views;
  }
}
