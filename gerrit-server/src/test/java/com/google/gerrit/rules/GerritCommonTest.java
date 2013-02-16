// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.AbstractModule;

import java.util.Arrays;
import java.util.Set;

public class GerritCommonTest extends PrologTestCase {
  private ProjectState project;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    LabelTypes types =
        new LabelTypes(Arrays.asList(
            category(0, "CRVW", "Code-Review",
                value(2, "Looks good to me, approved"),
                value(1, "Looks good to me, but someone else must approve"),
                value(0, "No score"),
                value(-1, "I would prefer that you didn't submit this"),
                value(-2, "Do not submit")),
            category(1, "VRIF", "Verified", value(1, "Verified"),
                value(0, "No score"), value(-1, "Fails"))));
    ProjectConfig config = new ProjectConfig(new Project.NameKey("myproject"));
    config.createInMemory();
    project = new ProjectState(null, null, null, null, null,
        null, types, config);

    load("gerrit", "gerrit_common_test.pl", new AbstractModule() {
      @Override
      protected void configure() {
        bind(ProjectCache.class).toInstance(new Projects(project));
      }
    });
  }

  @Override
  protected void setUpEnvironment(PrologEnvironment env) {
    env.set(StoredValues.CHANGE, new Change(
        new Change.Key("Ibeef"), new Change.Id(1), new Account.Id(2),
        new Branch.NameKey(project.getProject().getNameKey(), "master")));
  }

  private static LabelValue value(int value, String text) {
    return new LabelValue((short) value, text);
  }

  private static LabelType category(int pos, String id, String name,
      LabelValue... values) {
    LabelType type = new LabelType(id, name, Arrays.asList(values));
    type.setPosition((short) pos);
    return type;
  }

  private static class Projects implements ProjectCache {
    private final ProjectState project;

    private Projects(ProjectState project) {
      this.project = project;
    }

    @Override
    public ProjectState getAllProjects() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ProjectState get(Project.NameKey projectName) {
      assertEquals(project.getProject().getNameKey(), projectName);
      return project;
    }

    @Override
    public void evict(Project p) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void remove(Project p) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<Project.NameKey> all() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<UUID> guessRelevantGroupUUIDs() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<Project.NameKey> byName(String prefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onCreateProject(Project.NameKey newProjectName) {
      throw new UnsupportedOperationException();
    }
  }
}
