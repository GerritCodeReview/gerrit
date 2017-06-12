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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GcAssert;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GarbageCollectionQueue;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class GarbageCollectionIT extends AbstractDaemonTest {

  @Inject private GarbageCollection.Factory garbageCollectionFactory;

  @Inject private GarbageCollectionQueue gcQueue;

  @Inject private GcAssert gcAssert;

  private Project.NameKey project2;
  private Project.NameKey project3;

  @Before
  public void setUp() throws Exception {
    project2 = createProject("p2");
    project3 = createProject("p3");
  }

  @Test
  @UseLocalDisk
  public void testGc() throws Exception {
    String response =
        adminSshSession.exec("gerrit gc \"" + project.get() + "\" \"" + project2.get() + "\"");
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertNoError(response);
    gcAssert.assertHasPackFile(project, project2);
    gcAssert.assertHasNoPackFile(allProjects, project3);
  }

  @Test
  @UseLocalDisk
  public void testGcAll() throws Exception {
    String response = adminSshSession.exec("gerrit gc --all");
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertNoError(response);
    gcAssert.assertHasPackFile(allProjects, project, project2, project3);
  }

  @Test
  public void testGcWithoutCapability_Error() throws Exception {
    userSshSession.exec("gerrit gc --all");
    assertThat(userSshSession.hasError()).isTrue();
    String error = userSshSession.getError();
    assertThat(error).isNotNull();
    assertError(
        "One of the following capabilities is required to access this"
            + " resource: [runGC, maintainServer]",
        error);
  }

  @Test
  @UseLocalDisk
  public void testGcAlreadyScheduled() throws Exception {
    gcQueue.addAll(Arrays.asList(project));
    GarbageCollectionResult result =
        garbageCollectionFactory
            .create()
            .run(Arrays.asList(allProjects, project, project2, project3));
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).hasSize(1);
    GarbageCollectionResult.Error error = result.getErrors().get(0);
    assertThat(error.getType()).isEqualTo(GarbageCollectionResult.Error.Type.GC_ALREADY_SCHEDULED);
    assertThat(error.getProjectName()).isEqualTo(project);
  }

  private void assertError(String expectedError, String response) {
    assertThat(response).contains(expectedError);
  }

  private void assertNoError(String response) {
    assertThat(response.toLowerCase(Locale.US)).doesNotContain("error");
  }
}
