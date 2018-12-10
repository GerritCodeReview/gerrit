// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.project;

import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Operations for constructing projects in tests. This does not necessarily use the project REST
 * API, so don't use it for testing that.
 */
public interface ProjectOperations {

  /** Starts a fluent chain for creating a new project. */
  TestProjectCreation.Builder newProject();

  PerProjectOperations project(Project.NameKey key);

  interface PerProjectOperations {
    /**
     * Returns the commit for this project. branchName can either be shortened ("HEAD", "master") or
     * a fully qualified refname ("refs/heads/master"). The branch must exist.
     */
    RevCommit getHead(String branchName);

    /**
     * Returns if a branch exists. branchName can either be shortened ("HEAD", "master") or a fully
     * qualified refname ("refs/heads/master"). The branch must exist.
     */
    boolean hasHead(String branchName);
  }
}
