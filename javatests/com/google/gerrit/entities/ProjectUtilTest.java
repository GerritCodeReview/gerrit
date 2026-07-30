// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.entities;

import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.ProjectUtil.InvalidProjectNameException;
import org.junit.Test;

public class ProjectUtilTest {

  @Test
  public void validateProjectNameSingleGitSuffix() {
    ProjectUtil.validateProjectName("project.git");
  }

  @Test
  public void validateProjectNameSingleGitSuffixAndFinalSlash() {
    ProjectUtil.validateProjectName("project.git/");
  }

  @Test
  public void validateProjectNameNoGitSuffix() {
    ProjectUtil.validateProjectName("project");
  }

  @Test
  public void validateProjectNameRejectsRepeatedGitSuffix() {
    assertThrows(
        InvalidProjectNameException.class,
        () -> ProjectUtil.validateProjectName("project.git.git"));
  }

  @Test
  public void validateProjectNameRejectsRepeatedGitSuffixAndFinalSlash() {
    assertThrows(
        InvalidProjectNameException.class,
        () -> ProjectUtil.validateProjectName("project.git.git/"));
  }
}
