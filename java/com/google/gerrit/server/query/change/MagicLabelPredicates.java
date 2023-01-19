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

import static com.google.gerrit.server.query.change.EqualsLabelPredicates.type;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.project.ProjectState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MagicLabelPredicates {
  public class PostFilterMagicLabelPredicate extends PostFilterPredicate<ChangeData> {
    public PostFilterMagicLabelPredicate() {
      super(ChangeQueryBuilder.FIELD_LABEL, magicLabelVote.formatLabel());
    }

    @Override
    public boolean match(ChangeData changeData) {
      return MagicLabelPredicates.this.match(changeData, this);
    }

    @Override
    public int getCost() {
      return 2;
    }

    protected Predicate<ChangeData> numericPredicate(String label, short value) {
      return new EqualsLabelPredicates(args, label, value).new PostFilterEqualsLabelPredicate();
    }
  }

  public class IndexMagicLabelPredicate extends ChangeIndexPredicate {
    public IndexMagicLabelPredicate() {
      super(ChangeField.LABEL, magicLabelVote.formatLabel());
    }

    @Override
    public boolean match(ChangeData changeData) {
      return MagicLabelPredicates.this.match(changeData, this);
    }

    protected Predicate<ChangeData> numericPredicate(String label, short value) {
      return new EqualsLabelPredicates(args, label, value, account).new IndexEqualsLabelPredicate();
    }
  }

  protected final LabelPredicate.Args args;
  private final MagicLabelVote magicLabelVote;
  private final Account.Id account;

  public MagicLabelPredicates(LabelPredicate.Args args, MagicLabelVote magicLabelVote) {
    this(args, magicLabelVote, null);
  }

  public MagicLabelPredicates(
      LabelPredicate.Args args, MagicLabelVote magicLabelVote, Account.Id account) {
    this.account = account;
    this.args = args;
    this.magicLabelVote = magicLabelVote;
  }

  protected boolean match(ChangeData changeData, Predicate<ChangeData> magicLabelPredicate) {
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
        return matchAny(changeData, labelType, magicLabelPredicate);
      case MIN:
        return matchNumeric(
            changeData, magicLabelVote.label(), labelType.getMin().getValue(), magicLabelPredicate);
      case MAX:
        return matchNumeric(
            changeData, magicLabelVote.label(), labelType.getMax().getValue(), magicLabelPredicate);
    }

    throw new IllegalStateException("Unsupported magic label value: " + magicLabelVote.value());
  }

  protected boolean matchAny(
      ChangeData changeData, LabelType labelType, Predicate<ChangeData> magicLabelPredicate) {
    List<Predicate<ChangeData>> predicates = new ArrayList<>();
    for (LabelValue labelValue : labelType.getValues()) {
      if (labelValue.getValue() != 0) {
        predicates.add(
            numericPredicate(labelType.getName(), labelValue.getValue(), magicLabelPredicate));
      }
    }
    return Predicate.or(predicates).asMatchable().match(changeData);
  }

  protected boolean matchNumeric(
      ChangeData changeData, String label, short value, Predicate<ChangeData> magicLabelPredicate) {
    return numericPredicate(label, value, magicLabelPredicate).asMatchable().match(changeData);
  }

  protected Predicate<ChangeData> numericPredicate(
      String label, short value, Predicate<ChangeData> magicLabelPredicate) {
    if (magicLabelPredicate instanceof IndexMagicLabelPredicate) {
      return ((IndexMagicLabelPredicate) magicLabelPredicate).numericPredicate(label, value);
    }
    if (magicLabelPredicate instanceof PostFilterMagicLabelPredicate) {
      return ((PostFilterMagicLabelPredicate) magicLabelPredicate).numericPredicate(label, value);
    }
    throw new IllegalStateException("Unsupported MagicLabelPredicate: " + magicLabelPredicate);
  }
}
