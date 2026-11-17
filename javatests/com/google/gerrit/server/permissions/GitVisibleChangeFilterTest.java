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

package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static com.google.gerrit.testing.TestActionRefUpdateContext.testRefAction;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.git.ChangesByProjectCache;
import com.google.gerrit.server.git.ChangesByProjectCache.ProjectChange;
import com.google.gerrit.server.notedb.AbstractChangeNotesTest;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestChanges;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public final class GitVisibleChangeFilterTest extends AbstractChangeNotesTest {
  private int nextChangeNum = 1001;

  private ChangesByProjectCache changesByProjectCache;
  private ChangeData.Factory changeDataFactory;
  private PermissionBackend.ForProject forProject;

  @Override
  public void setUpTestEnvironment() throws Exception {
    changesByProjectCache = mock(ChangesByProjectCache.class);
    changeDataFactory = mock(ChangeData.Factory.class);
    forProject = mock(PermissionBackend.ForProject.class);
    super.setUpTestEnvironment();
  }

  @Test
  public void nonPrivateChangesShortCircuitByBranchAndMemoizeBranchChecks() throws Exception {
    BranchNameKey mainBranch = BranchNameKey.create(project, "refs/heads/main");
    BranchNameKey featureBranch = BranchNameKey.create(project, "refs/heads/feature");
    BranchNameKey secretBranch = BranchNameKey.create(project, "refs/heads/secret");

    stubBranchReadable("refs/heads/main", true);
    stubBranchReadable("refs/heads/feature", true);
    stubBranchReadable("refs/heads/secret", false);

    CountingProjectChange c1 = CountingProjectChange.nonPrivate(Change.id(1001), mainBranch);
    CountingProjectChange c2 = CountingProjectChange.nonPrivate(Change.id(1002), mainBranch);
    CountingProjectChange c3 = CountingProjectChange.nonPrivate(Change.id(1003), mainBranch);
    CountingProjectChange c4 = CountingProjectChange.nonPrivate(Change.id(1004), secretBranch);
    CountingProjectChange c5 = CountingProjectChange.nonPrivate(Change.id(1005), secretBranch);
    CountingProjectChange c6 = CountingProjectChange.nonPrivate(Change.id(1006), featureBranch);
    CountingProjectChange unrequested =
        CountingProjectChange.nonPrivate(Change.id(1007), BranchNameKey.create(project, "other"));

    when(changesByProjectCache.streamProjectChanges(project, repo))
        .thenAnswer(unused -> Stream.of(c1, c2, c3, c4, c5, c6, unrequested));

    ImmutableMap<Change.Id, BranchNameKey> visible =
        GitVisibleChangeFilter.getVisibleChanges(
            changesByProjectCache,
            changeDataFactory,
            project,
            forProject,
            repo,
            ImmutableSet.of(c1.id(), c2.id(), c3.id(), c4.id(), c5.id(), c6.id()));

    assertThat(visible)
        .containsExactly(
            c1.id(), mainBranch,
            c2.id(), mainBranch,
            c3.id(), mainBranch,
            c6.id(), featureBranch);

    verifyNoInteractions(changeDataFactory);
    verify(forProject, never()).change(any(ChangeData.class));
    verify(forProject, times(1)).ref("refs/heads/main");
    verify(forProject, times(1)).ref("refs/heads/feature");
    verify(forProject, times(1)).ref("refs/heads/secret");
    verify(forProject, never()).ref("refs/heads/other");
    for (CountingProjectChange pc : new CountingProjectChange[] {c1, c2, c3, c4, c5, c6}) {
      assertThat(pc.toChangeDataCalls()).isEqualTo(0);
    }
  }

  @Test
  public void privateChangesShortCircuitOnUnreadableBranchAndEvaluateChangeReadOnReadableBranch()
      throws Exception {
    BranchNameKey mainBranch = BranchNameKey.create(project, "refs/heads/main");

    stubBranchReadable("refs/heads/main", true);
    stubBranchReadable("refs/heads/secret", false);

    ChangeData cdUnreadableBranch = createChangeData("refs/heads/secret", true);
    ChangeData cdVisiblePrivate = createChangeData("refs/heads/main", true);
    ChangeData cdHiddenPrivate = createChangeData("refs/heads/main", true);
    ChangeData cdErrorPrivate = createChangeData("refs/heads/main", true);

    CountingProjectChange privateOnUnreadable =
        CountingProjectChange.fromChangeData(cdUnreadableBranch);
    CountingProjectChange privateVisibleOnReadable =
        CountingProjectChange.fromChangeData(cdVisiblePrivate);
    CountingProjectChange privateHiddenOnReadable =
        CountingProjectChange.fromChangeData(cdHiddenPrivate);
    CountingProjectChange privateThrowingOnReadable =
        CountingProjectChange.fromChangeData(cdErrorPrivate);
    CountingProjectChange nonPrivateOnReadable =
        CountingProjectChange.nonPrivate(Change.id(1099), mainBranch);

    stubChangeReadable(cdVisiblePrivate, true);
    stubChangeReadable(cdHiddenPrivate, false);
    PermissionBackend.ForChange throwingForChange = mock(PermissionBackend.ForChange.class);
    when(throwingForChange.test(ChangePermission.READ))
        .thenThrow(new PermissionBackendException("backend failure"));
    when(forProject.change(cdErrorPrivate)).thenReturn(throwingForChange);

    when(changesByProjectCache.streamProjectChanges(project, repo))
        .thenAnswer(
            unused ->
                Stream.of(
                    privateOnUnreadable,
                    privateVisibleOnReadable,
                    privateHiddenOnReadable,
                    privateThrowingOnReadable,
                    nonPrivateOnReadable));

    ImmutableMap<Change.Id, BranchNameKey> visible =
        GitVisibleChangeFilter.getVisibleChanges(
            changesByProjectCache,
            changeDataFactory,
            project,
            forProject,
            repo,
            ImmutableSet.of(
                privateOnUnreadable.id(),
                privateVisibleOnReadable.id(),
                privateHiddenOnReadable.id(),
                privateThrowingOnReadable.id(),
                nonPrivateOnReadable.id()));

    assertThat(visible)
        .containsExactly(
            privateVisibleOnReadable.id(), mainBranch,
            nonPrivateOnReadable.id(), mainBranch);

    assertThat(privateOnUnreadable.toChangeDataCalls()).isEqualTo(0);
    assertThat(nonPrivateOnReadable.toChangeDataCalls()).isEqualTo(0);
    assertThat(privateVisibleOnReadable.toChangeDataCalls()).isEqualTo(1);
    assertThat(privateHiddenOnReadable.toChangeDataCalls()).isEqualTo(1);
    assertThat(privateThrowingOnReadable.toChangeDataCalls()).isEqualTo(1);

    verify(forProject, never()).change(cdUnreadableBranch);
    verify(forProject, times(1)).change(cdVisiblePrivate);
    verify(forProject, times(1)).change(cdHiddenPrivate);
    verify(forProject, times(1)).change(cdErrorPrivate);
    verify(forProject, times(1)).ref("refs/heads/main");
    verify(forProject, times(1)).ref("refs/heads/secret");
  }

  @Test
  public void directLoadingPathUnderThresholdLoadsOneByOneAndFiltersByBranchFirst()
      throws Exception {
    BranchNameKey mainBranch = BranchNameKey.create(project, "refs/heads/main");
    stubBranchReadable("refs/heads/main", true);
    stubBranchReadable("refs/heads/secret", false);

    ChangeData nonPrivateReadable = createChangeData("refs/heads/main", false);
    ChangeData nonPrivateUnreadable = createChangeData("refs/heads/secret", false);
    ChangeData privateVisible = createChangeData("refs/heads/main", true);
    Change.Id corruptId = Change.id(1999);

    when(changeDataFactory.create(project, nonPrivateReadable.getId()))
        .thenReturn(nonPrivateReadable);
    when(changeDataFactory.create(project, nonPrivateUnreadable.getId()))
        .thenReturn(nonPrivateUnreadable);
    when(changeDataFactory.create(project, privateVisible.getId())).thenReturn(privateVisible);
    when(changeDataFactory.create(project, corruptId))
        .thenThrow(new StorageException("missing change notes"));
    stubChangeReadable(privateVisible, true);

    ImmutableMap<Change.Id, BranchNameKey> visible =
        GitVisibleChangeFilter.getVisibleChanges(
            changesByProjectCache,
            changeDataFactory,
            project,
            forProject,
            repo,
            ImmutableSet.of(
                nonPrivateReadable.getId(),
                nonPrivateUnreadable.getId(),
                privateVisible.getId(),
                corruptId));

    assertThat(visible)
        .containsExactly(
            nonPrivateReadable.getId(), mainBranch,
            privateVisible.getId(), mainBranch);

    verifyNoInteractions(changesByProjectCache);
    verify(forProject, never()).change(nonPrivateReadable);
    verify(forProject, never()).change(nonPrivateUnreadable);
    verify(forProject, times(1)).change(privateVisible);
    verify(forProject, times(1)).ref("refs/heads/main");
    verify(forProject, times(1)).ref("refs/heads/secret");
  }

  @Test
  public void defaultStreamProjectChangesDelegatesToStreamChangeDatas() throws Exception {
    BranchNameKey mainBranch = BranchNameKey.create(project, "refs/heads/main");
    stubBranchReadable("refs/heads/main", true);
    stubBranchReadable("refs/heads/secret", false);

    ChangeData cd1 = createChangeData("refs/heads/main", false);
    ChangeData cd2 = createChangeData("refs/heads/main", false);
    ChangeData cd3 = createChangeData("refs/heads/secret", false);
    ChangeData cd4 = createChangeData("refs/heads/secret", true);
    ChangeData cd5 = createChangeData("refs/heads/main", true);
    stubChangeReadable(cd5, true);

    ChangesByProjectCache legacyCache = (proj, repository) -> Stream.of(cd1, cd2, cd3, cd4, cd5);

    ImmutableMap<Change.Id, BranchNameKey> visible =
        GitVisibleChangeFilter.getVisibleChanges(
            legacyCache,
            changeDataFactory,
            project,
            forProject,
            repo,
            ImmutableSet.of(cd1.getId(), cd2.getId(), cd3.getId(), cd4.getId(), cd5.getId()));

    assertThat(visible)
        .containsExactly(
            cd1.getId(), mainBranch,
            cd2.getId(), mainBranch,
            cd5.getId(), mainBranch);
    verifyNoInteractions(changeDataFactory);
    verify(forProject, times(1)).change(cd5);
    verify(forProject, never()).change(cd4);
  }

  @Test
  public void mismatchedDestProjectThrowsIllegalArgumentException() throws Exception {
    Project.NameKey otherProject = Project.nameKey("example-project");
    CountingProjectChange wrongProjectChange =
        CountingProjectChange.nonPrivate(
            Change.id(1001), BranchNameKey.create(otherProject, "refs/heads/main"));

    when(changesByProjectCache.streamProjectChanges(project, repo))
        .thenAnswer(unused -> Stream.of(wrongProjectChange));

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                GitVisibleChangeFilter.getVisibleChanges(
                    changesByProjectCache,
                    changeDataFactory,
                    project,
                    forProject,
                    repo,
                    ImmutableSet.of(
                        Change.id(1001),
                        Change.id(1002),
                        Change.id(1003),
                        Change.id(1004),
                        Change.id(1005))));

    assertThat(thrown)
        .hasMessageThat()
        .contains("expected change in project " + project + ", not " + otherProject);
  }

  @Test
  public void nullDestAndStorageExceptionsFailClosedWithoutAbortingHealthyChanges()
      throws Exception {
    BranchNameKey mainBranch = BranchNameKey.create(project, "refs/heads/main");
    stubBranchReadable("refs/heads/main", true);

    ProjectChange nullIdChange =
        new CustomProjectChange(
            null,
            () -> mainBranch,
            () -> false,
            () -> {
              throw new AssertionError("unexpected toChangeData");
            });
    ProjectChange nullDestChange =
        new CustomProjectChange(
            Change.id(1001),
            () -> null,
            () -> false,
            () -> {
              throw new AssertionError("unexpected toChangeData");
            });
    ProjectChange destStorageError =
        new CustomProjectChange(
            Change.id(1002),
            () -> {
              throw new StorageException("corrupt dest");
            },
            () -> false,
            () -> {
              throw new AssertionError("unexpected toChangeData");
            });
    ProjectChange isPrivateStorageError =
        new CustomProjectChange(
            Change.id(1003),
            () -> mainBranch,
            () -> {
              throw new StorageException("corrupt privacy flag");
            },
            () -> {
              throw new AssertionError("unexpected toChangeData");
            });
    CountingProjectChange healthy1 = CountingProjectChange.nonPrivate(Change.id(1004), mainBranch);
    CountingProjectChange healthy2 = CountingProjectChange.nonPrivate(Change.id(1005), mainBranch);

    when(changesByProjectCache.streamProjectChanges(project, repo))
        .thenAnswer(
            unused ->
                Stream.of(
                    nullIdChange,
                    nullDestChange,
                    destStorageError,
                    isPrivateStorageError,
                    healthy1,
                    healthy2));

    ImmutableMap<Change.Id, BranchNameKey> visible =
        GitVisibleChangeFilter.getVisibleChanges(
            changesByProjectCache,
            changeDataFactory,
            project,
            forProject,
            repo,
            ImmutableSet.of(
                Change.id(1001),
                Change.id(1002),
                Change.id(1003),
                Change.id(1004),
                Change.id(1005)));

    assertThat(visible)
        .containsExactly(
            Change.id(1004), mainBranch,
            Change.id(1005), mainBranch);
    verifyNoInteractions(changeDataFactory);
    verify(forProject, times(1)).ref("refs/heads/main");
  }

  private void stubBranchReadable(String branch, boolean readable) {
    PermissionBackend.ForRef forRef = mock(PermissionBackend.ForRef.class);
    when(forRef.testOrFalse(RefPermission.READ)).thenReturn(readable);
    when(forProject.ref(branch)).thenReturn(forRef);
  }

  private void stubChangeReadable(ChangeData cd, boolean readable)
      throws PermissionBackendException {
    PermissionBackend.ForChange forChange = mock(PermissionBackend.ForChange.class);
    when(forChange.test(ChangePermission.READ)).thenReturn(readable);
    when(forProject.change(cd)).thenReturn(forChange);
  }

  private ChangeData createChangeData(String branch, boolean isPrivate) throws Exception {
    int num = nextChangeNum++;
    Change change =
        testRefAction(
            () -> {
              Change c =
                  new Change(
                      Change.key(String.format("I%040d", num)),
                      Change.id(num),
                      changeOwner.getAccountId(),
                      BranchNameKey.create(project, branch),
                      TimeUtil.now());
              TestChanges.incrementPatchSet(c);
              ChangeUpdate u = newUpdate(injector, c, changeOwner, false);
              u.setChangeId(c.getKey().get());
              u.setBranch(c.getDest().branch());
              u.setPrivate(isPrivate);
              u.commit();
              return c;
            });
    ChangeNotes notes = newNotes(change);
    ChangeData cd =
        ChangeData.createForTest(
            project,
            change.getId(),
            Objects.requireNonNull(change.currentPatchSetId()).get(),
            ObjectId.zeroId(),
            null,
            changeNotesFactory,
            notes);
    cd.setChange(notes.getChange());
    cd.setReviewers(ReviewerSet.empty());
    return cd;
  }

  private static final class CountingProjectChange implements ProjectChange {
    private final Change.Id id;
    private final BranchNameKey dest;
    private final boolean isPrivate;
    @Nullable private final ChangeData changeData;
    private int toChangeDataCalls;

    private CountingProjectChange(
        Change.Id id, BranchNameKey dest, boolean isPrivate, @Nullable ChangeData changeData) {
      this.id = id;
      this.dest = dest;
      this.isPrivate = isPrivate;
      this.changeData = changeData;
    }

    static CountingProjectChange nonPrivate(Change.Id id, BranchNameKey dest) {
      return new CountingProjectChange(id, dest, false, null);
    }

    static CountingProjectChange fromChangeData(ChangeData cd) {
      return new CountingProjectChange(cd.getId(), cd.branchOrThrow(), cd.isPrivateOrThrow(), cd);
    }

    @Override
    public Change.Id id() {
      return id;
    }

    @Override
    public BranchNameKey dest() {
      return dest;
    }

    @Override
    public boolean isPrivate() {
      return isPrivate;
    }

    @Override
    public ChangeData toChangeData(ChangeData.Factory factory) {
      toChangeDataCalls++;
      if (changeData == null) {
        throw new AssertionError("Unexpected toChangeData call for non-private change " + id);
      }
      return changeData;
    }

    int toChangeDataCalls() {
      return toChangeDataCalls;
    }
  }

  private static final class CustomProjectChange implements ProjectChange {
    @Nullable private final Change.Id id;
    private final Supplier<BranchNameKey> destSupplier;
    private final Supplier<Boolean> isPrivateSupplier;
    private final Supplier<ChangeData> changeDataSupplier;

    CustomProjectChange(
        @Nullable Change.Id id,
        Supplier<BranchNameKey> destSupplier,
        Supplier<Boolean> isPrivateSupplier,
        Supplier<ChangeData> changeDataSupplier) {
      this.id = id;
      this.destSupplier = destSupplier;
      this.isPrivateSupplier = isPrivateSupplier;
      this.changeDataSupplier = changeDataSupplier;
    }

    @Override
    @Nullable
    public Change.Id id() {
      return id;
    }

    @Override
    @Nullable
    public BranchNameKey dest() {
      return destSupplier.get();
    }

    @Override
    public boolean isPrivate() {
      return isPrivateSupplier.get();
    }

    @Override
    public ChangeData toChangeData(ChangeData.Factory changeDataFactory) {
      return changeDataSupplier.get();
    }
  }
}
