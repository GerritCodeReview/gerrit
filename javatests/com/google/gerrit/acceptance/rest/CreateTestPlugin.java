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

package com.google.gerrit.acceptance.rest;

import static com.google.gerrit.acceptance.rest.TestPluginModule.PLUGIN_CAPABILITY;

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Singleton;

@RequiresCapability(PLUGIN_CAPABILITY)
@Singleton
public class CreateTestPlugin
    implements RestCollectionCreateView<ConfigResource, PluginResource, CreateTestPlugin.Input> {

  public static class Input {
    public String input;
  }

  @Override
  public Object apply(ConfigResource parentResource, IdString id, Input input) throws Exception {
    return Response.created(input);
  }
}
