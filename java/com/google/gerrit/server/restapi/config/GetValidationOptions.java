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

package com.google.gerrit.server.restapi.config;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.extensions.common.ValidationOptionInfo;
import com.google.gerrit.extensions.common.ValidationOptionInfos;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.receive.PluginPushOption;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.StreamSupport;

@Singleton
public class GetValidationOptions implements RestReadView<ConfigResource> {
  private final DynamicSet<PluginPushOption> pluginPushOption;

  @Inject
  GetValidationOptions(DynamicSet<PluginPushOption> pluginPushOption) {
    this.pluginPushOption = pluginPushOption;
  }

  @Override
  public Response<ValidationOptionInfos> apply(ConfigResource resource) {
    return Response.ok(
        new ValidationOptionInfos(
            StreamSupport.stream(
                    this.pluginPushOption.entries().spliterator(), /* parallel= */ false)
                .map(Extension<PluginPushOption>::get)
                .map(o -> new ValidationOptionInfo(o.getName(), o.getDescription()))
                .collect(toImmutableList())));
  }
}
