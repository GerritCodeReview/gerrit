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

package com.google.gerrit.lucene;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class LuceneIndexModule extends LifecycleModule {
  private final Integer singleVersion;
  private final int threads;
  private final String base;

  public LuceneIndexModule() {
    this(null, 0, null);
  }

  public LuceneIndexModule(Integer singleVersion, int threads,
      String base) {
    this.singleVersion = singleVersion;
    this.threads = threads;
    this.base = base;
  }

  @Override
  protected void configure() {
    install(new FactoryModule() {
      @Override
      public void configure() {
        factory(LuceneChangeIndex.Factory.class);
      }
    });
    install(new IndexModule(threads));
    if (singleVersion == null && base == null) {
      install(new MultiVersionModule());
    } else {
      install(new SingleVersionModule());
    }
  }

  private class MultiVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      install(new FactoryModule() {
        @Override
        public void configure() {
          factory(OnlineReindexer.Factory.class);
        }
      });
      listener().to(LuceneVersionManager.class);
    }
  }

  private class SingleVersionModule extends LifecycleModule {
    @Override
    public void configure() {
      listener().to(SingleVersionListener.class);
    }

    @Provides
    @Singleton
    LuceneChangeIndex getIndex(LuceneChangeIndex.Factory factory,
        SitePaths sitePaths) {
      Schema<ChangeData> schema = singleVersion != null
          ? ChangeSchemas.get(singleVersion)
          : ChangeSchemas.getLatest();
      return factory.create(schema, base);
    }
  }

  @Singleton
  static class SingleVersionListener implements LifecycleListener {
    private final IndexCollection indexes;
    private final LuceneChangeIndex index;

    @Inject
    SingleVersionListener(IndexCollection indexes,
        LuceneChangeIndex index) {
      this.indexes = indexes;
      this.index = index;
    }

    @Override
    public void start() {
      indexes.setSearchIndex(index);
      indexes.addWriteIndex(index);
    }

    @Override
    public void stop() {
      // Do nothing; indexes are closed by IndexCollection.
    }
  }
}
