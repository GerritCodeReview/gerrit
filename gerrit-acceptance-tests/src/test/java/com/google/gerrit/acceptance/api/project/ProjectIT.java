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

package com.google.gerrit.acceptance.api.project;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.RestApiException;

import org.junit.Test;

@NoHttpd
public class ProjectIT extends AbstractDaemonTest  {

  @Test
  public void createBranch() throws RestApiException {
    gApi.projects()
        .name(project.get())
        .branch("foo")
        .create(new BranchInput());
  }
}
