package com.google.gerrit.server.git;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.BatchUpdate.RepoOnlyOp;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BatchUpdateTest {
  @Inject private AccountManager accountManager;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Inject private InMemoryDatabase schemaFactory;

  @Inject private InMemoryRepositoryManager repoManager;

  @Inject private SchemaCreator schemaCreator;

  @Inject private ThreadLocalRequestContext requestContext;

  @Inject private BatchUpdate.Factory batchUpdateFactory;

  private LifecycleManager lifecycle;
  private ReviewDb db;
  private TestRepository<InMemoryRepository> repo;
  private Project.NameKey project;
  private IdentifiedUser user;

  @Before
  public void setUp() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    Account.Id userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    user = userFactory.create(userId);

    project = new Project.NameKey("test");

    InMemoryRepository inMemoryRepo = repoManager.createRepository(project);
    repo = new TestRepository<>(inMemoryRepo);

    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });
  }

  @After
  public void tearDown() {
    if (repo != null) {
      repo.getRepository().close();
    }
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void addRefUpdateFromFastForwardCommit() throws Exception {
    final RevCommit masterCommit = repo.branch("master").commit().create();
    final RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();

    try (BatchUpdate bu = batchUpdateFactory.create(db, project, user, TimeUtil.nowTs())) {
      bu.addRepoOnlyOp(
          new RepoOnlyOp() {
            @Override
            public void updateRepo(RepoContext ctx) throws Exception {
              ctx.addRefUpdate(
                  new ReceiveCommand(
                      masterCommit.getId(), branchCommit.getId(), "refs/heads/master"));
            }
          });
      bu.execute();
    }

    assertEquals(
        repo.getRepository().exactRef("refs/heads/master").getObjectId(), branchCommit.getId());
  }
}
