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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.cache.serialize.entities.ProjectSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.ProjectSerializer.serialize;

import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import org.junit.Test;

public class ProjectSerializerTest {
  static final Project ALL_VALUES_SET =
      Project.builder(Project.nameKey("test"))
          .setDescription("desc")
          .setSubmitType(SubmitType.FAST_FORWARD_ONLY)
          .setState(ProjectState.HIDDEN)
          .setParent(Project.nameKey("parent"))
          .setMaxObjectSizeLimit("11K")
          .setDefaultDashboard("dashboard1")
          .setLocalDefaultDashboard("dashboard2")
          .setConfigRefState("1337")
          .setBooleanConfig(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL, InheritableBoolean.TRUE)
          .setBooleanConfig(
              BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
              InheritableBoolean.INHERIT)
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(ALL_VALUES_SET))).isEqualTo(ALL_VALUES_SET);
  }

  @Test
  public void roundTripWithMinimalValues() {
    Project projectAutoValue =
        Project.builder(Project.nameKey("test"))
            .setSubmitType(SubmitType.FAST_FORWARD_ONLY)
            .setState(ProjectState.HIDDEN)
            .build();

    assertThat(deserialize(serialize(projectAutoValue))).isEqualTo(projectAutoValue);
  }
}
