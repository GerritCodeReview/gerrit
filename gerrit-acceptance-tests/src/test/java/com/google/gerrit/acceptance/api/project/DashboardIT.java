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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.DashboardsCollection;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class DashboardIT extends AbstractDaemonTest {
  @Before
  public void setup() throws Exception {
    allow("refs/meta/dashboards/*", Permission.CREATE, REGISTERED_USERS);
  }

  @Test
  public void defaultDashboardDoesNotExist() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    project().defaultDashboard().get();
  }

  @Test
  public void dashboardDoesNotExist() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    project().dashboard("my:dashboard").get();
  }

  @Test
  public void getDashboard() throws Exception {
    DashboardInfo info = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    DashboardInfo result = project().dashboard(info.id).get();
    assertDashboardInfo(result, info);
  }

  @Test
  public void getDashboardNonDefault() throws Exception {
    DashboardInfo info = createDashboard("my", "test");
    DashboardInfo result = project().dashboard(info.id).get();
    assertDashboardInfo(result, info);
  }

  @Test
  public void listDashboards() throws Exception {
    assertThat(dashboards()).isEmpty();
    DashboardInfo info1 = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test1");
    DashboardInfo info2 = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test2");
    assertThat(dashboards().stream().map(d -> d.id).collect(toList()))
        .containsExactly(info1.id, info2.id);
  }

  @Test
  public void setDefaultDashboard() throws Exception {
    DashboardInfo info = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    assertThat(info.isDefault).isNull();
    project().dashboard(info.id).setDefault();
    assertThat(project().dashboard(info.id).get().isDefault).isTrue();
    assertThat(project().defaultDashboard().get().id).isEqualTo(info.id);
  }

  @Test
  public void setDefaultDashboardByProject() throws Exception {
    DashboardInfo info = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    assertThat(info.isDefault).isNull();
    project().defaultDashboard(info.id);
    assertThat(project().dashboard(info.id).get().isDefault).isTrue();
    assertThat(project().defaultDashboard().get().id).isEqualTo(info.id);

    project().removeDefaultDashboard();
    assertThat(project().dashboard(info.id).get().isDefault).isNull();

    exception.expect(ResourceNotFoundException.class);
    project().defaultDashboard().get();
  }

  @Test
  public void replaceDefaultDashboard() throws Exception {
    DashboardInfo d1 = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test1");
    DashboardInfo d2 = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test2");
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
    DashboardInfo info = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    exception.expect(BadRequestException.class);
    exception.expectMessage("inherited flag can only be used with default");
    project().dashboard(info.id).get(true);
  }

  private void assertDashboardInfo(DashboardInfo actual, DashboardInfo expected) throws Exception {
    assertThat(actual.id).isEqualTo(expected.id);
    assertThat(actual.path).isEqualTo(expected.path);
    assertThat(actual.ref).isEqualTo(expected.ref);
    assertThat(actual.project).isEqualTo(project.get());
    assertThat(actual.definingProject).isEqualTo(project.get());
  }

  private List<DashboardInfo> dashboards() throws Exception {
    return project().dashboards().get();
  }

  private ProjectApi project() throws RestApiException {
    return gApi.projects().name(project.get());
  }

  private DashboardInfo createDashboard(String ref, String path) throws Exception {
    DashboardInfo info = DashboardsCollection.newDashboardInfo(ref, path);
    String canonicalRef = DashboardsCollection.normalizeDashboardRef(info.ref);
    try {
      project().branch(canonicalRef).create(new BranchInput());
    } catch (ResourceConflictException e) {
      // The branch already exists if this method has already been called once.
      if (!e.getMessage().contains("already exists")) {
        throw e;
      }
    }
    try (Repository r = repoManager.openRepository(project)) {
      TestRepository<Repository>.CommitBuilder cb =
          new TestRepository<>(r).branch(canonicalRef).commit();
      String content =
          "[dashboard]\n"
              + "Title = Reviewer\n"
              + "Description = Own review requests\n"
              + "foreach = owner:self\n"
              + "[section \"Open\"]\n"
              + "query = is:open";
      cb.add(info.path, content);
      RevCommit c = cb.create();
      project().commit(c.name());
    }
    return info;
  }
}
