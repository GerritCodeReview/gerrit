// Copyright (C) 2013 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.query.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

@Ignore
public abstract class AbstractQueryChangesTest {
  @Inject private AccountManager accountManager;
  @Inject private ChangeInserter.Factory changeFactory;
  @Inject private CreateProject.Factory projectFactory;
  @Inject private IdentifiedUser.RequestFactory userFactory;
  @Inject private InMemoryDatabase schemaFactory;
  @Inject private InMemoryRepositoryManager repoManager;
  @Inject private ProjectControl.GenericFactory projectControlFactory;
  @Inject private Provider<QueryChanges> queryProvider;
  @Inject private SchemaCreator schemaCreator;
  @Inject private ThreadLocalRequestContext requestContext;

  private LifecycleManager lifecycle;
  private ReviewDb db;
  private Account.Id user;

  protected abstract Injector createInjector();

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = createInjector();
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    user = accountManager.authenticate(AuthRequest.forUser("user"))
        .getAccountId();

    requestContext.setContext(new RequestContext() {
      @Override
      public CurrentUser getCurrentUser() {
        return userFactory.create(user);
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    });
  }

  @After
  public void tearDownInjector() {
    lifecycle.stop();
    requestContext.setContext(null);
    db.close();
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void byId() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(repo.commit().message("message").create());
    Change change = new Change(
        new Change.Key("I1234567890123456789012345678901234567890"),
        new Change.Id(db.nextChangeId()),
        user,
        new Branch.NameKey(new Project.NameKey("repo"), "refs/heads/master"));
    ChangeInserter ins = changeFactory.create(
        refControl("repo", change),
        change,
        commit);
    ins.insert();

    int id = change.getId().get();
    ChangeInfo result = queryOne(Integer.toString(id));
    assertEquals(id, result._number);
  }

  private TestRepository<InMemoryRepository> createProject(String name) throws Exception {
    CreateProject create = projectFactory.create(name);
    create.apply(TopLevelResource.INSTANCE, new CreateProject.Input());
    return new TestRepository<InMemoryRepository>(
        repoManager.openRepository(new Project.NameKey(name)));
  }

  private RefControl refControl(String repo, Change change)
      throws NoSuchProjectException, IOException {
        return projectControlFactory.controlFor(
            new Project.NameKey(repo),
            userFactory.create(user)).controlFor(change).getRefControl();
      }

  private ChangeInfo queryOne(String query) throws Exception {
    QueryChanges q = queryProvider.get();
    q.addQuery(query);
    Object result = q.apply(TopLevelResource.INSTANCE);
    assertTrue(String.format("expected List<ChangeInfo>, found %s", result),
        result instanceof List);
    @SuppressWarnings("rawtypes")
    List results = (List) result;
    assertTrue(String.format(
        "expected singleton List<ChangeInfo>, found %s", result),
        results.size() == 1);
    Object ci = results.get(0);
    assertTrue(String.format("expected ChangeInfo, found %s", result),
        ci instanceof ChangeInfo);
    return (ChangeInfo) ci;
  }
}
