// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.notedb.rebuild.SiteRebuilder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

public class RebuildNoteDb extends SiteProgram {
  @Option(name = "--threads", usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--project", usage = "Projects to rebuild; recommended for debugging only")
  private List<String> projects = new ArrayList<>();

  @Option(
    name = "--change",
    usage = "Individual change numbers to rebuild; recommended for debugging only"
  )
  private List<Integer> changes = new ArrayList<>();

  private Injector dbInjector;
  private Injector sysInjector;

  @Inject private Provider<SiteRebuilder.Builder> rebuilderBuilderProvider;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    try (SiteRebuilder rebuilder =
        rebuilderBuilderProvider
            .get()
            .setThreads(threads)
            .setProgressOut(System.err)
            .setTrialMode(true)
            .setForceRebuild(true)
            .setProjects(projects.stream().map(Project.NameKey::new).collect(toList()))
            .setChanges(changes.stream().map(Change.Id::new).collect(toList()))
            .build()) {
      if (!projects.isEmpty() || !changes.isEmpty()) {
        rebuilder.rebuild();
      } else {
        rebuilder.autoRebuild();
      }
    }
    return 0;
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(
        new FactoryModule() {
          @Override
          public void configure() {
            install(dbInjector.getInstance(BatchProgramModule.class));
            DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
                .to(ReindexAfterRefUpdate.class);
            install(new DummyIndexModule());
            factory(ChangeResource.Factory.class);
          }
        });
  }
}
