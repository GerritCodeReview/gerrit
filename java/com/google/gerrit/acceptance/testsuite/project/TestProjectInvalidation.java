// Copyright (C) 2020 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.Config;

/**
 * API to invalidate projects in tests.
 *
 * <p>This allows to test Gerrit behavior when there is invalid project data in NoteDb (e.g. an
 * invalid {@code project.config} file).
 */
@AutoValue
public abstract class TestProjectInvalidation {
  public abstract boolean makeProjectConfigInvalid();

  public abstract ImmutableList<Consumer<Config>> projectConfigUpdater();

  abstract ThrowingConsumer<TestProjectInvalidation> projectInvalidator();

  public static Builder builder(ThrowingConsumer<TestProjectInvalidation> projectInvalidator) {
    return new AutoValue_TestProjectInvalidation.Builder()
        .projectInvalidator(projectInvalidator)
        .makeProjectConfigInvalid(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Updates the project.config file so that it becomes invalid and loading it within Gerrit fails
     * with {@link org.eclipse.jgit.errors.ConfigInvalidException}.
     */
    public Builder makeProjectConfigInvalid() {
      makeProjectConfigInvalid(true);
      return this;
    }

    protected abstract Builder makeProjectConfigInvalid(boolean makeProjectConfigInvalid);

    /**
     * Adds a consumer that can update the project config.
     *
     * <p>This allows tests to set arbitrary values in the project config.
     */
    public Builder addProjectConfigUpdater(Consumer<Config> projectConfigUpdater) {
      projectConfigUpdaterBuilder().add(projectConfigUpdater);
      return this;
    }

    protected abstract ImmutableList.Builder<Consumer<Config>> projectConfigUpdaterBuilder();

    abstract Builder projectInvalidator(
        ThrowingConsumer<TestProjectInvalidation> projectInvalidator);

    abstract TestProjectInvalidation autoBuild();

    /** Executes the project invalidation as specified. */
    public void invalidate() {
      TestProjectInvalidation projectInvalidation = autoBuild();
      projectInvalidation.projectInvalidator().acceptAndThrowSilently(projectInvalidation);
    }
  }
}
