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

package com.google.gerrit.server.index;

import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

public abstract class AbstractIndexModule extends AbstractModule {

  private final int threads;
  private final Map<String, Integer> singleVersions;
  private final boolean slave;

  protected AbstractIndexModule(Map<String, Integer> singleVersions, int threads, boolean slave) {
    this.singleVersions = singleVersions;
    this.threads = threads;
    this.slave = slave;
  }

  @Override
  protected void configure() {
    if (slave) {
      bind(AccountIndex.Factory.class).toInstance(AbstractIndexModule::createDummyIndexFactory);
      bind(ChangeIndex.Factory.class).toInstance(AbstractIndexModule::createDummyIndexFactory);
      bind(ProjectIndex.Factory.class).toInstance(AbstractIndexModule::createDummyIndexFactory);
    } else {
      install(
          new FactoryModuleBuilder()
              .implement(AccountIndex.class, getAccountIndex())
              .build(AccountIndex.Factory.class));
      install(
          new FactoryModuleBuilder()
              .implement(ChangeIndex.class, getChangeIndex())
              .build(ChangeIndex.Factory.class));
      install(
          new FactoryModuleBuilder()
              .implement(ProjectIndex.class, getProjectIndex())
              .build(ProjectIndex.Factory.class));
    }
    install(
        new FactoryModuleBuilder()
            .implement(GroupIndex.class, getGroupIndex())
            .build(GroupIndex.Factory.class));

    install(new IndexModule(threads, slave));
    if (singleVersions == null) {
      install(new MultiVersionModule());
    } else {
      install(new SingleVersionModule(singleVersions));
    }
  }

  @SuppressWarnings("unused")
  private static <T> T createDummyIndexFactory(Schema<?> schema) {
    throw new UnsupportedOperationException();
  }

  protected abstract Class<? extends AccountIndex> getAccountIndex();

  protected abstract Class<? extends ChangeIndex> getChangeIndex();

  protected abstract Class<? extends GroupIndex> getGroupIndex();

  protected abstract Class<? extends ProjectIndex> getProjectIndex();

  protected abstract Class<? extends VersionManager> getVersionManager();

  @Provides
  @Singleton
  IndexConfig provideIndexConfig(@GerritServerConfig Config cfg) {
    return getIndexConfig(cfg);
  }

  protected IndexConfig getIndexConfig(@GerritServerConfig Config cfg) {
    return IndexConfig.fromConfig(cfg).separateChangeSubIndexes(true).build();
  }

  private class MultiVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      Class<? extends VersionManager> versionManagerClass = getVersionManager();
      bind(VersionManager.class).to(versionManagerClass);
      listener().to(versionManagerClass);
    }
  }
}
