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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.SubmitType;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;

@AutoValue
public abstract class TestProjectCreation {

  public abstract Optional<String> name();

  public abstract Optional<Project.NameKey> parent();

  public abstract ImmutableSet<String> branches();

  public abstract Optional<Boolean> createEmptyCommit();

  public abstract Optional<Boolean> permissionOnly();

  public abstract Optional<SubmitType> submitType();

  public abstract ImmutableSet<AccountGroup.UUID> owners();

  abstract ThrowingFunction<TestProjectCreation, Project.NameKey> projectCreator();

  public static Builder builder(
      ThrowingFunction<TestProjectCreation, Project.NameKey> projectCreator) {
    return new AutoValue_TestProjectCreation.Builder()
        .branches(Constants.R_HEADS + Constants.MASTER)
        .projectCreator(projectCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract TestProjectCreation.Builder name(String name);

    public abstract TestProjectCreation.Builder parent(Project.NameKey parent);

    public abstract TestProjectCreation.Builder submitType(SubmitType submitType);

    /**
     * Branches which should be created in the repository (with an empty root commit). The
     * "refs/heads/" prefix of the branch name can be omitted. The specified branches are ignored if
     * {@link #noEmptyCommit()} is used.
     */
    @CanIgnoreReturnValue
    public TestProjectCreation.Builder branches(String branch1, String... otherBranches) {
      return branches(Sets.union(ImmutableSet.of(branch1), ImmutableSet.copyOf(otherBranches)));
    }

    abstract TestProjectCreation.Builder branches(Set<String> branches);

    public abstract TestProjectCreation.Builder createEmptyCommit(boolean value);

    public abstract TestProjectCreation.Builder permissionOnly(boolean value);

    /** Skips the empty commit on creation. This means that project's branches will not exist. */
    @CanIgnoreReturnValue
    public TestProjectCreation.Builder noEmptyCommit() {
      return createEmptyCommit(false);
    }

    @CanIgnoreReturnValue
    public TestProjectCreation.Builder addOwner(AccountGroup.UUID owner) {
      ownersBuilder().add(requireNonNull(owner, "owner"));
      return this;
    }

    abstract ImmutableSet.Builder<AccountGroup.UUID> ownersBuilder();

    abstract TestProjectCreation.Builder projectCreator(
        ThrowingFunction<TestProjectCreation, Project.NameKey> projectCreator);

    abstract TestProjectCreation autoBuild();

    /**
     * Executes the project creation as specified.
     *
     * @return the name of the created project
     */
    @CanIgnoreReturnValue
    public Project.NameKey create() {
      TestProjectCreation creation = autoBuild();
      return creation.projectCreator().applyAndThrowSilently(creation);
    }
  }
}
