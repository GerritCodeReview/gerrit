/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.acceptance.testsuite.change;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.edit.tree.TreeModification;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;

/** Initial attributes of the change. If not provided, arbitrary values will be used. */
@AutoValue
public abstract class TestChangeCreation {
  public abstract Optional<Project.NameKey> project();

  public abstract String branch();

  public abstract Optional<Account.Id> owner();

  public abstract String commitMessage();

  public abstract ImmutableList<TreeModification> treeModifications();

  abstract ThrowingFunction<TestChangeCreation, Change.Id> changeCreator();

  public static Builder builder(ThrowingFunction<TestChangeCreation, Change.Id> changeCreator) {
    return new AutoValue_TestChangeCreation.Builder()
        .changeCreator(changeCreator)
        .branch(Constants.R_HEADS + Constants.MASTER)
        .commitMessage("A test change");
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Target project/Repository of the change. Must be an existing project. */
    public abstract Builder project(Project.NameKey project);

    /**
     * Target branch of the change. Neither needs to exist nor needs to point to an actual commit.
     */
    public abstract Builder branch(String branch);

    /** The change owner. Must be an existing user account. */
    public abstract Builder owner(Account.Id owner);

    /**
     * The commit message. The message may contain a {@code Change-Id} footer but does not need to.
     * If the footer is absent, it will be generated.
     */
    public abstract Builder commitMessage(String commitMessage);

    /** Modified file of the change. The file content is specified via the returned builder. */
    public FileContentBuilder<Builder> file(String filePath) {
      return new FileContentBuilder<>(this, filePath, treeModificationsBuilder()::add);
    }

    abstract ImmutableList.Builder<TreeModification> treeModificationsBuilder();

    abstract Builder changeCreator(ThrowingFunction<TestChangeCreation, Change.Id> changeCreator);

    abstract TestChangeCreation autoBuild();

    /**
     * Creates the change.
     *
     * @return the {@code Change.Id} of the created change
     */
    public Change.Id create() {
      TestChangeCreation changeUpdate = autoBuild();
      return changeUpdate.changeCreator().applyAndThrowSilently(changeUpdate);
    }
  }
}
