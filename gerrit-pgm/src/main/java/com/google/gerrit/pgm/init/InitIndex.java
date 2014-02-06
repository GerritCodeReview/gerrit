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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.InitUtil.die;
import static com.google.gerrit.pgm.init.InitUtil.toURI;

import com.google.common.collect.Sets;
import com.google.gerrit.lucene.LuceneChangeIndex;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.TrackingFooter;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.patch.IntraLineDiff;
import com.google.gerrit.server.patch.IntraLineDiffKey;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.SortedSet;

/** Initialize the {@code index} configuration section. */
@Singleton
class InitIndex implements InitStep {
  private final ConsoleUI ui;
  private final Section index;
  private final SitePaths site;
  private final InitFlags initFlags;

  @Inject
  InitIndex(ConsoleUI ui,
      Section.Factory sections,
      SitePaths site,
      InitFlags initFlags) {
    this.ui = ui;
    this.index = sections.get("index", null);
    this.site = site;
    this.initFlags = initFlags;
  }

  public void run() throws IOException {
    ui.header("Index");

    IndexType type = index.select("Type", "type", IndexType.LUCENE);
    if (type == IndexType.SOLR) {
      String url = index.string("Solr Index URL", "url", "localhost:8983");
      try {
        toURI(url);
      } catch (URISyntaxException e) {
        throw die("invalid index.url");
      }
    }
    if (site.isNew && type == IndexType.LUCENE) {
      createLuceneIndex();
    } else {
      final String message = String.format(
        "\nThe index must be %sbuilt before starting Gerrit:\n"
        + "  java -jar gerrit.war reindex -d site_path\n",
        site.isNew ? "" : "re");
      ui.message(message);
      initFlags.autoStart = false;
    }
  }

  private void createLuceneIndex() throws IOException {
    Injector injector = Guice.createInjector(
      new LuceneIndexModule(ChangeSchemas.getLatest().getVersion(), 0, null),
      new MockIndexSupportModule());

    ChangeIndex index = injector.getInstance(LuceneChangeIndex.class);
    index.markReady(true);
    index.close();
  }

  private class MockIndexSupportModule extends FactoryModule {
    @Override
    protected void configure() {
      bind(SitePaths.class).toInstance(site);
      factory(ChangeData.Factory.class);
    }

    @Provides @GerritServerConfig Config getConfig() {
      return new Config();
    }

    @Provides TrackingFooters newTrackingFooters() {
      return new TrackingFooters(Collections.<TrackingFooter> emptyList());
    }

    @Provides ReviewDb getReviewDb() {
      throw new ProvisionException("database not initialized");
    }

    @Provides SchemaFactory<ReviewDb> getSchemaFactory() {
      return new SchemaFactory<ReviewDb>() {
        @Override
        public ReviewDb open() throws OrmException {
          return getReviewDb();
        }
      };
    }

    @Provides GitRepositoryManager getGitRepositoryManager() {
      return new GitRepositoryManager() {
        @Override
        public Repository openRepository(Project.NameKey name)
            throws RepositoryNotFoundException{
          throw new RepositoryNotFoundException(name.get());
        }

        @Override
        public Repository createRepository(Project.NameKey name)
            throws RepositoryNotFoundException {
          throw new RepositoryNotFoundException(name.get());
        }

        @Override
        public SortedSet<Project.NameKey> list() {
          return Sets.newTreeSet();
        }

        @Override
        public String getProjectDescription(Project.NameKey name) {
          return null;
        }

        @Override
        public void setProjectDescription(NameKey name, String description) {
        }
      };
    }

    @Provides PatchListCache newPatchListCache() {
      return new PatchListCache() {
        @Override
        public PatchList get(PatchListKey key)
            throws PatchListNotAvailableException {
          throw new PatchListNotAvailableException("new site, no changes");
        }

        @Override
        public PatchList get(Change change, PatchSet patchSet)
            throws PatchListNotAvailableException {
          throw new PatchListNotAvailableException("new site, no changes");
        }

        @Override
        public IntraLineDiff getIntraLineDiff(IntraLineDiffKey key) {
          return null;
        }
      };
    }
  }

  @Override
  public void postRun() throws Exception {
  }
}
