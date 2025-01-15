// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.extensions.common.PluginPushOption;
import com.google.gerrit.extensions.common.PluginPushOptionsInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.StreamSupport;

@Singleton
public class GetPluginPushOptions implements RestReadView<PluginResource> {
  private final DynamicSet<com.google.gerrit.server.git.receive.PluginPushOption> pluginPushOptions;

  @Inject
  GetPluginPushOptions(
      DynamicSet<com.google.gerrit.server.git.receive.PluginPushOption> pluginPushOption) {
    this.pluginPushOptions = pluginPushOption;
  }

  @Override
  public Response<PluginPushOptionsInfo> apply(PluginResource resource) {
    return Response.ok(
        new PluginPushOptionsInfo(
            StreamSupport.stream(
                    this.pluginPushOptions.entries().spliterator(), /* parallel= */ false)
                .map(Extension<com.google.gerrit.server.git.receive.PluginPushOption>::get)
                .map(o -> new PluginPushOption(o.getName(), o.getDescription()))
                .collect(toImmutableList())));
  }
}
