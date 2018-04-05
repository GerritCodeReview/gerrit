// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.rules;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicSet;

public class PrologModule extends FactoryModule {
  @Override
  protected void configure() {
    install(new EnvironmentModule());
    install(new RulesCache.Module());
    bind(PrologEnvironment.Args.class);
    factory(PrologRuleEvaluator.Factory.class);

    bind(SubmitRule.class).annotatedWith(Exports.named("PrologRule")).to(PrologRule.class);
  }

  static class EnvironmentModule extends FactoryModule {
    @Override
    protected void configure() {
      DynamicSet.setOf(binder(), PredicateProvider.class);
      factory(PrologEnvironment.Factory.class);
    }
  }
}
