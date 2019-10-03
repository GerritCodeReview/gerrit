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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class FileBranchIT extends AbstractDaemonTest {

  private BranchNameKey branch;

  @Before
  public void setUp() throws Exception {
    branch = BranchNameKey.create(project, "master");
    PushOneCommit.Result change = createChange();
    approve(change.getChangeId());
    revision(change).submit();
  }

  @Test
  public void getFileContent() throws Exception {
    BinaryResult content = branch().file(PushOneCommit.FILE_NAME);
    assertThat(content.asString()).isEqualTo(PushOneCommit.FILE_CONTENT);
  }

  @Test
  public void getNonExistingFile() throws Exception {
    assertThrows(ResourceNotFoundException.class, () -> branch().file("does-not-exist"));
  }

  private BranchApi branch() throws Exception {
    return gApi.projects().name(branch.project().get()).branch(branch.branch());
  }
}
