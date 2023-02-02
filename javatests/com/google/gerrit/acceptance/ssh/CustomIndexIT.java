// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.index.testing.FakeIndexVersionManager;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.AbstractIndexModule;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * Tests for a defaulted custom index configuration. This unknown type is the opposite of {@link
 * IndexType#getKnownTypes()}.
 */
public class CustomIndexIT extends AbstractIndexTests {

  @Override
  public Module createModule() {
    return CustomIndexModule.latestVersion(false);
  }

  @ConfigSuite.Default
  public static Config customIndexType() {
    Config config = new Config();
    config.setString("index", null, "type", "custom");
    return config;
  }

  @Inject private ChangeIndexCollection changeIndex;

  @Test
  public void customIndexModuleIsBound() throws Exception {
    assertThat(changeIndex.getSearchIndex()).isInstanceOf(CustomModuleFakeIndexChange.class);
  }
}

class CustomIndexModule extends AbstractIndexModule {

  public static CustomIndexModule latestVersion(boolean secondary) {
    return new CustomIndexModule(/* singleVersions= */ null, -1 /* direct executor */, secondary);
  }

  private CustomIndexModule(
      ImmutableMap<String, Integer> singleVersions, int threads, boolean secondary) {
    super(singleVersions, threads, secondary);
  }

  @Override
  protected Class<? extends AccountIndex> getAccountIndex() {
    return AbstractFakeIndex.FakeAccountIndex.class;
  }

  @Override
  protected Class<? extends ChangeIndex> getChangeIndex() {
    return CustomModuleFakeIndexChange.class;
  }

  @Override
  protected Class<? extends GroupIndex> getGroupIndex() {
    return AbstractFakeIndex.FakeGroupIndex.class;
  }

  @Override
  protected Class<? extends ProjectIndex> getProjectIndex() {
    return AbstractFakeIndex.FakeProjectIndex.class;
  }

  @Override
  protected Class<? extends VersionManager> getVersionManager() {
    return FakeIndexVersionManager.class;
  }
}

class CustomModuleFakeIndexChange extends AbstractFakeIndex.FakeChangeIndex {

  @com.google.inject.Inject
  CustomModuleFakeIndexChange(
      SitePaths sitePaths,
      ChangeData.Factory changeDataFactory,
      @Assisted Schema<ChangeData> schema,
      @GerritServerConfig Config cfg,
      IndexConfig indexConfig) {
    super(sitePaths, changeDataFactory, schema, cfg, indexConfig);
  }
}
