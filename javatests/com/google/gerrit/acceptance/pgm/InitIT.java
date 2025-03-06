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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.server.account.AuthTokenCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

@NoHttpd
public class InitIT extends StandaloneSiteTest {

  @Test
  public void indexesAllProjectsAndAllUsers() throws Exception {
    initSite();
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
    // These tests expect the Lucene index to modify files on disk which the fake index doesn't do.
    String configuredIndexBackend = baseConfig.getString("index", null, "type");
    configuredIndexBackend = configuredIndexBackend == null ? "fake" : configuredIndexBackend;
    assume().that(configuredIndexBackend).isEqualTo("lucene");
    initSite();

    // Simulate a projects indexes files modified in the past by 3 seconds
    Optional<Instant> projectsLastModified =
        getProjectsIndexLastModified(sitePaths.index_dir).map(t -> t.minusSeconds(3));
    assertThat(projectsLastModified).isPresent();
    setProjectsIndexLastModifiedInThePast(sitePaths.index_dir, projectsLastModified.get());

    initSite();
    Optional<Instant> projectsLastModifiedAfterInit =
        getProjectsIndexLastModified(sitePaths.index_dir);

    // Verify that projects index files haven't been updated
    assertThat(projectsLastModified).isEqualTo(projectsLastModifiedAfterInit);
  }

  @Test
  public void initInDevModeCreatesAdminUserWithToken() throws Exception {
    initSite("--dev");

    try (ServerContext ctx = startServer()) {
      AuthTokenCache tokenCache = ctx.getInjector().getInstance(AuthTokenCache.class);
      assertThat(tokenCache.get(Account.id(10000))).isNotNull();
    }
  }

  private void initSite(String... additionalOptions) throws Exception {
    if (additionalOptions.length > 0) {
      runGerrit(
          "init",
          "-d",
          sitePaths.site_path.toString(),
          "--show-stack-trace",
          String.join(" ", additionalOptions));
    } else {
      runGerrit("init", "-d", sitePaths.site_path.toString(), "--show-stack-trace");
    }
  }

  private void setProjectsIndexLastModifiedInThePast(Path indexDir, Instant time)
      throws IOException {
    try (Stream<Path> allprojectsIndexFiles = getAllProjectsIndexFiles(indexDir)) {
      for (Path path : allprojectsIndexFiles.collect(Collectors.toList())) {
        FS.DETECTED.setLastModified(path, time);
      }
    }
  }

  private Optional<Instant> getProjectsIndexLastModified(Path indexDir) throws IOException {
    try (Stream<Path> allprojectsIndexFiles = getAllProjectsIndexFiles(indexDir)) {
      return allprojectsIndexFiles
          .map(FS.DETECTED::lastModifiedInstant)
          .max(Comparator.comparingLong(Instant::toEpochMilli));
    }
  }

  @MustBeClosed
  private Stream<Path> getAllProjectsIndexFiles(Path indexDir) throws IOException {
    try (Stream<Path> stream = Files.walk(indexDir, 1)) {
      Optional<Path> projectsPath =
          stream
              .filter(Files::isDirectory)
              .filter(p -> p.getFileName().toString().startsWith("projects_"))
              .findFirst();
      if (!projectsPath.isPresent()) {
        return Stream.empty();
      }
      return Files.walk(projectsPath.get(), 1, FileVisitOption.FOLLOW_LINKS);
    }
  }
}
