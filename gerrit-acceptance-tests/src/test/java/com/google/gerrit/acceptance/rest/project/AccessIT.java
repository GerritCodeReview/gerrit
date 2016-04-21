// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class AccessIT extends AbstractDaemonTest {

  @Inject
  private AllProjectsName allProjectsName;

  @Test
  public void testGetAccessForAllProjects() throws Exception {

    //gApi.projects().name("").access()
     assertThat("").isEqualTo("");

  }

  @Test
  public void testGetDefaultInheritance() throws Exception {
    // create proj
    String newProjectName = name("newProjectAccess");
    RestResponse r = adminSession.put("/projects/" + newProjectName);
    String inheritedName = gApi.projects().name(newProjectName).access().inheritsFrom.name;
    assertThat(inheritedName).isEqualTo(allProjectsName.get());
  }

}
