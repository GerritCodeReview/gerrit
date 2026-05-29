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

package com.google.gerrit.server.notedb;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.ChangeDraftUpdate;
import com.google.gerrit.server.ChangeDraftUpdateExecutor;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.inject.Singleton;

public class NoteDbDraftCommentsModule extends FactoryModule {
  @Override
  public void configure() {
    factory(ChangeDraftNotesUpdate.Factory.class);
    factory(ChangeDraftNotesUpdate.Executor.Factory.class);
    factory(DraftCommentNotes.Factory.class);

    bind(DraftCommentsReader.class).to(DraftCommentsNotesReader.class).in(Singleton.class);
    bind(ChangeDraftUpdate.ChangeDraftUpdateFactory.class).to(ChangeDraftNotesUpdate.Factory.class);
    bind(ChangeDraftUpdateExecutor.AbstractFactory.class)
        .to(ChangeDraftNotesUpdate.Executor.Factory.class)
        .in(Singleton.class);
  }
}
