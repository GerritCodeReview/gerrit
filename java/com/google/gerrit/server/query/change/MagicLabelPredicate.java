// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.project.ProjectState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MagicLabelPredicate extends ChangeIndexPredicate {
  protected final LabelPredicate.Args args;
  private final MagicLabelVote magicLabelVote;
  private final Account.Id account;
  @Nullable private final Integer count;

  public MagicLabelPredicate(
      LabelPredicate.Args args,
      MagicLabelVote magicLabelVote,
      Account.Id account,
      @Nullable Integer count) {
    super(
        ChangeField.LABEL_SPEC,
        ChangeField.formatLabel(
            magicLabelVote.label(), magicLabelVote.value().name(), account, count));
    this.account = account;
    this.args = args;
    this.magicLabelVote = magicLabelVote;
    this.count = count;
  }

  @Override
  public boolean match(ChangeData changeData) {
    Change change = changeData.change();
    if (change == null) {
      // The change has disappeared.
      //
      return false;
    }

    Optional<ProjectState> project = args.projectCache.get(change.getDest().project());
    if (!project.isPresent()) {
      // The project has disappeared.
      //
      return false;
    }

    LabelType labelType = type(project.get().getLabelTypes(), magicLabelVote.label());
    if (labelType == null) {
      return false; // Label is not defined by this project.
    }

    switch (magicLabelVote.value()) {
      case ANY:
        return matchAny(changeData, labelType);
      case MIN:
        return matchNumeric(changeData, magicLabelVote.label(), labelType.getMin().getValue());
      case MAX:
        return matchNumeric(changeData, magicLabelVote.label(), labelType.getMax().getValue());
    }

    throw new IllegalStateException("Unsupported magic label value: " + magicLabelVote.value());
  }

  private boolean matchAny(ChangeData changeData, LabelType labelType) {
    List<Predicate<ChangeData>> predicates = new ArrayList<>();
    for (LabelValue labelValue : labelType.getValues()) {
      if (labelValue.getValue() != 0) {
        predicates.add(numericPredicate(labelType.getName(), labelValue.getValue()));
      }
    }
    return or(predicates).asMatchable().match(changeData);
  }

  private boolean matchNumeric(ChangeData changeData, String label, short value) {
    return numericPredicate(label, value).match(changeData);
  }

  private EqualsLabelPredicate numericPredicate(String label, short value) {
    return new EqualsLabelPredicate(args, label, value, account, count);
  }

  @Nullable
  protected static LabelType type(LabelTypes types, String toFind) {
    if (types.byLabel(toFind).isPresent()) {
      return types.byLabel(toFind).get();
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getName())) {
        return lt;
      }
    }
    return null;
  }
}
