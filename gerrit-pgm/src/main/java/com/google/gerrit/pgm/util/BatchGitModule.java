// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.pgm.util;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.DisabledChangeHooks;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidators;

/** Module for batch programs that need git access. */
public class BatchGitModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(ChangeHooks.class).to(DisabledChangeHooks.class);
    DynamicSet.setOf(binder(), GitReferenceUpdatedListener.class);
    DynamicSet.setOf(binder(), CommitValidationListener.class);
    factory(CommitValidators.Factory.class);
    install(new GitModule());
  }
}
