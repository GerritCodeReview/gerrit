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

import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Java implementation of Gerrit's default pre-submti rules behavior: check if the labels have the
 * correct values, according to the {@link LabelFunction} they are attached to.
 *
 * <p>As this behavior is also implemented by the Prolog rules system, we skip it if at least one
 * project in the hierarchy has a {@code rules.pl} file.
 */
public final class DefaultSubmitRule implements SubmitRule {
  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("DefaultRules"))
          .to(DefaultSubmitRule.class);
    }
  }

  private ProjectCache cache;

  @Inject
  public DefaultSubmitRule(ProjectCache cache) {
    this.cache = cache;
  }

  @Override
  public Collection<SubmitRecord> evaluate(ChangeData cd, SubmitRuleOptions options) {
    ProjectState projectState = cache.get(cd.project());

    // In case at least one project has a rules.pl file, we let Prolog handle it.
    // The Prolog rules engine will also handle the labels for us.
    if (projectState == null || projectState.hasPrologRules()) {
      return Collections.emptyList();
    }

    SubmitRecord r = new SubmitRecord();
    List<LabelType> labelTypes;
    List<PatchSetApproval> approvals;
    try {
      labelTypes = cd.getLabelTypes().getLabelTypes();
      approvals = cd.currentApprovals();
    } catch (OrmException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }

    r.labels = new ArrayList<>(labelTypes.size());

    for (final LabelType t : labelTypes) {
      LabelFunction labelFunction = t.getFunction();
      if (labelFunction == null) {
        continue;
      }

      Collection<PatchSetApproval> _approvals = getApprovalsForLabel(approvals, t);
      r.labels.add(labelFunction.check(t, _approvals));
    }

    return Collections.singletonList(r);
  }

  private static List<PatchSetApproval> getApprovalsForLabel(
      List<PatchSetApproval> approvals, LabelType t) {
    return approvals
        .stream()
        .filter(input -> input.getLabel().equals(t.getLabelId().get()))
        .collect(Collectors.toList());
  }
}
