// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.db;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.inject.Provides;

/** Bind NoteDb implementation for storage layer. */
public class NoteDbCheckersModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(Checkers.class).to(NoteDbCheckers.class);
    bind(Checks.class).to(NoteDbChecks.class);
    factory(CheckNotes.Factory.class);
    factory(NoteDbCheckersUpdate.Factory.class);
    factory(NoteDbChecksUpdate.Factory.class);
  }

  @Provides
  @ServerInitiated
  CheckersUpdate provideServerInitiatedCheckersUpdate(
      NoteDbCheckersUpdate.Factory checkersUpdateFactory) {
    return checkersUpdateFactory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  CheckersUpdate provideUserInitiatedCheckersUpdate(
      NoteDbCheckersUpdate.Factory checkersUpdateFactory, IdentifiedUser currentUser) {
    return checkersUpdateFactory.create(currentUser);
  }

  @Provides
  @ServerInitiated
  ChecksUpdate provideServerInitiatedChecksUpdate(NoteDbChecksUpdate.Factory factory) {
    return factory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  ChecksUpdate provideUserInitiatedChecksUpdate(
      NoteDbChecksUpdate.Factory factory, IdentifiedUser currentUser) {
    return factory.create(currentUser);
  }
}
