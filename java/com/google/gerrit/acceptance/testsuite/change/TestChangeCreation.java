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

package com.google.gerrit.acceptance.testsuite.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.edit.tree.TreeModification;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.merge.MergeStrategy;

/** Initial attributes of the change. If not provided, arbitrary values will be used. */
@AutoValue
public abstract class TestChangeCreation {
  public abstract Optional<Project.NameKey> project();

  public abstract String branch();

  public abstract Optional<Account.Id> owner();

  public abstract Optional<Account.Id> author();

  public abstract Optional<PersonIdent> authorIdent();

  public abstract Optional<Account.Id> committer();

  public abstract Optional<PersonIdent> committerIdent();

  public abstract Optional<String> topic();

  public abstract ImmutableMap<String, Short> approvals();

  public abstract String commitMessage();

  public abstract ImmutableList<TreeModification> treeModifications();

  public abstract Optional<ImmutableList<TestCommitIdentifier>> parents();

  public abstract MergeStrategy mergeStrategy();

  abstract ThrowingFunction<TestChangeCreation, Change.Id> changeCreator();

  public static Builder builder(ThrowingFunction<TestChangeCreation, Change.Id> changeCreator) {
    return new AutoValue_TestChangeCreation.Builder()
        .changeCreator(changeCreator)
        .branch(Constants.R_HEADS + Constants.MASTER)
        .commitMessage("A test change")
        // Which value we choose here doesn't matter. All relevant code paths set the desired value.
        .mergeStrategy(MergeStrategy.OURS)
        .approvals(ImmutableMap.of());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /** Target project/Repository of the change. Must be an existing project. */
    public abstract Builder project(Project.NameKey project);

    /**
     * Target branch of the change. Neither needs to exist nor needs to point to an actual commit.
     */
    public abstract Builder branch(String branch);

    /**
     * The change owner.
     *
     * <p>Must be an existing user account.
     */
    public abstract Builder owner(Account.Id owner);

    /**
     * The author of the commit for which the change is created.
     *
     * <p>Must be an existing user account.
     *
     * <p>Cannot be set together with {@link #authorIdent()} is set.
     *
     * <p>If neither {@link #author()} nor {@link #authorIdent()} is set the {@link
     * TestChangeCreation#owner()} is used as the author.
     */
    public abstract Builder author(Account.Id author);

    /**
     * The author ident of the commit for which the change is created.
     *
     * <p>Cannot be set together with {@link #author()} is set.
     *
     * <p>If neither {@link #author()} nor {@link #authorIdent()} is set the {@link
     * TestChangeCreation#owner()} is used as the author.
     */
    public abstract Builder authorIdent(PersonIdent authorIdent);

    public abstract Optional<Account.Id> author();

    public abstract Optional<PersonIdent> authorIdent();

    /**
     * The committer of the commit for which the change is created.
     *
     * <p>Must be an existing user account.
     *
     * <p>Cannot be set together with {@link #committerIdent()} is set.
     *
     * <p>If neither {@link #committer()} nor {@link #committerIdent()} is set the {@link
     * TestChangeCreation#owner()} is used as the committer.
     */
    public abstract Builder committer(Account.Id committer);

    /**
     * The committer ident of the commit for which the change is created.
     *
     * <p>Cannot be set together with {@link #committer()} is set.
     *
     * <p>If neither {@link #committer()} nor {@link #committerIdent()} is set the {@link
     * TestChangeCreation#owner()} is used as the committer.
     */
    public abstract Builder committerIdent(PersonIdent committerIdent);

    public abstract Optional<Account.Id> committer();

    public abstract Optional<PersonIdent> committerIdent();

    /** The topic to add this change to. */
    public abstract Builder topic(String topic);

    /**
     * The approvals to apply to this change. Map of label name to value. All approvals will be
     * granted by the uploader.
     */
    public abstract Builder approvals(ImmutableMap<String, Short> approvals);

    /**
     * The commit message. The message may contain a {@code Change-Id} footer but does not need to.
     * If the footer is absent, it will be generated.
     */
    public abstract Builder commitMessage(String commitMessage);

    /** Modified file of the change. The file content is specified via the returned builder. */
    public FileContentBuilder<Builder> file(String filePath) {
      return new FileContentBuilder<>(this, filePath, 0, treeModificationsBuilder()::add);
    }

    /**
     * Modified file of the change. The file content is specified via the returned builder. The
     * second parameter indicates the git file mode for the modified file if it has been changed.
     *
     * @see org.eclipse.jgit.lib.FileMode
     */
    public FileContentBuilder<Builder> file(String filePath, int newGitFileMode) {
      return new FileContentBuilder<>(
          this, filePath, newGitFileMode, treeModificationsBuilder()::add);
    }

    abstract ImmutableList.Builder<TreeModification> treeModificationsBuilder();

    /**
     * Parent commit of the change. The commit can be specified via various means in the returned
     * builder.
     */
    public ParentBuilder<Builder> childOf() {
      return new ParentBuilder<>(parentCommit -> parents(ImmutableList.of(parentCommit)));
    }

    /**
     * Parent commits of the change. Each parent commit can be specified via various means in the
     * returned builder. The order of the parents matters and is preserved (first parent commit in
     * fluent change -> first parent of the change).
     *
     * <p>This method will automatically merge the parent commits and use the resulting commit as
     * base for the change. Use {@link #file(String)} for additional file adjustments on top of that
     * merge commit.
     *
     * <p><strong>Note:</strong> If this method fails with a merge conflict, use {@link
     * #mergeOfButBaseOnFirst()} instead and specify all other necessary file contents manually via
     * {@link #file(String)}.
     */
    public ParentBuilder<MultipleParentBuilder<Builder>> mergeOf() {
      return new ParentBuilder<>(parent -> mergeBuilder(MergeStrategy.RECURSIVE, parent));
    }

    /**
     * Parent commits of the change. Each parent commit can be specified via various means in the
     * returned builder. The order of the parents matters and is preserved (first parent commit in
     * fluent change -> first parent of the change).
     *
     * <p>This method will use the first specified parent commit as base for the resulting change.
     * This approach is especially useful if merging the parents is not possible.
     */
    public ParentBuilder<MultipleParentBuilder<Builder>> mergeOfButBaseOnFirst() {
      return new ParentBuilder<>(parent -> mergeBuilder(MergeStrategy.OURS, parent));
    }

    MultipleParentBuilder<Builder> mergeBuilder(
        MergeStrategy mergeStrategy, TestCommitIdentifier parent) {
      mergeStrategy(mergeStrategy);
      return new MultipleParentBuilder<>(this::parents, parent);
    }

    abstract Builder parents(ImmutableList<TestCommitIdentifier> parents);

    abstract Builder mergeStrategy(MergeStrategy mergeStrategy);

    abstract Builder changeCreator(ThrowingFunction<TestChangeCreation, Change.Id> changeCreator);

    abstract TestChangeCreation autoBuild();

    public TestChangeCreation build() {
      checkState(
          author().isEmpty() || authorIdent().isEmpty(),
          "author and authorIdent cannot be set together");
      checkState(
          committer().isEmpty() || committerIdent().isEmpty(),
          "committer and committerIdent cannot be set together");
      return autoBuild();
    }

    /**
     * Creates the change.
     *
     * @return the {@code Change.Id} of the created change
     */
    public Change.Id create() {
      TestChangeCreation changeUpdate = build();
      return changeUpdate.changeCreator().applyAndThrowSilently(changeUpdate);
    }
  }
}
