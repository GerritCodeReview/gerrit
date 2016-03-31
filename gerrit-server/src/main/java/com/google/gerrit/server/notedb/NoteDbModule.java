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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

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
    factory(ChangeUpdate.Factory.class);
    factory(ChangeDraftUpdate.Factory.class);
    factory(DraftCommentNotes.Factory.class);
    factory(NoteDbUpdateManager.Factory.class);
    if (!useTestBindings) {
      bind(ChangeRebuilder.class).to(ChangeRebuilderImpl.class);
    } else {
      bind(ChangeRebuilder.class).toInstance(new ChangeRebuilder(null) {
        @Override
        public NoteDbChangeState rebuild(ReviewDb db, Change.Id changeId)
            throws OrmException {
          return null;
        }
      });
    }
  }
}
