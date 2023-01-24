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
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.edit.tree.TreeModification;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;

/** Initial attributes of the patchset. If not provided, arbitrary values will be used. */
@AutoValue
public abstract class TestPatchsetCreation {

  public abstract Optional<Account.Id> uploader();

  public abstract Optional<Account.Id> author();

  public abstract Optional<PersonIdent> authorIdent();

  public abstract Optional<Account.Id> committer();

  public abstract Optional<PersonIdent> committerIdent();

  public abstract Optional<String> commitMessage();

  public abstract ImmutableList<TreeModification> treeModifications();

  public abstract Optional<ImmutableList<TestCommitIdentifier>> parents();

  abstract ThrowingFunction<TestPatchsetCreation, PatchSet.Id> patchsetCreator();

  public static TestPatchsetCreation.Builder builder(
      ThrowingFunction<TestPatchsetCreation, PatchSet.Id> patchsetCreator) {
    return new AutoValue_TestPatchsetCreation.Builder().patchsetCreator(patchsetCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * The uploader for the new patch set.
     *
     * <p>Must be an existing user account.
     *
     * <p>If not set the new patch set is uploaded by the change owner.
     */
    public abstract Builder uploader(Account.Id uploader);

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

    public abstract Builder commitMessage(String commitMessage);

    /** Modified file of the patchset. The file content is specified via the returned builder. */
    public FileContentBuilder<Builder> file(String filePath) {
      return new FileContentBuilder<>(this, filePath, 0, treeModificationsBuilder()::add);
    }

    /** Modified file of the patchset. The file content is specified via the returned builder. */
    public FileContentBuilder<Builder> file(String filePath, int newGitFileMode) {
      return new FileContentBuilder<>(
          this, filePath, newGitFileMode, treeModificationsBuilder()::add);
    }

    abstract ImmutableList.Builder<TreeModification> treeModificationsBuilder();

    /**
     * Parent commit of the change. The commit can be specified via various means in the returned
     * builder.
     *
     * <p>This method will just change the parent but not influence the contents of the patchset
     * commit.
     *
     * <p>It's possible to switch from a change representing a merge commit to a change not being a
     * merge commit with this method.
     */
    public ParentBuilder<Builder> parent() {
      return new ParentBuilder<>(parent -> parents(ImmutableList.of(parent)));
    }

    /**
     * Parent commits of the change. Each parent commit can be specified via various means in the
     * returned builder. The order of the parents matters and is preserved (first parent commit in
     * fluent change -> first parent of the change).
     *
     * <p>This method will just change the parents but not influence the contents of the patchset
     * commit.
     *
     * <p>It's possible to switch from a change representing a non-merge commit to a change which is
     * a merge commit with this method.
     */
    public ParentBuilder<MultipleParentBuilder<Builder>> parents() {
      return new ParentBuilder<>(parent -> new MultipleParentBuilder<>(this::parents, parent));
    }

    abstract Builder parents(ImmutableList<TestCommitIdentifier> value);

    abstract TestPatchsetCreation.Builder patchsetCreator(
        ThrowingFunction<TestPatchsetCreation, PatchSet.Id> patchsetCreator);

    abstract TestPatchsetCreation autoBuild();

    public TestPatchsetCreation build() {
      checkState(
          author().isEmpty() || authorIdent().isEmpty(),
          "author and authorIdent cannot be set together");
      checkState(
          committer().isEmpty() || committerIdent().isEmpty(),
          "committer and committerIdent cannot be set together");
      return autoBuild();
    }

    /**
     * Creates the patchset.
     *
     * @return the {@code PatchSet.Id} of the created patchset
     */
    public PatchSet.Id create() {
      TestPatchsetCreation patchsetCreation = build();
      return patchsetCreation.patchsetCreator().applyAndThrowSilently(patchsetCreation);
    }
  }
}
