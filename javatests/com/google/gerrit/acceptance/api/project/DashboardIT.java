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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.api.projects.DashboardSectionInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.restapi.project.DashboardsCollection;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class DashboardIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Before
  public void setup() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref("refs/meta/dashboards/*").group(REGISTERED_USERS))
        .update();
  }

  @Test
  public void defaultDashboardDoesNotExist() throws Exception {
    assertThrows(ResourceNotFoundException.class, () -> project().defaultDashboard().get());
  }

  @Test
  public void dashboardDoesNotExist() throws Exception {
    assertThrows(ResourceNotFoundException.class, () -> project().dashboard("my:dashboard").get());
  }

  @Test
  public void getDashboard() throws Exception {
    DashboardInfo info = createTestDashboard();
    DashboardInfo result = project().dashboard(info.id).get();
    assertDashboardInfo(result, info);
  }

  @Test
  public void getDashboardWithNoDescription() throws Exception {
    DashboardInfo info = newDashboardInfo(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    info.description = null;
    DashboardInfo created = createDashboard(info);
    assertThat(created.description).isNull();
    DashboardInfo result = project().dashboard(created.id).get();
    assertThat(result.description).isNull();
  }

  @Test
  public void getDashboardNonDefault() throws Exception {
    DashboardInfo info = createTestDashboard("my", "test");
    DashboardInfo result = project().dashboard(info.id).get();
    assertDashboardInfo(result, info);
  }

  @Test
  public void listDashboards() throws Exception {
    assertThat(dashboards()).isEmpty();
    DashboardInfo info1 = createTestDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test1");
    DashboardInfo info2 = createTestDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test2");
    assertThat(dashboards().stream().map(d -> d.id).collect(toList()))
        .containsExactly(info1.id, info2.id);
  }

  @Test
  public void setDefaultDashboard() throws Exception {
    DashboardInfo info = createTestDashboard();
    assertThat(info.isDefault).isNull();
    project().dashboard(info.id).setDefault();
    assertThat(project().dashboard(info.id).get().isDefault).isTrue();
    assertThat(project().defaultDashboard().get().id).isEqualTo(info.id);
  }

  @Test
  public void setDefaultDashboardByProject() throws Exception {
    DashboardInfo info = createTestDashboard();
    assertThat(info.isDefault).isNull();
    project().defaultDashboard(info.id);
    assertThat(project().dashboard(info.id).get().isDefault).isTrue();
    assertThat(project().defaultDashboard().get().id).isEqualTo(info.id);

    project().removeDefaultDashboard();
    assertThat(project().dashboard(info.id).get().isDefault).isNull();

    assertThrows(ResourceNotFoundException.class, () -> project().defaultDashboard().get());
  }

  @Test
  public void replaceDefaultDashboard() throws Exception {
    DashboardInfo d1 = createTestDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test1");
    DashboardInfo d2 = createTestDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test2");
    assertThat(d1.isDefault).isNull();
    assertThat(d2.isDefault).isNull();
    project().dashboard(d1.id).setDefault();
    assertThat(project().dashboard(d1.id).get().isDefault).isTrue();
    assertThat(project().dashboard(d2.id).get().isDefault).isNull();
    assertThat(project().defaultDashboard().get().id).isEqualTo(d1.id);
    project().dashboard(d2.id).setDefault();
    assertThat(project().defaultDashboard().get().id).isEqualTo(d2.id);
    assertThat(project().dashboard(d1.id).get().isDefault).isNull();
    assertThat(project().dashboard(d2.id).get().isDefault).isTrue();
  }

  @Test
  public void cannotGetDashboardWithInheritedForNonDefault() throws Exception {
    DashboardInfo info = createTestDashboard();
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> project().dashboard(info.id).get(true));
    assertThat(thrown).hasMessageThat().contains("inherited flag can only be used with default");
  }

  private void assertDashboardInfo(DashboardInfo actual, DashboardInfo expected) throws Exception {
    assertThat(actual.id).isEqualTo(expected.id);
    assertThat(actual.path).isEqualTo(expected.path);
    assertThat(actual.ref).isEqualTo(expected.ref);
    assertThat(actual.project).isEqualTo(project.get());
    assertThat(actual.definingProject).isEqualTo(project.get());
    assertThat(actual.description).isEqualTo(expected.description);
    assertThat(actual.title).isEqualTo(expected.title);
    assertThat(actual.foreach).isEqualTo(expected.foreach);
    if (expected.sections == null) {
      assertThat(actual.sections).isNull();
    } else {
      assertThat(actual.sections).hasSize(expected.sections.size());
    }
  }

  private List<DashboardInfo> dashboards() throws Exception {
    return project().dashboards().get();
  }

  private ProjectApi project() throws RestApiException {
    return gApi.projects().name(project.get());
  }

  private DashboardInfo newDashboardInfo(String ref, String path) {
    DashboardInfo info = DashboardsCollection.newDashboardInfo(ref, path);
    info.title = "Reviewer";
    info.description = "Own review requests";
    info.foreach = "owner:self";
    DashboardSectionInfo section = new DashboardSectionInfo();
    section.name = "Open";
    section.query = "is:open";
    info.sections = ImmutableList.of(section);
    return info;
  }

  private DashboardInfo createTestDashboard() throws Exception {
    return createTestDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
  }

  private DashboardInfo createTestDashboard(String ref, String path) throws Exception {
    return createDashboard(newDashboardInfo(ref, path));
  }

  private DashboardInfo createDashboard(DashboardInfo info) throws Exception {
    String canonicalRef = DashboardsCollection.normalizeDashboardRef(info.ref);
    try {
      project().branch(canonicalRef).create(new BranchInput());
    } catch (ResourceConflictException e) {
      // The branch already exists if this method has already been called once.
      if (!e.getMessage().contains("already exists")) {
        throw e;
      }
    }
    try (Repository r = repoManager.openRepository(project);
        TestRepository<Repository> tr = new TestRepository<>(r)) {
      TestRepository<Repository>.CommitBuilder cb = tr.branch(canonicalRef).commit();
      StringBuilder content = new StringBuilder("[dashboard]\n");
      if (info.title != null) {
        content.append("title = ").append(info.title).append("\n");
      }
      if (info.description != null) {
        content.append("description = ").append(info.description).append("\n");
      }
      if (info.foreach != null) {
        content.append("foreach = ").append(info.foreach).append("\n");
      }
      if (info.sections != null) {
        for (DashboardSectionInfo section : info.sections) {
          content.append("[section \"").append(section.name).append("\"]\n");
          content.append("query = ").append(section.query).append("\n");
        }
      }
      cb.add(info.path, content.toString());
      RevCommit c = cb.create();
      project().commit(c.name());
    }
    return info;
  }
}
