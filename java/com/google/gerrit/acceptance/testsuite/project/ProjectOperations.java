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

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Operations for constructing projects in tests. This does not necessarily use the project REST
 * API, so don't use it for testing that.
 */
public interface ProjectOperations {

  /** Starts a fluent chain for creating a new project. */
  TestProjectCreation.Builder newProject();

  PerProjectOperations project(Project.NameKey key);

  /** Starts a fluent chain for updating All-Projects. */
  TestProjectUpdate.Builder allProjectsForUpdate();

  interface PerProjectOperations {
    /**
     * Returns the commit for this project. branchName can either be shortened ("HEAD", "master") or
     * a fully qualified refname ("refs/heads/master"). The branch must exist.
     */
    RevCommit getHead(String branchName);

    /**
     * Returns true if a branch exists. branchName can either be shortened ("HEAD", "master") or a
     * fully qualified refname ("refs/heads/master").
     */
    boolean hasHead(String branchName);

    /** Returns a fresh {@link ProjectConfig} read from the tip of {@code refs/meta/config}. */
    ProjectConfig getProjectConfig();

    /**
     * Returns a fresh JGit {@link Config} instance read from {@code project.config} at the tip of
     * {@code refs/meta/config}. Does not have a base config, i.e. does not respect {@code
     * $site_path/etc/project.config}.
     */
    Config getConfig();

    /**
     * Starts the fluent chain to update a project. The returned builder can be used to specify how
     * the attributes of the project should be modified. To update the project for real, the {@link
     * TestProjectUpdate.Builder#update()} must be called.
     *
     * <p>Example:
     *
     * <pre>
     * projectOperations
     *     .forUpdate()
     *     .add(allow(ABANDON).ref("refs/*").group(REGISTERED_USERS))
     *     .update();
     * </pre>
     *
     * @return a builder to update the check.
     */
    TestProjectUpdate.Builder forUpdate();
  }
}
