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

package com.google.gerrit.server.verifier.db;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.inject.Provides;

/** Bind NoteDb implementation for verifier storage layer. */
public class NoteDbVerifiersModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(Verifiers.class).to(NoteDbVerifiers.class);
    factory(NoteDbVerifiersUpdate.Factory.class);
  }

  @Provides
  @ServerInitiated
  VerifiersUpdate provideServerInitiatedGroupsUpdate(
      NoteDbVerifiersUpdate.Factory verifiersUpdateFactory) {
    return verifiersUpdateFactory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  VerifiersUpdate provideUserInitiatedGroupsUpdate(
      NoteDbVerifiersUpdate.Factory verifiersUpdateFactory, IdentifiedUser currentUser) {
    return verifiersUpdateFactory.create(currentUser);
  }
}
