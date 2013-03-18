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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectState;

import java.util.List;
import java.util.Set;

public class ProjectAssert {

  public static void assertProjects(Iterable<Project.NameKey> expected,
      List<ProjectInfo> actual) {
    for (final Project.NameKey p : expected) {
      ProjectInfo info = Iterables.find(actual, new Predicate<ProjectInfo>() {
        @Override
        public boolean apply(ProjectInfo info) {
          return new Project.NameKey(info.name).equals(p);
        }}, null);
      assertNotNull("missing project: " + p, info);
      actual.remove(info);
    }
    assertTrue("unexpected projects: " + actual, actual.isEmpty());
  }

  public static void assertProjectInfo(Project project, ProjectInfo info) {
    if (info.name != null) {
      // 'name' is not set if returned in a map
      assertEquals(project.getName(), info.name);
    }
    assertEquals(project.getName(), Url.decode(info.id));
    Project.NameKey parentName = project.getParent(new Project.NameKey("All-Projects"));
    assertEquals(parentName != null ? parentName.get() : null, info.parent);
    assertEquals(project.getDescription(), Strings.nullToEmpty(info.description));
  }

  public static void assertProjectOwners(Set<AccountGroup.UUID> expectedOwners,
      ProjectState state) {
    for (AccountGroup.UUID g : state.getOwners()) {
      assertTrue("unexpected owner group " + g, expectedOwners.remove(g));
    }
    assertTrue("missing owner groups: " + expectedOwners,
        expectedOwners.isEmpty());
  }
}
