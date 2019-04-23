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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

public class AllProjectsNameTest {
  @Test
  public void equalToProjectNameKey() {
    String name = "a-project";
    AllProjectsName allProjectsName = new AllProjectsName(name);
    Project.NameKey projectName = Project.nameKey(name);
    assertThat(allProjectsName.get()).isEqualTo(projectName.get());
    assertThat(allProjectsName).isEqualTo(projectName);
  }

  @Test
  public void equalToAllUsersName() {
    String name = "a-project";
    AllProjectsName allProjectsName = new AllProjectsName(name);
    AllUsersName allUsersName = new AllUsersName(name);
    assertThat(allProjectsName.get()).isEqualTo(allUsersName.get());
    assertThat(allProjectsName).isEqualTo(allUsersName);
  }
}
