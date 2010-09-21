// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.PerformCreateProjectImpl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;

public class CreateNewProject extends Handler<VoidResult> {

  interface Factory {
    CreateNewProject create(@Assisted("projectName") String projectName,
        @Assisted("parentName") String parentName);
  }

  @Inject
  private PerformCreateProjectImpl.Factory performCreateProjectFactory;

  private final String projectName;
  private final String parentName;

  @Inject
  public CreateNewProject(@Assisted("projectName") String projectName,
      @Assisted("parentName") String parentName) {
    this.projectName = projectName;
    this.parentName = parentName;
  }

  @Override
  public VoidResult call() throws Exception {
    PerformCreateProject performCreateProject =
      performCreateProjectFactory.create(projectName, null, parentName, "",
            SubmitType.MERGE_IF_NECESSARY, false, false, false, Constants.MASTER);

    StringBuilder err = null;
    try {
      err = performCreateProject.createProject();
    } catch (Exception e) {
      throw new Exception("Error when trying to create project: " + e.getMessage());
    }

    if (err != null && err.length() > 0) {
      throw new Exception(err.toString());
    }

    return VoidResult.INSTANCE;
  }

}
