package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.update.Submission.Preparation;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Map;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SubmissionExecutorTest {
  private static final int MAX_UPDATES = 4;
  private static final int MAX_PATCH_SETS = 3;

  @Rule
  public InMemoryTestEnvironment testEnvironment =
      new InMemoryTestEnvironment(
          () -> {
            Config cfg = new Config();
            cfg.setInt("change", null, "maxFiles", 2);
            cfg.setInt("change", null, "maxPatchSets", MAX_PATCH_SETS);
            cfg.setInt("change", null, "maxUpdates", MAX_UPDATES);
            return cfg;
          });

  @Inject private BatchUpdate.Factory batchUpdateFactory;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private GitRepositoryManager origRepoManager;
  @Inject private PatchSetInserter.Factory patchSetInserterFactory;
  @Inject private Provider<CurrentUser> user;
  @Inject private Sequences sequences;

  private TestGitRepositoryManager repoManager;

  @Inject
  private @Named("diff_summary") Cache<DiffSummaryKey, DiffSummary> diffSummaryCache;

  private Project.NameKey project;
  private TestRepository<Repository> repo;

  private Project.NameKey project2;
  private TestRepository<Repository> repo2;

  @Before
  public void setUp() throws Exception {

    repoManager = new TestGitRepositoryManager(origRepoManager);

    project = Project.nameKey("test");
    Repository inMemoryRepo = repoManager.createRepository(project);
    repo = new TestRepository<>(inMemoryRepo);

    project2 = Project.nameKey("test2");
    Repository inMemoryRepo2 = repoManager.createRepository(project2);
    repo2 = new TestRepository<>(inMemoryRepo2);
  }

  @Test
  public void submissionMethodsInvokedOneRepo() throws Exception {
    try (BatchUpdate bu = updateRepo(project, repo)) {
      SubmissionExecutor executor = new SubmissionExecutor(repoManager);
      executor.execute(ImmutableList.of(bu), BatchUpdateListener.NONE, false);
    }

    assertThat(repoManager.preparation.prepareForCalls).isEqualTo(1);
    assertThat(repoManager.preparation.finished).isTrue();
    assertThat(repoManager.submission.attachToCalls).isEqualTo(1);
    assertThat(repoManager.submission.finished).isTrue();
  }

  @Test
  public void submissionMethodsInvokedTwoRepos() throws Exception {
    try (BatchUpdate bu = updateRepo(project, repo);
        BatchUpdate bu2 = updateRepo(project2, repo2)) {
      SubmissionExecutor executor = new SubmissionExecutor(repoManager);
      executor.execute(ImmutableList.of(bu, bu2), BatchUpdateListener.NONE, false);
    }

    assertThat(repoManager.preparation.prepareForCalls).isEqualTo(2);
    assertThat(repoManager.preparation.finished).isTrue();
    assertThat(repoManager.submission.attachToCalls).isEqualTo(2);
    assertThat(repoManager.submission.finished).isTrue();
  }

  private BatchUpdate updateRepo(Project.NameKey project, TestRepository<Repository> repo)
      throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();

    BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs());
    bu.addRepoOnlyOp(
        new RepoOnlyOp() {
          @Override
          public void updateRepo(RepoContext ctx) throws Exception {
            ctx.addRefUpdate(masterCommit.getId(), branchCommit.getId(), "refs/heads/master");
          }
        });
    return bu;
  }

  // Wrapper of GitRepositoryManager to return (and hold references) to instrumented submission.
  private static class TestGitRepositoryManager implements GitRepositoryManager {

    private GitRepositoryManager repoManager;
    private InstrumentedSubmission submission;
    private InstrumentedSubmissionPreparation preparation;

    TestGitRepositoryManager(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
      this.submission = new InstrumentedSubmission();
      this.preparation = new InstrumentedSubmissionPreparation(submission);
    }

    @Override
    public Repository openRepository(NameKey name) throws RepositoryNotFoundException, IOException {
      return repoManager.openRepository(name);
    }

    @Override
    public Repository createRepository(NameKey name)
        throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
      return repoManager.createRepository(name);
    }

    @Override
    public SortedSet<NameKey> list() {
      return repoManager.list();
    }

    @Override
    public Preparation newSubmission() {
      return preparation;
    }
  }

  static class InstrumentedSubmission implements Submission {
    int attachToCalls;
    boolean finished;

    @Override
    public BatchRefUpdate attachTo(BatchRefUpdate bru) {
      assertThat(finished).isFalse();
      attachToCalls += 1;
      return bru;
    }

    @Override
    public void finish() {
      assertThat(finished).isFalse();
      finished = true;
    }
  }

  static class InstrumentedSubmissionPreparation implements Submission.Preparation {

    int prepareForCalls;
    boolean finished;
    private InstrumentedSubmission submission;

    public InstrumentedSubmissionPreparation(InstrumentedSubmission submission) {
      this.submission = submission;
    }

    @Override
    public void prepareFor(Repository repository, Map<String, ReceiveCommand> refUpdates) {
      assertThat(finished).isFalse();
      prepareForCalls += 1;
    }

    @Override
    public Submission finish() {
      assertThat(finished).isFalse();
      finished = true;
      return submission;
    }
  }
}
