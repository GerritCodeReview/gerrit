// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.Schema;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SingleVersionModule extends LifecycleModule {
  public static final String SINGLE_VERSIONS = "IndexModule/SingleVersions";

  private final Map<String, Integer> singleVersions;

  public SingleVersionModule(Map<String, Integer> singleVersions) {
    this.singleVersions = singleVersions;
  }

  @Override
  public void configure() {
    listener().to(SingleVersionListener.class);
    bind(new TypeLiteral<Map<String, Integer>>() {})
        .annotatedWith(Names.named(SINGLE_VERSIONS))
        .toProvider(Providers.of(singleVersions));
  }

  @Singleton
  public static class SingleVersionListener implements LifecycleListener {
    private final Set<String> disabled;
    private final Collection<IndexDefinition<?, ?, ?>> defs;
    private final Map<String, Integer> singleVersions;

    @Inject
    SingleVersionListener(
        @GerritServerConfig Config cfg,
        Collection<IndexDefinition<?, ?, ?>> defs,
        @Named(SINGLE_VERSIONS) Map<String, Integer> singleVersions) {
      this.defs = defs;
      this.singleVersions = singleVersions;

      disabled = ImmutableSet.copyOf(cfg.getStringList("index", null, "testDisable"));
    }

    @Override
    public void start() {
      for (IndexDefinition<?, ?, ?> def : defs) {
        start(def);
      }
    }

    private <K, V, I extends Index<K, V>> void start(IndexDefinition<K, V, I> def) {
      if (disabled.contains(def.getName())) {
        return;
      }
      Schema<V> schema;
      Integer v = singleVersions.get(def.getName());
      if (v == null) {
        schema = def.getLatest();
      } else {
        schema = def.getSchemas().get(v);
        if (schema == null) {
          throw new ProvisionException(
              String.format("Unrecognized %s schema version: %s", def.getName(), v));
        }
      }
      I index = def.getIndexFactory().create(schema);
      def.getIndexCollection().setSearchIndex(index);
      def.getIndexCollection().addWriteIndex(index);
    }

    @Override
    public void stop() {
      // Do nothing; indexes are closed by IndexCollection.
    }
  }
}
