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

package com.google.gerrit.plugins.checks.acceptance;

import com.google.gerrit.plugins.checks.Module;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckOperations;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckOperationsImpl;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperations;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerOperationsImpl;
import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {
  @Override
  public void configure() {
    install(new Module());

    // Only add bindings here that are specifically required for tests, in order to keep the Guice
    // setup in tests as realistic as possible by delegating to the original module.
    bind(CheckerOperations.class).to(CheckerOperationsImpl.class);
    bind(CheckOperations.class).to(CheckOperationsImpl.class);
  }
}
