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

package com.google.gerrit.plugins.checks;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.plugins.checks.db.NoteDbCheckersModule;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;

public class Module extends FactoryModule {
  @Override
  protected void configure() {
    install(new NoteDbCheckersModule());

    DynamicSet.bind(binder(), CommitValidationListener.class)
        .to(CheckerCommitValidator.class)
        .in(SINGLETON);
    DynamicSet.bind(binder(), MergeValidationListener.class)
        .to(CheckerMergeValidator.class)
        .in(SINGLETON);
    DynamicSet.bind(binder(), RefOperationValidationListener.class)
        .to(CheckerRefOperationValidator.class)
        .in(SINGLETON);
  }
}
