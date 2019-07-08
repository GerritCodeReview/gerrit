// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm.init.index;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.index.IndexDefinition;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collection;

/**
 * This class starts/stops the indexes from the init program so that init can write updates the
 * indexes.
 */
public class IndexManagerOnInit {
  private final LifecycleListener indexManager;
  private final Collection<IndexDefinition<?, ?, ?>> defs;

  @Inject
  IndexManagerOnInit(
      @Named(IndexModuleOnInit.INDEX_MANAGER) LifecycleListener indexManager,
      Collection<IndexDefinition<?, ?, ?>> defs) {
    this.indexManager = indexManager;
    this.defs = defs;
  }

  public void start() {
    indexManager.start();

    for (IndexDefinition<?, ?, ?> def : defs) {
      def.getIndexCollection().start();
    }
  }

  public void stop() {
    indexManager.stop();

    for (IndexDefinition<?, ?, ?> def : defs) {
      def.getIndexCollection().stop();
    }
  }
}
