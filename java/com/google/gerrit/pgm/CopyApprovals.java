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

package com.google.gerrit.pgm;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.LibModuleLoader;
import com.google.gerrit.server.ModuleOverloader;
import com.google.gerrit.server.approval.RecursiveApprovalCopier;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.index.options.IsFirstInsertForEntry;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.util.ReplicaUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.OptionalBinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

public class CopyApprovals extends SiteProgram {

  private Injector dbInjector;
  private Injector sysInjector;
  private Injector cfgInjector;
  private Config globalConfig;

  @Inject private RecursiveApprovalCopier recursiveApprovalCopier;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector();
    cfgInjector = dbInjector.createChildInjector();
    globalConfig = dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.getInstance(PluginGuiceEnvironment.class).setDbCfgInjector(dbInjector, cfgInjector);
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    sysInjector.injectMembers(this);

    try {
      recursiveApprovalCopier.persist();
      return 0;
    } catch (Exception e) {
      throw die(e.getMessage(), e);
    } finally {
      sysManager.stop();
      dbManager.stop();
    }
  }

  private Injector createSysInjector() {
    Map<String, Integer> versions = new HashMap<>();
    boolean replica = ReplicaUtil.isReplica(globalConfig);
    List<Module> modules = new ArrayList<>();
    Module indexModule;
    IndexType indexType = IndexModule.getIndexType(dbInjector);
    if (indexType.isLucene()) {
      indexModule =
          LuceneIndexModule.singleVersionWithExplicitVersions(
              versions, 1, replica, AutoFlush.DISABLED);
    } else if (indexType.isFake()) {
      // Use Reflection so that we can omit the fake index binary in production code. Test code does
      // compile the component in.
      try {
        Class<?> clazz = Class.forName("com.google.gerrit.index.testing.FakeIndexModule");
        Method m =
            clazz.getMethod(
                "singleVersionWithExplicitVersions", Map.class, int.class, boolean.class);
        indexModule = (Module) m.invoke(null, versions, 1, replica);
      } catch (NoSuchMethodException
          | ClassNotFoundException
          | IllegalAccessException
          | InvocationTargetException e) {
        throw new IllegalStateException("can't create index", e);
      }
    } else {
      throw new IllegalStateException("unsupported index.type = " + indexType);
    }
    modules.add(indexModule);
    modules.add(
        new AbstractModule() {
          @Override
          protected void configure() {
            super.configure();
            OptionalBinder.newOptionalBinder(binder(), IsFirstInsertForEntry.class)
                .setBinding()
                .toInstance(IsFirstInsertForEntry.YES);
          }
        });

    modules.add(
        new FactoryModule() {
          @Override
          protected void configure() {
            factory(ChangeResource.Factory.class);
          }
        });
    modules.add(new BatchProgramModule(dbInjector));

    return dbInjector.createChildInjector(
        ModuleOverloader.override(
            modules, LibModuleLoader.loadReindexModules(cfgInjector, versions, 1, replica)));
  }
}
