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

import com.google.common.collect.ImmutableMultimap;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

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
    factory(ChangeUpdate.Factory.class);
    factory(ChangeDraftUpdate.Factory.class);
    factory(DraftCommentNotes.Factory.class);
    factory(NoteDbUpdateManager.Factory.class);
    install(ChangeNoteCache.module());
    if (!useTestBindings) {
      if (cfg.getBoolean("noteDb", null, "testRebuilderWrapper", false)) {
        // Yes, another variety of test bindings with a different way of
        // configuring it.
        bind(ChangeRebuilder.class).to(TestChangeRebuilderWrapper.class);
      } else {
        bind(ChangeRebuilder.class).to(ChangeRebuilderImpl.class);
      }
    } else {
      bind(ChangeRebuilder.class).toInstance(new ChangeRebuilder(null) {
        @Override
        public NoteDbChangeState rebuild(ReviewDb db, Change.Id changeId)
            throws OrmException {
          return null;
        }

        @Override
        public NoteDbChangeState rebuild(NoteDbUpdateManager manager,
            ChangeBundle bundle) throws NoSuchChangeException, IOException,
            OrmException, ConfigInvalidException {
          return null;
        }

        @Override
        public boolean rebuildProject(ReviewDb db,
            ImmutableMultimap<NameKey, Id> allChanges, NameKey project,
            Repository allUsersRepo) throws NoSuchChangeException, IOException,
            OrmException, ConfigInvalidException {
          return false;
        }
      });
    }
  }
}
