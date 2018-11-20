// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.project;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

public class ProjectOperationsImplTest extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;

  @Test
  public void defaultName() throws Exception {
    Project.NameKey name = projectOperations.newProject().create();
    // check that the project was created (throws exception if not found.)
    gApi.projects().name(name.get());
    Project.NameKey name2 = projectOperations.newProject().create();
    assertThat(name2).isNotEqualTo(name);
  }

  @Test
  public void specifiedName() throws Exception {
    String name = "somename";
    Project.NameKey key = projectOperations.newProject().name(name).create();
    assertThat(key.get()).isEqualTo(name);
  }

  @Test
  public void emptyCommit() throws Exception {
    Project.NameKey key = projectOperations.newProject().create();
    List<BranchInfo> branches = gApi.projects().name(key.get()).branches().get();
    assertThat(branches).isNotEmpty();
    assertThat(branches.stream().map(x -> x.ref).collect(toList()))
        .isEqualTo(ImmutableList.of("HEAD", "refs/meta/config", "refs/heads/master"));
  }
}
