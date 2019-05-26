// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import java.util.Optional;
import org.junit.Test;

@NoHttpd
public class InitIT extends StandaloneSiteTest {

  @Test
  public void indexesAllProjectsAndAllUsers() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
    try (ServerContext ctx = startServer()) {
      ProjectIndexCollection projectIndex =
          ctx.getInjector().getInstance(ProjectIndexCollection.class);
      Project.NameKey allProjects = ctx.getInjector().getInstance(AllProjectsName.class);
      Project.NameKey allUsers = ctx.getInjector().getInstance(AllUsersName.class);
      QueryOptions opts =
          QueryOptions.create(IndexConfig.createDefault(), 0, 1, ImmutableSet.of("name"));
      Optional<ProjectData> allProjectsData = projectIndex.getSearchIndex().get(allProjects, opts);
      assertThat(allProjectsData).isPresent();
      Optional<ProjectData> allUsersData = projectIndex.getSearchIndex().get(allUsers, opts);
      assertThat(allUsersData).isPresent();
    }
  }
}
