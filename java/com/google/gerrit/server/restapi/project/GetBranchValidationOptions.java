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

package com.google.gerrit.server.restapi.project;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.extensions.common.ValidationOptionInfo;
import com.google.gerrit.extensions.common.ValidationOptionInfos;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PluginName;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.PluginPushOption;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.StreamSupport;

@Singleton
public class GetBranchValidationOptions implements RestReadView<BranchResource> {
  private final DynamicSet<PluginPushOption> pluginPushOption;

  @Inject
  GetBranchValidationOptions(DynamicSet<PluginPushOption> pluginPushOption) {
    this.pluginPushOption = pluginPushOption;
  }

  @Override
  public Response<ValidationOptionInfos> apply(BranchResource resource) {
    return Response.ok(
        new ValidationOptionInfos(
            StreamSupport.stream(
                    this.pluginPushOption.entries().spliterator(), /* parallel= */ false)
                .filter(
                    extension ->
                        extension
                            .get()
                            .isOptionEnabled(
                                resource.getProjectState().getNameKey(), resource.getBranchKey()))
                .map(
                    extension ->
                        new ValidationOptionInfo(
                            PluginName.GERRIT.equals(extension.getPluginName())
                                ? extension.get().getName()
                                : String.format(
                                    "%s~%s", extension.getPluginName(), extension.get().getName()),
                            extension.get().getDescription()))
                .collect(toImmutableList())));
  }
}
