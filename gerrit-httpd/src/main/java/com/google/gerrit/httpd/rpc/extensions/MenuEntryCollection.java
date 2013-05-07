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

package com.google.gerrit.httpd.rpc.extensions;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.webui.TopMenuExtension;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class MenuEntryCollection implements
    RestCollection<TopLevelResource, TopMenuExtension> {
  private Provider<TopMenuExtensionList> listProvider;

  @Inject
  MenuEntryCollection(Provider<TopMenuExtensionList> listProvider) {
    this.listProvider = listProvider;
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException,
      AuthException {
    return listProvider.get();
  }

  @Override
  public TopMenuExtension parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    return null;
  }

  @Override
  public DynamicMap<RestView<TopMenuExtension>> views() {
    return null;
  }
}
