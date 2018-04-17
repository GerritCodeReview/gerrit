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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.IterableSubject;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectAccessor;
import java.util.List;
import java.util.Set;

public class ProjectAssert {
  public static IterableSubject assertThatNameList(Iterable<ProjectInfo> actualIt) {
    List<ProjectInfo> actual = ImmutableList.copyOf(actualIt);
    for (ProjectInfo info : actual) {
      assertWithMessage("missing project name").that(info.name).isNotNull();
      assertWithMessage("project name does not match id")
          .that(Url.decode(info.id))
          .isEqualTo(info.name);
    }
    return assertThat(Iterables.transform(actual, p -> new Project.NameKey(p.name)));
  }

  public static void assertProjectInfo(Project project, ProjectInfo info) {
    if (info.name != null) {
      // 'name' is not set if returned in a map
      assertThat(info.name).isEqualTo(project.getName());
    }
    assertThat(Url.decode(info.id)).isEqualTo(project.getName());
    Project.NameKey parentName = project.getParent(new Project.NameKey("All-Projects"));
    if (parentName != null) {
      assertThat(info.parent).isEqualTo(parentName.get());
    } else {
      assertThat(info.parent).isNull();
    }
    assertThat(Strings.nullToEmpty(info.description)).isEqualTo(project.getDescription());
  }

  public static void assertProjectOwners(
      Set<AccountGroup.UUID> expectedOwners, ProjectAccessor accessor) {
    for (AccountGroup.UUID g : accessor.getOwners()) {
      assertThat(expectedOwners.remove(g)).isTrue();
    }
    assertThat(expectedOwners).isEmpty();
  }
}
