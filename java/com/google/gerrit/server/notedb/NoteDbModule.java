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

package com.google.gerrit.server.notedb;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.NoteDbUpdateManager.Result;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilderImpl;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import org.eclipse.jgit.lib.Config;

public class NoteDbModule extends FactoryModule {
  private final Config cfg;
  private final boolean useTestBindings;

  static NoteDbModule forTest(Config cfg) {
    return new NoteDbModule(cfg, true);
  }

  public NoteDbModule(Config cfg) {
    this(cfg, false);
  }

  private NoteDbModule(Config cfg, boolean useTestBindings) {
    this.cfg = cfg;
    this.useTestBindings = useTestBindings;
  }

  @Override
  public void configure() {
    factory(ChangeDraftUpdate.Factory.class);
    factory(ChangeUpdate.Factory.class);
    factory(DeleteCommentRewriter.Factory.class);
    factory(DraftCommentNotes.Factory.class);
    factory(NoteDbUpdateManager.Factory.class);
    factory(RobotCommentNotes.Factory.class);
    factory(RobotCommentUpdate.Factory.class);

    if (!useTestBindings) {
      install(ChangeNotesCache.module());
      if (cfg.getBoolean("noteDb", null, "testRebuilderWrapper", false)) {
        // Yes, another variety of test bindings with a different way of
        // configuring it.
        bind(ChangeRebuilder.class).to(TestChangeRebuilderWrapper.class);
      } else {
        bind(ChangeRebuilder.class).to(ChangeRebuilderImpl.class);
      }
    } else {
      bind(ChangeRebuilder.class)
          .toInstance(
              new ChangeRebuilder(null) {
                @Override
                public Result rebuild(ReviewDb db, Change.Id changeId) {
                  return null;
                }

                @Override
                public Result rebuildEvenIfReadOnly(ReviewDb db, Id changeId) {
                  return null;
                }

                @Override
                public Result rebuild(NoteDbUpdateManager manager, ChangeBundle bundle) {
                  return null;
                }

                @Override
                public NoteDbUpdateManager stage(ReviewDb db, Change.Id changeId) {
                  return null;
                }

                @Override
                public Result execute(
                    ReviewDb db, Change.Id changeId, NoteDbUpdateManager manager) {
                  return null;
                }

                @Override
                public void buildUpdates(NoteDbUpdateManager manager, ChangeBundle bundle) {
                  // Do nothing.
                }

                @Override
                public void rebuildReviewDb(ReviewDb db, Project.NameKey project, Id changeId) {
                  // Do nothing.
                }
              });
      bind(new TypeLiteral<Cache<ChangeNotesCache.Key, ChangeNotesState>>() {})
          .annotatedWith(Names.named(ChangeNotesCache.CACHE_NAME))
          .toInstance(CacheBuilder.newBuilder().<ChangeNotesCache.Key, ChangeNotesState>build());
    }
  }
}
