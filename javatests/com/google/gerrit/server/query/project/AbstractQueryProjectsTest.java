// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.query.project;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.base.CharMatcher;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.Projects.QueryRequest;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testing.GerritServerTests;
import com.google.gerrit.testing.InMemoryDatabase;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public abstract class AbstractQueryProjectsTest extends GerritServerTests {
  @Inject protected Accounts accounts;

  @Inject @ServerInitiated protected Provider<AccountsUpdate> accountsUpdate;

  @Inject protected AccountCache accountCache;

  @Inject protected AccountManager accountManager;

  @Inject protected GerritApi gApi;

  @Inject protected IdentifiedUser.GenericFactory userFactory;

  @Inject private Provider<AnonymousUser> anonymousUser;

  @Inject protected InMemoryDatabase schemaFactory;

  @Inject protected SchemaCreator schemaCreator;

  @Inject protected ThreadLocalRequestContext requestContext;

  @Inject protected OneOffRequestContext oneOffRequestContext;

  @Inject protected InternalAccountQuery internalAccountQuery;

  @Inject protected AllProjectsName allProjects;

  protected LifecycleManager lifecycle;
  protected Injector injector;
  protected ReviewDb db;
  protected AccountInfo currentUserInfo;
  protected CurrentUser user;

  protected abstract Injector createInjector();

  @Before
  public void setUpInjector() throws Exception {
    lifecycle = new LifecycleManager();
    injector = createInjector();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();
    setUpDatabase();
  }

  @After
  public void cleanUp() {
    lifecycle.stop();
    db.close();
  }

  protected void setUpDatabase() throws Exception {
    db = schemaFactory.open();
    schemaCreator.create(db);

    Account.Id userId = createAccount("user", "User", "user@example.com", true);
    user = userFactory.create(userId);
    requestContext.setContext(newRequestContext(userId));
    currentUserInfo = gApi.accounts().id(userId.get()).get();
  }

  protected RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser = userFactory.create(requestUserId);
    return new RequestContext() {
      @Override
      public CurrentUser getUser() {
        return requestUser;
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    };
  }

  protected void setAnonymous() {
    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return anonymousUser.get();
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });
  }

  @After
  public void tearDownInjector() {
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
  public void byName() throws Exception {
    assertQuery("name:project");
    assertQuery("name:non-existing");

    ProjectInfo project = createProject(name("project"));

    assertQuery("name:" + project.name, project);

    // only exact match
    ProjectInfo projectWithHyphen = createProject(name("project-with-hyphen"));
    createProject(name("project-no-match-with-hyphen"));
    assertQuery("name:" + projectWithHyphen.name, projectWithHyphen);
  }

  @Test
  public void byInname() throws Exception {
    String namePart = getSanitizedMethodName();
    namePart = CharMatcher.is('_').removeFrom(namePart);

    ProjectInfo project1 = createProject(name("project-" + namePart));
    ProjectInfo project2 = createProject(name("project-" + namePart + "-2"));
    ProjectInfo project3 = createProject(name("project-" + namePart + "3"));

    assertQuery("inname:" + namePart, project1, project2, project3);
    assertQuery("inname:" + namePart.toUpperCase(Locale.US), project1, project2, project3);
    assertQuery("inname:" + namePart.toLowerCase(Locale.US), project1, project2, project3);
  }

  @Test
  public void byDescription() throws Exception {
    ProjectInfo project1 =
        createProjectWithDescription(name("project1"), "This is a test project.");
    ProjectInfo project2 = createProjectWithDescription(name("project2"), "ANOTHER TEST PROJECT.");
    createProjectWithDescription(name("project3"), "Maintainers of project foo.");
    assertQuery("description:test", project1, project2);

    assertQuery("description:non-existing");

    exception.expect(BadRequestException.class);
    exception.expectMessage("description operator requires a value");
    assertQuery("description:\"\"");
  }

  @Test
  public void byDefaultField() throws Exception {
    ProjectInfo project1 = createProject(name("foo-project"));
    ProjectInfo project2 = createProject(name("project2"));
    ProjectInfo project3 =
        createProjectWithDescription(
            name("project3"),
            "decription that contains foo and the UUID of project2: " + project2.id);

    assertQuery("non-existing");
    assertQuery("foo", project1, project3);
    assertQuery(project2.id, project2, project3);
  }

  @Test
  public void withLimit() throws Exception {
    ProjectInfo project1 = createProject(name("project1"));
    ProjectInfo project2 = createProject(name("project2"));
    ProjectInfo project3 = createProject(name("project3"));

    String query =
        "name:" + project1.name + " OR name:" + project2.name + " OR name:" + project3.name;
    List<ProjectInfo> result = assertQuery(query, project1, project2, project3);

    result = assertQuery(newQuery(query).withLimit(2), result.subList(0, 2));
  }

  @Test
  public void withStart() throws Exception {
    ProjectInfo project1 = createProject(name("project1"));
    ProjectInfo project2 = createProject(name("project2"));
    ProjectInfo project3 = createProject(name("project3"));

    String query =
        "name:" + project1.name + " OR name:" + project2.name + " OR name:" + project3.name;
    List<ProjectInfo> result = assertQuery(query, project1, project2, project3);

    assertQuery(newQuery(query).withStart(1), result.subList(1, 3));
  }

  @Test
  public void asAnonymous() throws Exception {
    ProjectInfo project = createProject(name("project"));

    setAnonymous();
    assertQuery("name:" + project.name);
  }

  private Account.Id createAccount(String username, String fullName, String email, boolean active)
      throws Exception {
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      Account.Id id = accountManager.authenticate(AuthRequest.forUser(username)).getAccountId();
      if (email != null) {
        accountManager.link(id, AuthRequest.forEmail(email));
      }
      accountsUpdate
          .get()
          .update(
              "Update Test Account",
              id,
              u -> {
                u.setFullName(fullName).setPreferredEmail(email).setActive(active);
              });
      return id;
    }
  }

  protected ProjectInfo createProject(String name) throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name;
    return gApi.projects().create(in).get();
  }

  protected ProjectInfo createProjectWithDescription(String name, String description)
      throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name;
    in.description = description;
    return gApi.projects().create(in).get();
  }

  protected ProjectInfo getProject(Project.NameKey nameKey) throws Exception {
    return gApi.projects().name(nameKey.get()).get();
  }

  protected List<ProjectInfo> assertQuery(Object query, ProjectInfo... projects) throws Exception {
    return assertQuery(newQuery(query), projects);
  }

  protected List<ProjectInfo> assertQuery(QueryRequest query, ProjectInfo... projects)
      throws Exception {
    return assertQuery(query, Arrays.asList(projects));
  }

  protected List<ProjectInfo> assertQuery(QueryRequest query, List<ProjectInfo> projects)
      throws Exception {
    List<ProjectInfo> result = query.get();
    Iterable<String> names = names(result);
    assertThat(names)
        .named(format(query, result, projects))
        .containsExactlyElementsIn(names(projects));
    return result;
  }

  protected QueryRequest newQuery(Object query) {
    return gApi.projects().query(query.toString());
  }

  protected String format(
      QueryRequest query, List<ProjectInfo> actualProjects, List<ProjectInfo> expectedProjects) {
    StringBuilder b = new StringBuilder();
    b.append("query '").append(query.getQuery()).append("' with expected projects ");
    b.append(format(expectedProjects));
    b.append(" and result ");
    b.append(format(actualProjects));
    return b.toString();
  }

  protected String format(Iterable<ProjectInfo> projects) {
    StringBuilder b = new StringBuilder();
    b.append("[");
    Iterator<ProjectInfo> it = projects.iterator();
    while (it.hasNext()) {
      ProjectInfo p = it.next();
      b.append("{")
          .append(p.id)
          .append(", ")
          .append("name=")
          .append(p.name)
          .append(", ")
          .append("parent=")
          .append(p.parent)
          .append(", ")
          .append("description=")
          .append(p.description)
          .append("}");
      if (it.hasNext()) {
        b.append(", ");
      }
    }
    b.append("]");
    return b.toString();
  }

  protected static Iterable<String> names(ProjectInfo... projects) {
    return names(Arrays.asList(projects));
  }

  protected static Iterable<String> names(List<ProjectInfo> projects) {
    return projects.stream().map(p -> p.name).collect(toList());
  }

  protected String name(String name) {
    if (name == null) {
      return null;
    }

    return name + "_" + getSanitizedMethodName();
  }
}
