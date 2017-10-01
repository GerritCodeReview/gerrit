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

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.DashboardInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.project.DashboardsCollection;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class DashboardIT extends AbstractDaemonTest {
  @Test
  public void defaultDashboardDoesNotExist() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).defaultDashboard().get();
  }

  @Test
  public void dashboardDoesNotExist() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).dashboard("my:dashboard").get();
  }

  @Test
  public void getDashboard() throws Exception {
    String id = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    gApi.projects().name(project.get()).dashboard(id).get();
  }

  @Test
  public void cannotGetDashboardWithInheritedForNonDefault() throws Exception {
    String id = createDashboard(DashboardsCollection.DEFAULT_DASHBOARD_NAME, "test");
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("inherited");
    gApi.projects().name(project.get()).dashboard(id).get(true);
  }

  private String createDashboard(String ref, String path) throws Exception {
    DashboardInfo info = DashboardsCollection.newDashboardInfo(ref, path);
    String canonicalRef = DashboardsCollection.normalizeDashboardRef(info.ref);
    allow("refs/meta/dashboards/*", Permission.CREATE, REGISTERED_USERS);
    gApi.projects().name(project.get()).branch(canonicalRef).create(new BranchInput());
    try (Repository r = repoManager.openRepository(project)) {
      TestRepository<Repository>.CommitBuilder cb =
          new TestRepository<>(r).branch(canonicalRef).commit();
      String content =
          "[dashboard]\n"
              + "Description = Test\n"
              + "foreach = owner:self\n"
              + "[section \"Mine\"]\n"
              + "query = is:open";
      cb.add(info.path, content);
      RevCommit c = cb.create();
      gApi.projects().name(project.get()).commit(c.name());
    }
    return info.id;
  }
}
