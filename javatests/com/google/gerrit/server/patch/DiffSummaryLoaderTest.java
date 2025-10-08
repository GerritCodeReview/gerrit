package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.patch.CommitUtil.createCommit;
import static com.google.gerrit.server.patch.CommitUtil.createMergeCommit;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.CommitUtil.FileEntity;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Test class for {@link DiffSummaryLoader}. */
public class DiffSummaryLoaderTest {
  private static final Project.NameKey testProjectName = Project.nameKey("test-project");
  @Inject private DiffSummaryLoader.Factory diffSummaryLoaderFactory;
  @Inject private GitRepositoryManager repoManager;
  private Repository repo;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    repo = repoManager.createRepository(testProjectName);
  }

  @Test
  public void mergeCommit_diffAgainst_parent1() throws Exception {
    ObjectId parent1 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_1.txt", "file 1 content")));
    ObjectId parent2 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_2.txt", "file 2 content")));

    ObjectId merge =
        createMergeCommit(
            repo,
            ImmutableList.of(
                new FileEntity("file_1.txt", "file 1 content"),
                new FileEntity("file_2.txt", "file 2 content")),
            parent1,
            parent2);

    PatchListKey pk = PatchListKey.againstBase(merge, 2);
    DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
    DiffSummaryLoader loader = diffSummaryLoaderFactory.create(key, testProjectName);
    DiffSummary diffSummary = loader.call();
    assertThat(diffSummary.getPaths()).containsExactly("file_2.txt");
  }

  @Test
  public void mergeCommit_diffAgainst_autoMerge() throws Exception {
    ObjectId parent1 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_1.txt", "file 1 content")));
    ObjectId parent2 =
        createCommit(repo, null, ImmutableList.of(new FileEntity("file_2.txt", "file 2 content")));

    ObjectId merge =
        createMergeCommit(
            repo,
            ImmutableList.of(
                new FileEntity("file_1.txt", "file 1 content"),
                new FileEntity("file_2.txt", "file 2 content")),
            parent1,
            parent2);

    PatchListKey pk = PatchListKey.againstDefaultBase(merge, Whitespace.IGNORE_NONE);
    DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
    DiffSummaryLoader loader = diffSummaryLoaderFactory.create(key, testProjectName);
    DiffSummary diffSummary = loader.call();
    assertThat(diffSummary.getPaths()).isEmpty();
  }

  @Test
  public void nonMergeCommit_diffAgainst_parent() throws Exception {
    String fileName1 = "file_1.txt";
    String fileContent1 = "File content 1";
    String fileName2 = "file_2.txt";
    String fileContent2 = "File content 2";
    ImmutableList<FileEntity> oldFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1), new FileEntity(fileName2, fileContent2));
    ObjectId oldCommitId = createCommit(repo, null, oldFiles);

    ImmutableList<FileEntity> newFiles =
        ImmutableList.of(
            new FileEntity(fileName1, fileContent1),
            new FileEntity(fileName2, fileContent2 + "\nnew line here"));
    ObjectId newCommitId = createCommit(repo, oldCommitId, newFiles);

    PatchListKey pk = PatchListKey.againstBase(newCommitId, 1);
    DiffSummaryKey key = DiffSummaryKey.fromPatchListKey(pk);
    DiffSummaryLoader loader = diffSummaryLoaderFactory.create(key, testProjectName);
    DiffSummary diffSummary = loader.call();
    assertThat(diffSummary.getPaths()).containsExactly("file_2.txt");

    pk = PatchListKey.againstCommit(oldCommitId, newCommitId, Whitespace.IGNORE_NONE);
    key = DiffSummaryKey.fromPatchListKey(pk);
    assertThat(diffSummaryLoaderFactory.create(key, testProjectName).call().getPaths())
        .containsExactly("file_2.txt");
  }
}
