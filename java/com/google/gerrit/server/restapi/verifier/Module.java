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

package com.google.gerrit.server.restapi.verifier;

import static com.google.gerrit.server.restapi.verifier.VerifierResource.VERIFIER_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gerrit.server.verifier.VerifiersUpdateFactory;
import com.google.gerrit.server.verifier.db.NoteDbVerifiers;
import com.google.gerrit.server.verifier.db.NoteDbVerifiersUpdate;
import com.google.gerrit.server.verifier.db.NoteDbVerifiersUpdateFactory;
import com.google.inject.Provides;

public class Module extends RestApiModule {
  @Override
  protected void configure() {
    bind(VerifiersCollection.class);

    DynamicMap.mapOf(binder(), VERIFIER_KIND);

    create(VERIFIER_KIND).to(CreateVerifier.class);
    get(VERIFIER_KIND).to(GetVerifier.class);

    bind(Verifiers.class).to(NoteDbVerifiers.class);
    bind(VerifiersUpdateFactory.class).to(NoteDbVerifiersUpdateFactory.class);
    factory(NoteDbVerifiersUpdate.Factory.class);
  }

  @Provides
  @ServerInitiated
  VerifiersUpdate provideServerInitiatedGroupsUpdate(
      VerifiersUpdateFactory verifiersUpdateFactory) {
    return verifiersUpdateFactory.create(null);
  }

  @Provides
  @UserInitiated
  VerifiersUpdate provideUserInitiatedGroupsUpdate(
      VerifiersUpdateFactory verifiersUpdateFactory, IdentifiedUser currentUser) {
    return verifiersUpdateFactory.create(currentUser);
  }
}
