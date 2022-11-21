// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GcConfig;
import com.google.gerrit.server.config.GitBasePathProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GarbageCollectionTest {
  private static final Project.NameKey FOO = Project.nameKey("foo");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private GcConfig gcConfig;
  @Mock private DelegateRepository wrapper;

  private SitePaths site;
  private Config cfg;

  @Before
  public void setup() throws Exception {
    site = new SitePaths(temporaryFolder.newFolder().toPath());
    site.resolve("git").toFile().mkdir();
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
  }

  @Test
  public void shouldCallGcOnDelegatedRepositoryWhenDelegateRepositoryIsPassed() throws IOException {
    // given
    GarbageCollection objectUnderTest = prepareObjectForTesting();

    // when
    objectUnderTest.run(ImmutableList.of(FOO), false, null);

    // then
    verify(wrapper).delegate();
  }

  private GarbageCollection prepareObjectForTesting() throws IOException {
    LocalDiskRepositoryManager repoManager =
        new DelegatedRepositoryManager(new GitBasePathProvider(cfg, site), wrapper);
    try (Repository repo = repoManager.createRepository(FOO)) {
      assertThat(repo).isNotNull();
    }
    return new GarbageCollection(
        repoManager,
        new GarbageCollectionQueue(),
        gcConfig,
        new PluginSetContext<>(new DynamicSet<>(), PluginMetrics.DISABLED_INSTANCE));
  }

  private static final class DelegatedRepositoryManager extends LocalDiskRepositoryManager {
    private final DelegateRepository wrapper;

    private DelegatedRepositoryManager(
        GitBasePathProvider basePathProvider, DelegateRepository wrapper) {
      super(basePathProvider);
      this.wrapper = wrapper;
    }

    @Override
    public Repository openRepository(NameKey name) throws RepositoryNotFoundException {
      Repository opened = super.openRepository(name);
      when(wrapper.delegate()).thenReturn(opened);
      when(wrapper.getConfig()).thenReturn(opened.getConfig());
      return wrapper;
    }
  }
}
