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
import com.google.common.truth.Truth;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
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

  @Test
  public void initDoesNotReindexProjectsOnExistingSites() throws Exception {
    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
    Optional<Long> initialProjectsPath = getProjectsIndexLastModified(sitePaths.index_dir);

    // Make sure that the lastModified() returns different values if directory is rewritten
    Thread.sleep(1000L);

    runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
    Optional<Long> afterInitProjectsPath = getProjectsIndexLastModified(sitePaths.index_dir);

    Truth.assertThat(initialProjectsPath).isEqualTo(afterInitProjectsPath);
  }

  private Optional<Long> getProjectsIndexLastModified(Path indexDir) throws IOException {
    Optional<Path> projectsIndexPath =
        Files.walk(indexDir, 1)
            .filter(Files::isDirectory)
            .filter(p -> p.getFileName().toString().startsWith("projects_"))
            .findFirst();

    Optional<Stream<Long>> projectsIndexLastModified =
        projectsIndexPath.map(
            pd -> {
              try {
                return Files.walk(pd, 1).map(p -> p.toFile().lastModified());
              } catch (IOException e) {
                e.printStackTrace();
                return Stream.empty();
              }
            });

    return projectsIndexLastModified.flatMap(
        mod -> mod.max(Comparator.comparingLong(Long::valueOf)));
  }
}
