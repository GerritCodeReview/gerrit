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
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class NoteDbModule extends FactoryModule {
  private final boolean useTestBindings;

  static NoteDbModule forTest() {
    return new NoteDbModule(true);
  }

  public NoteDbModule() {
    this(false);
  }

  private NoteDbModule(boolean useTestBindings) {
    this.useTestBindings = useTestBindings;
  }

  @Override
  public void configure() {
    factory(ChangeDraftNotesUpdate.Factory.class);
    factory(ChangeUpdate.Factory.class);
    factory(DeleteCommentRewriter.Factory.class);
    factory(DraftCommentNotes.Factory.class);
    factory(NoteDbUpdateManager.Factory.class);
    factory(RobotCommentNotes.Factory.class);
    factory(RobotCommentUpdate.Factory.class);
    bind(StarredChangesUtil.class).to(StarredChangesUtilNoteDbImpl.class).in(Singleton.class);
    bind(DraftCommentsReader.class).to(DraftCommentsNotesReader.class).in(Singleton.class);

    if (!useTestBindings) {
      install(ChangeNotesCache.module());
    } else {
      bind(new TypeLiteral<Cache<ChangeNotesCache.Key, ChangeNotesState>>() {})
          .annotatedWith(Names.named(ChangeNotesCache.CACHE_NAME))
          .toInstance(CacheBuilder.newBuilder().build());
    }
  }
}
