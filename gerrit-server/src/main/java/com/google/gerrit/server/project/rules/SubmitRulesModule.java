// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.project.rules;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.project.SubmitRule;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitTypeRule;

public class SubmitRulesModule extends FactoryModule {

  @Override
  protected void configure() {
    factory(SubmitRuleEvaluator.Factory.class);

    DynamicMap.mapOf(binder(), SubmitRule.class);
    bind(SubmitRule.class)
        .annotatedWith(Exports.named("default-submit-rule"))
        .to(DefaultSubmitRule.class);

    DynamicMap.mapOf(binder(), SubmitTypeRule.class);
    bind(SubmitTypeRule.class)
        .annotatedWith(Exports.named("default-submit-type"))
        .to(DefaultSubmitTypeRule.class);
  }
}
