// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.Arrays;

/** Helper to (de)serialize values for caches. */
public class ProjectSerializer {
  private static final Converter<String, ProjectState> PROJECT_STATE_CONVERTER =
      Enums.stringConverter(ProjectState.class);
  private static final Converter<String, SubmitType> SUBMIT_TYPE_CONVERTER =
      Enums.stringConverter(SubmitType.class);

  public static Project deserialize(Cache.ProjectProto proto) {
    Project.Builder builder =
        Project.builder(Project.nameKey(proto.getName()))
            .setSubmitType(SUBMIT_TYPE_CONVERTER.convert(proto.getSubmitType()))
            .setState(PROJECT_STATE_CONVERTER.convert(proto.getState()))
            .setDescription(emptyToNull(proto.getDescription()))
            .setParent(emptyToNull(proto.getParent()))
            .setMaxObjectSizeLimit(emptyToNull(proto.getMaxObjectSizeLimit()))
            .setDefaultDashboard(emptyToNull(proto.getDefaultDashboard()))
            .setLocalDefaultDashboard(emptyToNull(proto.getLocalDefaultDashboard()))
            .setConfigRefState(emptyToNull(proto.getConfigRefState()));

    ImmutableSet<String> configs =
        Arrays.stream(BooleanProjectConfig.values())
            .map(BooleanProjectConfig::name)
            .collect(toImmutableSet());
    proto
        .getBooleanConfigsMap()
        .entrySet()
        .forEach(
            configEntry -> {
              if (configs.contains(configEntry.getKey())) {
                builder.setBooleanConfig(
                    BooleanProjectConfig.valueOf(configEntry.getKey()),
                    InheritableBoolean.valueOf(configEntry.getValue()));
              }
            });

    return builder.build();
  }

  public static Cache.ProjectProto serialize(Project autoValue) {
    Cache.ProjectProto.Builder builder =
        Cache.ProjectProto.newBuilder()
            .setName(autoValue.getName())
            .setSubmitType(SUBMIT_TYPE_CONVERTER.reverse().convert(autoValue.getSubmitType()))
            .setState(PROJECT_STATE_CONVERTER.reverse().convert(autoValue.getState()))
            .setDescription(nullToEmpty(autoValue.getDescription()))
            .setParent(nullToEmpty(autoValue.getParentName()))
            .setMaxObjectSizeLimit(nullToEmpty(autoValue.getMaxObjectSizeLimit()))
            .setDefaultDashboard(nullToEmpty(autoValue.getDefaultDashboard()))
            .setLocalDefaultDashboard(nullToEmpty(autoValue.getLocalDefaultDashboard()))
            .setConfigRefState(nullToEmpty(autoValue.getConfigRefState()));

    autoValue
        .getBooleanConfigs()
        .entrySet()
        .forEach(
            configEntry -> {
              builder.putBooleanConfigs(configEntry.getKey().name(), configEntry.getValue().name());
            });

    return builder.build();
  }

  private ProjectSerializer() {}
}
