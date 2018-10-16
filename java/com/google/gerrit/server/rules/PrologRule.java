// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;

@Singleton
public class PrologRule implements SubmitRule {
  private final PrologRuleEvaluator.Factory factory;
  private final ProjectCache projectCache;

  @Inject
  private PrologRule(PrologRuleEvaluator.Factory factory, ProjectCache projectCache) {
    this.factory = factory;
    this.projectCache = projectCache;
  }

  @Override
  public Collection<SubmitRecord> evaluate(ChangeData cd, SubmitRuleOptions opts) {
    ProjectState projectState = projectCache.get(cd.project());
    // We only want to run the Prolog engine if we have at least one rules.pl file to use.
    if ((projectState == null || !projectState.hasPrologRules()) && opts.rule() == null) {
      return Collections.emptyList();
    }

    return getEvaluator(cd, opts).evaluate();
  }

  private PrologRuleEvaluator getEvaluator(ChangeData cd, SubmitRuleOptions opts) {
    return factory.create(cd, opts);
  }

  public SubmitTypeRecord getSubmitType(ChangeData cd, SubmitRuleOptions opts) {
    return getEvaluator(cd, opts).getSubmitType();
  }
}
