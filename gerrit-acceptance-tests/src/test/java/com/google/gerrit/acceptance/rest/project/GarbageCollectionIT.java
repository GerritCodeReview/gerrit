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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GcAssert;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GarbageCollectionIT extends AbstractDaemonTest {

  @Inject
  private GcAssert gcAssert;

  private Project.NameKey project2;

  @Before
  public void setUp() throws Exception {
    project2 = createProject("p2");
  }

  @Test
  public void testGcNonExistingProject_NotFound() throws Exception {
    assertThat(POST("/projects/non-existing/gc")).isEqualTo(
        HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void testGcNotAllowed_Forbidden() throws Exception {
    assertThat(
        userSession.post("/projects/" + allProjects.get() + "/gc")
            .getStatusCode()).isEqualTo(HttpStatus.SC_FORBIDDEN);
  }

  @Test
  @UseLocalDisk
  public void testGcOneProject() throws Exception {
    assertThat(POST("/projects/" + allProjects.get() + "/gc")).isEqualTo(
        HttpStatus.SC_OK);
    gcAssert.assertHasPackFile(allProjects);
    gcAssert.assertHasNoPackFile(project, project2);
  }

  private int POST(String endPoint) throws IOException {
    RestResponse r = adminSession.post(endPoint);
    r.consume();
    return r.getStatusCode();
  }
}
