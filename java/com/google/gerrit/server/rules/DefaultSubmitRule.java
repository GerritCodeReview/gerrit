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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Java implementation of Gerrit's default pre-submit rules behavior: check if the labels have the
 * correct values, according to the {@link LabelFunction} they are attached to.
 *
 * <p>As this behavior is also implemented by the Prolog rules system, we skip it if at least one
 * project in the hierarchy has a {@code rules.pl} file.
 */
@Singleton
public final class DefaultSubmitRule implements SubmitRule {
  public static class DefaultSubmitRuleModule extends FactoryModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("DefaultRules"))
          .to(DefaultSubmitRule.class);
    }
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.status = SubmitRecord.Status.OK;

    List<LabelType> labelTypes = cd.getLabelTypes().getLabelTypes();
    List<PatchSetApproval> approvals = cd.currentApprovals();
    submitRecord.labels = new ArrayList<>(labelTypes.size());

    for (LabelType t : labelTypes) {
      LabelFunction labelFunction = t.getFunction();
      checkState(
          labelFunction != null,
          "Unable to find the LabelFunction for label %s, change %s",
          t.getName(),
          cd.getId());

      Collection<PatchSetApproval> approvalsForLabel = getApprovalsForLabel(approvals, t);
      SubmitRecord.Label label = labelFunction.check(t, approvalsForLabel);
      submitRecord.labels.add(label);

      switch (label.status) {
        case OK:
        case MAY:
          break;

        case NEED:
        case REJECT:
        case IMPOSSIBLE:
          submitRecord.status = SubmitRecord.Status.NOT_READY;
          break;
      }
    }

    return Optional.of(submitRecord);
  }

  private static List<PatchSetApproval> getApprovalsForLabel(
      List<PatchSetApproval> approvals, LabelType t) {
    return approvals.stream()
        .filter(input -> input.label().equals(t.getLabelId().get()))
        .collect(toImmutableList());
  }
}
