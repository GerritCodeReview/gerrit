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

public class AllUsersNameTest {
  @Test
  public void equalToProjectNameKey() {
    String name = "a-project";
    AllUsersName allUsersName = new AllUsersName(name);
    Project.NameKey projectName = new Project.NameKey(name);
    assertThat(allUsersName.get()).isEqualTo(projectName.get());
    assertThat(allUsersName).isEqualTo(projectName);
  }

  @Test
  public void equalToAllProjectsName() {
    String name = "a-project";
    AllUsersName allUsersName = new AllUsersName(name);
    AllProjectsName allProjectsName = new AllProjectsName(name);
    assertThat(allUsersName.get()).isEqualTo(allProjectsName.get());
    assertThat(allUsersName).isEqualTo(allProjectsName);
  }
}
