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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.access.AccessSectionInfo;
import com.google.gerrit.extensions.api.access.PermissionInfo;
import com.google.gerrit.extensions.api.access.PermissionRuleInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.Projects.QueryRequest;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testing.GerritServerTests;
import com.google.gerrit.testing.GerritTestName;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests queries against the project index.
 *
 * <p>Note, returned projects are sorted by name. Projects that start with a capital letter are
 * returned first.
 */
@Ignore
public abstract class AbstractQueryProjectsTest extends GerritServerTests {
  @Rule public final GerritTestName testName = new GerritTestName();

  @Inject protected Accounts accounts;

  @Inject @ServerInitiated protected Provider<AccountsUpdate> accountsUpdate;

  @Inject protected AccountCache accountCache;

  @Inject protected AccountManager accountManager;

  @Inject protected GerritApi gApi;

  @Inject protected IdentifiedUser.GenericFactory userFactory;

  @Inject private Provider<AnonymousUser> anonymousUser;

  @Inject protected SchemaCreator schemaCreator;

  @Inject protected ThreadLocalRequestContext requestContext;

  @Inject protected OneOffRequestContext oneOffRequestContext;

  @Inject protected ProjectIndexCollection indexes;

  @Inject protected AllProjectsName allProjects;

  @Inject protected AllUsersName allUsers;

  @Inject protected AuthRequest.Factory authRequestFactory;

  protected LifecycleManager lifecycle;
  protected Injector injector;
  protected AccountInfo currentUserInfo;
  protected CurrentUser user;
  protected ProjectInfo allProjectsInfo;
  protected ProjectInfo allUsersInfo;

  protected abstract Injector createInjector();

  protected void validateAssumptions() {}

  @Before
  public void setUpInjector() throws Exception {
    lifecycle = new LifecycleManager();
    injector = createInjector();
    lifecycle.add(injector);
    injector.injectMembers(this);
    lifecycle.start();
    initAfterLifecycleStart();
    setUpDatabase();
    validateAssumptions();
  }

  @After
  public void cleanUp() {
    lifecycle.stop();
  }

  protected void setUpDatabase() throws Exception {
    schemaCreator.create();

    Account.Id userId = createAccount("user", "User", "user@example.com", true);
    user = userFactory.create(userId);
    requestContext.setContext(newRequestContext(userId));
    currentUserInfo = gApi.accounts().id(userId.get()).get();

    // All-Projects and All-Users are not indexed, index them now.
    gApi.projects().name(allProjects.get()).index(/* indexChildren= */ false);
    gApi.projects().name(allUsers.get()).index(/* indexChildren= */ false);

    allProjectsInfo = gApi.projects().name(allProjects.get()).get();
    allUsersInfo = gApi.projects().name(allUsers.get()).get();
  }

  protected void initAfterLifecycleStart() throws Exception {}

  protected RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser = userFactory.create(requestUserId);
    return () -> requestUser;
  }

  protected void setAnonymous() {
    requestContext.setContext(anonymousUser::get);
  }

  @After
  public void tearDownInjector() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
  }

  @Test
  public void byEmptyQuery() throws Exception {
    ProjectInfo project1 = createProject(name("project1"));
    ProjectInfo project2 = createProject(name("project2"));
    assertQuery("", allProjectsInfo, allUsersInfo, project1, project2);
  }

  @Test
  public void byName() throws Exception {
    assertQuery("name:project");
    assertQuery("name:non-existing");

    ProjectInfo project = createProject(name("project"));

    assertQuery("name:" + project.name, project);
    assertQuery("name:" + allProjects.get(), allProjectsInfo);
    assertQuery("name:" + allUsers.get(), allUsersInfo);

    // only exact match
    ProjectInfo projectWithHyphen = createProject(name("project-with-hyphen"));
    createProject(name("project-no-match-with-hyphen"));
    assertQuery("name:" + projectWithHyphen.name, projectWithHyphen);
  }

  @Test
  public void byPrefix() throws Exception {
    assume().that(getSchemaVersion() >= 8).isTrue();

    assertQuery("prefix:project");
    assertQuery("prefix:non-existing");
    assertQuery("prefix:All", allProjectsInfo, allUsersInfo);
    assertQuery("prefix:All-", allProjectsInfo, allUsersInfo);

    ProjectInfo project1 = createProject(name("project-1"));
    ProjectInfo project2 = createProject(name("project-2"));
    ProjectInfo testProject = createProject(name("test-project"));

    assertQuery("prefix:project", project1, project2);
    assertQuery("prefix:test", testProject);
    assertQuery("prefix:TEST");
  }

  @Test
  public void byPrefixWithOtherCase() throws Exception {
    assume().that(getSchemaVersion() >= 8).isTrue();

    assertQuery("prefix:all");

    createProject(name("test-project"));
    assertQuery("prefix:TEST");
  }

  @Test
  public void bySubstring() throws Exception {
    assertQuery("substring:non-existing");

    ProjectInfo project1 = createProject(name("project-1"));
    ProjectInfo project2 = createProject(name("project-2"));
    ProjectInfo testProject = createProject(name("test-project"));
    ProjectInfo myTests = createProject(name("MY-TESTS"));

    assertQuery("substring:project", allProjectsInfo, project1, project2, testProject);
    assertQuery("substring:PROJECT", allProjectsInfo, project1, project2, testProject);
    assertQuery("substring:test", myTests, testProject);
    assertQuery("substring:TEST", myTests, testProject);
  }

  @Test
  public void byParent() throws Exception {
    assertQuery("parent:project");
    ProjectInfo parent = createProject(name("parent"));
    assertQuery("parent:" + parent.name);
    ProjectInfo child = createProject(name("child"), parent.name);
    assertQuery("parent:" + parent.name, child);
  }

  @Test
  public void byParentOfAllProjects() throws Exception {
    assume().that(getSchemaVersion() < 7).isTrue();

    ProjectInfo parent1 = createProject(name("parent1"));
    createProject(name("child"), parent1.name);

    ProjectInfo parent2 = createProject(name("parent2"));
    createProject(name("child2"), parent2.name);

    // All-Users should be returned as well, since it's a direct child project under
    // All-Projects, but it's missing in the result since the parent1 field in the index is not set
    // for projects that don't have 'access.inheritsFrom' set in project.config (which is the case
    // for the All-Users project).
    assertQuery("parent:" + allProjects.get(), parent1, parent2);
  }

  @Test
  public void byParentOfAllProjects2() throws Exception {
    assume().that(getSchemaVersion() >= 7).isTrue();

    ProjectInfo parent1 = createProject(name("parent1"));
    createProject(name("child"), parent1.name);

    ProjectInfo parent2 = createProject(name("parent2"));
    createProject(name("child2"), parent2.name);

    assertQuery("parent:" + allProjects.get(), allUsersInfo, parent1, parent2);
  }

  @Test
  public void byInname() throws Exception {
    String namePart = testName.getSanitizedMethodName();
    namePart = CharMatcher.is('_').removeFrom(namePart);

    ProjectInfo project1 = createProject(name("project1-" + namePart));
    ProjectInfo project2 = createProject(name("project2-" + namePart + "-foo"));
    ProjectInfo project3 = createProject(name("project3-" + namePart + "foo"));

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

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("description:\"\""));
    assertThat(thrown).hasMessageThat().contains("description operator requires a value");
  }

  @Test
  public void byState() throws Exception {
    assume().that(getSchemaVersion() >= 2).isTrue();

    ProjectInfo project1 = createProjectWithState(name("project1"), ProjectState.ACTIVE);
    ProjectInfo project2 = createProjectWithState(name("project2"), ProjectState.READ_ONLY);
    assertQuery("state:active", allProjectsInfo, allUsersInfo, project1);
    assertQuery("state:read-only", project2);
  }

  @Test
  public void byState_emptyQuery() throws Exception {
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("state:\"\""));
    assertThat(thrown).hasMessageThat().contains("state operator requires a value");
  }

  @Test
  public void byState_badQuery() throws Exception {
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("state:bla"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("state operator must be either 'active' or 'read-only'");
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
    assertThat(Iterables.getLast(result)._moreProjects).isNull();

    result = assertQuery(newQuery(query).withLimit(2), result.subList(0, 2));
    assertThat(Iterables.getLast(result)._moreProjects).isTrue();
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
  public void withStartCannotBeLessThanZero() throws Exception {
    assertFailingQuery(
        newQuery("name:" + allProjects.get()).withStart(-1),
        "'start' parameter cannot be less than zero");
  }

  @Test
  public void sortedByName() throws Exception {
    ProjectInfo projectFoo = createProject("foo-" + name("project1"));
    ProjectInfo projectBar = createProject("bar-" + name("project2"));
    ProjectInfo projectBaz = createProject("baz-" + name("project3"));

    String query =
        "name:" + projectFoo.name + " OR name:" + projectBar.name + " OR name:" + projectBaz.name;
    assertQuery(newQuery(query), projectBar, projectBaz, projectFoo);
  }

  @Test
  public void asAnonymous() throws Exception {
    ProjectInfo project = createProjectRestrictedToRegisteredUsers(name("project"));

    setAnonymous();
    assertQuery("name:" + project.name);
  }

  private Account.Id createAccount(String username, String fullName, String email, boolean active)
      throws Exception {
    try (ManualRequestContext ctx = oneOffRequestContext.open()) {
      Account.Id id =
          accountManager.authenticate(authRequestFactory.createForUser(username)).getAccountId();
      if (email != null) {
        accountManager.link(id, authRequestFactory.createForEmail(email));
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

  @CanIgnoreReturnValue
  protected ProjectInfo createProject(String name) throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name;
    return gApi.projects().create(in).get();
  }

  @CanIgnoreReturnValue
  protected ProjectInfo createProject(String name, String parent) throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name;
    in.parent = parent;
    return gApi.projects().create(in).get();
  }

  @CanIgnoreReturnValue
  protected ProjectInfo createProjectWithDescription(String name, String description)
      throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name;
    in.description = description;
    return gApi.projects().create(in).get();
  }

  @CanIgnoreReturnValue
  protected ProjectInfo createProjectWithState(String name, ProjectState state) throws Exception {
    ProjectInfo info = createProject(name);
    ConfigInput config = new ConfigInput();
    config.state = state;
    gApi.projects().name(info.name).config(config);
    return info;
  }

  @CanIgnoreReturnValue
  protected ProjectInfo createProjectRestrictedToRegisteredUsers(String name) throws Exception {
    createProject(name);

    ProjectAccessInput accessInput = new ProjectAccessInput();
    AccessSectionInfo accessSection = new AccessSectionInfo();
    PermissionInfo read = new PermissionInfo(null, null);
    PermissionRuleInfo pri = new PermissionRuleInfo(PermissionRuleInfo.Action.BLOCK, false);
    read.rules = ImmutableMap.of(SystemGroupBackend.ANONYMOUS_USERS.get(), pri);
    accessSection.permissions = ImmutableMap.of("read", read);
    accessInput.add = ImmutableMap.of("refs/*", accessSection);
    gApi.projects().name(name).access(accessInput);

    return gApi.projects().name(name).get();
  }

  protected ProjectInfo getProject(Project.NameKey nameKey) throws Exception {
    return gApi.projects().name(nameKey.get()).get();
  }

  @CanIgnoreReturnValue
  protected List<ProjectInfo> assertQuery(Object query, ProjectInfo... projects) throws Exception {
    return assertQuery(newQuery(query), projects);
  }

  @CanIgnoreReturnValue
  protected List<ProjectInfo> assertQuery(QueryRequest query, ProjectInfo... projects)
      throws Exception {
    return assertQuery(query, Arrays.asList(projects));
  }

  @CanIgnoreReturnValue
  protected List<ProjectInfo> assertQuery(QueryRequest query, List<ProjectInfo> projects)
      throws Exception {
    List<ProjectInfo> result = query.get();
    Iterable<String> names = names(result);
    assertWithMessage(format(query, result, projects))
        .that(names)
        .containsExactlyElementsIn(names(projects))
        .inOrder();
    return result;
  }

  protected void assertFailingQuery(QueryRequest query, String expectedMessage) throws Exception {
    try {
      assertQuery(query);
      fail("expected BadRequestException for query '" + query + "'");
    } catch (BadRequestException e) {
      assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
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

  protected int getSchemaVersion() {
    return getSchema().getVersion();
  }

  protected Schema<ProjectData> getSchema() {
    return indexes.getSearchIndex().getSchema();
  }

  protected static Iterable<String> names(ProjectInfo... projects) {
    return names(Arrays.asList(projects));
  }

  protected static Iterable<String> names(List<ProjectInfo> projects) {
    return projects.stream().map(p -> p.name).collect(toList());
  }

  @Nullable
  protected String name(String name) {
    if (name == null) {
      return null;
    }

    return name + "_" + testName.getSanitizedMethodName();
  }
}
