// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.project.PeriodicProjectIndexer;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.junit.Test;

@UseLocalDisk
public class PeriodicProjectIndexerIT extends AbstractDaemonTest {

  @Inject private ProjectIndexCollection indexes;
  @Inject private IndexConfig indexConfig;

  @Inject private PeriodicProjectIndexer periodicIndexer;

  private static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(ProjectField.NAME_SPEC.getName());

  @Test
  public void removesNonExistingProjectsFromIndex() throws Exception {
    Project.NameKey foo = Project.nameKey("foo");
    gApi.projects().create(foo.get());
    ProjectIndex i = indexes.getSearchIndex();
    Optional<FieldBundle> result = i.getRaw(foo, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();
    Path basePath = ((LocalDiskRepositoryManager) repoManager).getBasePath(foo);
    Path fooPath = basePath.resolve(foo.get() + Constants.DOT_GIT_EXT);

    MoreFiles.deleteRecursively(fooPath, RecursiveDeleteOption.ALLOW_INSECURE);
    projectCache.evict(foo);
    RepositoryCache.clear();

    result = i.getRaw(foo, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();

    periodicIndexer.run();
    result = i.getRaw(foo, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isEmpty();
  }
}
