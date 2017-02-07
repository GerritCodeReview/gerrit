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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GcAssert;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class GarbageCollectionIT extends AbstractDaemonTest {

  @Inject private GcAssert gcAssert;

  private Project.NameKey project2;

  @Before
  public void setUp() throws Exception {
    project2 = createProject("p2");
  }

  @Test
  public void testGcNonExistingProject_NotFound() throws Exception {
    POST("/projects/non-existing/gc").assertNotFound();
  }

  @Test
  public void testGcNotAllowed_Forbidden() throws Exception {
    userRestSession.post("/projects/" + allProjects.get() + "/gc").assertForbidden();
  }

  @Test
  @UseLocalDisk
  public void testGcOneProject() throws Exception {
    POST("/projects/" + allProjects.get() + "/gc").assertOK();
    gcAssert.assertHasPackFile(allProjects);
    gcAssert.assertHasNoPackFile(project, project2);
  }

  private RestResponse POST(String endPoint) throws Exception {
    RestResponse r = adminRestSession.post(endPoint);
    r.consume();
    return r;
  }
}
