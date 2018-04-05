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
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import org.eclipse.jgit.lib.ObjectId;

public class PrologModule extends CacheModule {
  @Override
  protected void configure() {
    install(new EnvironmentModule());
    bind(PrologEnvironment.Args.class);
    factory(PrologRuleEvaluator.Factory.class);

    bind(SubmitRule.class).annotatedWith(Exports.named("PrologRule")).to(PrologRule.class);

    cache(RulesCache.CACHE_NAME, ObjectId.class, PrologMachineCopy.class)
        // This cache is auxiliary to the project cache, so size it the same.
        .configName(ProjectCacheImpl.CACHE_NAME);
  }

  static class EnvironmentModule extends FactoryModule {
    @Override
    protected void configure() {
      DynamicSet.setOf(binder(), PredicateProvider.class);
      factory(PrologEnvironment.Factory.class);
    }
  }
}
