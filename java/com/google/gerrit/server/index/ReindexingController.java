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

package com.google.gerrit.server.index;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;

public class ReindexingController implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(ReindexingController.class);
    }
  }

  private final IndexVersionReindexer indexVersionReindexer;
  private final Config cfg;
  private Collection<IndexDefinition<?, ?, ?>> indexDefs;

  @Inject
  ReindexingController(
      IndexVersionReindexer indexVersionReindexer,
      @GerritServerConfig Config cfg,
      Collection<IndexDefinition<?, ?, ?>> indexDefs) {
    this.indexVersionReindexer = indexVersionReindexer;
    this.cfg = cfg;
    this.indexDefs = indexDefs;
  }

  @Override
  public void start() {
    Thread t = new Thread(this::reindex);
    t.setName("ReindexOnStart");
    t.start();
  }

  private void reindex() {
    for (IndexDefinition<?, ?, ?> def : indexDefs) {
      String name = def.getName();
      if (cfg.getBoolean("index", name, "reindexOnStart", false)) {
        int version = def.getIndexCollection().getSearchIndex().getSchema().getVersion();
        try {
          indexVersionReindexer.reindex(def, version, true).get();
        } catch (InterruptedException | ExecutionException e) {
          logger.atSevere().withCause(e).log(
              "Error while reindexing index %s version %d", name, version);
        }
      }
    }
  }

  @Override
  public void stop() {}
}
