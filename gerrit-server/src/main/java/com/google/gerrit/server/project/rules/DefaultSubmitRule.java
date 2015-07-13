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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRule;
import com.google.gerrit.server.project.SubmitRuleFlags;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DefaultSubmitRule implements SubmitRule {

  @Override
  public List<SubmitRecord> evaluate(ChangeData cd, SubmitRuleFlags flags)
      throws RuleEvalException {
    SubmitRecord r = new SubmitRecord();
    // TODO: check flags like in the PrologRulesEvaluator or move the common
    // part into the SubmitRulesEvaluator
    List<LabelType> labelTypes;
    List<PatchSetApproval> approvals;
    try {
      labelTypes = cd.changeControl().getLabelTypes().getLabelTypes();
      approvals = cd.currentApprovals();
    } catch (OrmException e) {
      throw new RuleEvalException(e);
    }

    r.labels = new ArrayList<>(labelTypes.size());

    for (final LabelType t : labelTypes) {
      LabelCheck labelCheck = getLabelCheck(t.getFunctionName());
      if (labelCheck == null) {
        throw new RuleEvalException("Unkown function name: " + t.getFunctionName());
      }
      labelCheck.check(r, t, Iterables.filter(approvals,
          new Predicate<PatchSetApproval>() {
            @Override
            public boolean apply(PatchSetApproval input) {
              return input.getLabel().equals(t.getLabelId().get());
            }
      }));
    }

    r.status = SubmitRecord.Status.OK;
    for (SubmitRecord.Label l : r.labels) {
      if (l.status == SubmitRecord.Label.Status.OK
          || l.status == SubmitRecord.Label.Status.MAY) {
        continue;
      } else {
        r.status = SubmitRecord.Status.NOT_READY;
        break;
      }
    }

    return Collections.singletonList(r);
  }

  private LabelCheck getLabelCheck(String function) {
    if (function.equalsIgnoreCase("MaxWithBlock")) {
      return new MaxWithBlock();
    } else if (function.equalsIgnoreCase("AnyWithBlock")) {
      return new AnyWithBlock();
    } else if (function.equalsIgnoreCase("MaxNoBlock")) {
      return new MaxNoBlock();
    } else if (function.equalsIgnoreCase("NoOp")
        || function.equalsIgnoreCase("NoBlock")) {
      return new NoOp();
    } else {
      return null;
    }
  }

  private interface LabelCheck {
    void check(SubmitRecord r, LabelType t, Iterable<PatchSetApproval> approvals);
  }

  private static class MaxWithBlock implements LabelCheck {

    @Override
    public void check(SubmitRecord r, LabelType t,
        Iterable<PatchSetApproval> approvals) {
      SubmitRecord.Label l = new SubmitRecord.Label();
      l.label = t.getName();
      r.labels.add(l);

      boolean hasMax = false;
      boolean hasMin = false;
      for (PatchSetApproval a : approvals) {
        l.appliedBy = a.getAccountId();
        if (a.getValue() == t.getMax().getValue()) {
          hasMax = true;
        } else if (a.getValue() == t.getMin().getValue()) {
          hasMin = true;
          break;
        }
      }

      if (hasMin) {
        l.status = SubmitRecord.Label.Status.REJECT;
      } else if (hasMax) {
        l.status = SubmitRecord.Label.Status.OK;
      } else {
        l.status = SubmitRecord.Label.Status.NEED;
      }
    }
  }

  private static class AnyWithBlock implements LabelCheck {

    @Override
    public void check(SubmitRecord r, LabelType t,
        Iterable<PatchSetApproval> approvals) {
      SubmitRecord.Label l = new SubmitRecord.Label();
      l.label = t.getName();
      r.labels.add(l);

      boolean hasMin = false;
      for (PatchSetApproval a : approvals) {
        l.appliedBy = a.getAccountId();
        if (a.getValue() == t.getMin().getValue()) {
          hasMin = true;
          break;
        }
      }

      if (hasMin) {
        l.status = SubmitRecord.Label.Status.REJECT;
      } else {
        l.status = SubmitRecord.Label.Status.MAY;
      }
    }
  }

  private static class MaxNoBlock implements LabelCheck {

    @Override
    public void check(SubmitRecord r, LabelType t,
        Iterable<PatchSetApproval> approvals) {
      SubmitRecord.Label l = new SubmitRecord.Label();
      l.label = t.getName();
      r.labels.add(l);

      boolean hasMax = false;
      for (PatchSetApproval a : approvals) {
        l.appliedBy = a.getAccountId();
        if (a.getValue() == t.getMax().getValue()) {
          hasMax = true;
          break;
        }
      }

      if (hasMax) {
        l.status = SubmitRecord.Label.Status.OK;
      } else {
        l.status = SubmitRecord.Label.Status.NEED;
      }
    }
  }

  private static class NoOp implements LabelCheck {

    @Override
    public void check(SubmitRecord r, LabelType t,
        Iterable<PatchSetApproval> approvals) {
      SubmitRecord.Label l = new SubmitRecord.Label();
      l.label = t.getName();
      r.labels.add(l);

      for (PatchSetApproval a : approvals) {
        l.appliedBy = a.getAccountId();
        break;
      }

      l.status = SubmitRecord.Label.Status.MAY;
    }
  }
}