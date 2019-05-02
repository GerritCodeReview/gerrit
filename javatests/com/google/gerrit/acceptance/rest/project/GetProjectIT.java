// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.common.LabelTypeInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import org.junit.Test;

@NoHttpd
public class GetProjectIT extends AbstractDaemonTest {

  @Test
  public void getProject() throws Exception {
    String name = project.get();
    ProjectInfo p = gApi.projects().name(name).get();
    assertThat(p.name).isEqualTo(name);

    assertThat(p.labels).hasSize(1);
    LabelTypeInfo l = p.labels.get("Code-Review");

    ImmutableMap<String, String> want =
        ImmutableMap.of(
            " 0", "No score",
            "-1", "I would prefer this is not merged as is",
            "-2", "This shall not be merged",
            "+1", "Looks good to me, but someone else must approve",
            "+2", "Looks good to me, approved");
    assertThat(l.values).isEqualTo(want);
    assertThat(l.defaultValue).isEqualTo(0);
  }

  @Test
  public void getProjectWithGitSuffix() throws Exception {
    String name = project.get();
    ProjectInfo p = gApi.projects().name(name).get();
    assertThat(p.name).isEqualTo(name);
  }

  @Test
  public void getProjectNotExisting() throws Exception {
    assertThrows(
        ResourceNotFoundException.class, () -> gApi.projects().name("does-not-exist").get());
  }
}
