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

package com.google.gerrit.acceptance.ssh;

import static com.google.gerrit.acceptance.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GcAssert;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GarbageCollectionQueue;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;

@NoHttpd
public class GarbageCollectionIT extends AbstractDaemonTest {

  @Inject
  private GarbageCollection.Factory garbageCollectionFactory;

  @Inject
  private GarbageCollectionQueue gcQueue;

  @Inject
  private GcAssert gcAssert;

  private Project.NameKey project2;
  private Project.NameKey project3;

  @Before
  public void setUp() throws Exception {
    project2 = new Project.NameKey("p2");
    createProject(sshSession, project2.get());

    project3 = new Project.NameKey("p3");
    createProject(sshSession, project3.get());
  }

  @Test
  @UseLocalDisk
  public void testGc() throws Exception {
    String response =
        sshSession.exec("gerrit gc \"" + project.get() + "\" \""
            + project2.get() + "\"");
    assertFalse(sshSession.getError(), sshSession.hasError());
    assertNoError(response);
    gcAssert.assertHasPackFile(project, project2);
    gcAssert.assertHasNoPackFile(allProjects, project3);
  }

  @Test
  @UseLocalDisk
  public void testGcAll() throws Exception {
    String response = sshSession.exec("gerrit gc --all");
    assertFalse(sshSession.getError(), sshSession.hasError());
    assertNoError(response);
    gcAssert.assertHasPackFile(allProjects, project, project2, project3);
  }

  @Test
  public void testGcWithoutCapability_Error() throws Exception {
    SshSession s = new SshSession(server, user);
    s.exec("gerrit gc --all");
    assertError("Capability runGC is required to access this resource", s.getError());
    s.close();
  }

  @Test
  @UseLocalDisk
  public void testGcAlreadyScheduled() throws Exception {
    gcQueue.addAll(Arrays.asList(project));
    GarbageCollectionResult result = garbageCollectionFactory.create().run(
        Arrays.asList(allProjects, project, project2, project3));
    assertTrue(result.hasErrors());
    assertEquals(1, result.getErrors().size());
    GarbageCollectionResult.Error error = result.getErrors().get(0);
    assertEquals(GarbageCollectionResult.Error.Type.GC_ALREADY_SCHEDULED, error.getType());
    assertEquals(project, error.getProjectName());
  }

  private void assertError(String expectedError, String response) {
    assertTrue(response, response.contains(expectedError));
  }

  private void assertNoError(String response) {
    assertFalse(response, response.toLowerCase(Locale.US).contains("error"));
  }
}
