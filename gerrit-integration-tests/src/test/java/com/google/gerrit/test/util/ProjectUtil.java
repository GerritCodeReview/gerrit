// Copyright (C) 2011 The Android Open Source Project
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
package com.google.gerrit.test.util;

import com.google.gerrit.test.GerritSshInterface;

import java.util.List;

public class ProjectUtil {

  /**
   * Generates a project name TestProject<N> e.g. TestProject7.
   * N will be the lowest number >1 with: TestProject<N> does not exist
   * @param ssh
   * @return project name
   * @throws Exception
   */
  public static String calcTestProjectName(GerritSshInterface ssh) throws Exception {
    String baseName = "TestProject";
    int index = 1;
    String projectName = baseName + index;
    List<String> projects = ssh.listProjects();
    while (projects.contains(projectName)) {
      projectName = baseName + ++index;
    }
    return projectName;
  }

}
