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
  public static class PostFilterMagicLabelPredicate extends PostFilterPredicate<ChangeData> {
    private static class PostFilterMatcher extends Matcher {
      public PostFilterMatcher(LabelPredicate.Args args, MagicLabelVote magicLabelVote) {
        super(args, magicLabelVote);
      }

      @Override
      protected Predicate<ChangeData> numericPredicate(String label, short value) {
        return new EqualsLabelPredicates.PostFilterEqualsLabelPredicate(args, label, value);
      }
    }

    private final PostFilterMatcher matcher;

    public PostFilterMagicLabelPredicate(LabelPredicate.Args args, MagicLabelVote magicLabelVote) {
      super(ChangeQueryBuilder.FIELD_LABEL, magicLabelVote.formatLabel());
      this.matcher = new PostFilterMatcher(args, magicLabelVote);
    }

    @Override
    public boolean match(ChangeData changeData) {
      return matcher.match(changeData);
    }

    @Override
    public int getCost() {
      return 2;
    }
  }

  public static class IndexMagicLabelPredicate extends ChangeIndexPredicate {
    private static class IndexMatcher extends Matcher {
      public IndexMatcher(
          LabelPredicate.Args args, MagicLabelVote magicLabelVote, Account.Id account) {
        super(args, magicLabelVote, account);
      }

      @Override
      protected Predicate<ChangeData> numericPredicate(String label, short value) {
        return new EqualsLabelPredicates.IndexEqualsLabelPredicate(args, label, value, account);
      }
    }

    private final Matcher matcher;

    public IndexMagicLabelPredicate(LabelPredicate.Args args, MagicLabelVote magicLabelVote) {
      this(args, magicLabelVote, null);
    }

    public IndexMagicLabelPredicate(
        LabelPredicate.Args args, MagicLabelVote magicLabelVote, Account.Id account) {
      super(ChangeField.LABEL, magicLabelVote.formatLabel());
      this.matcher = new IndexMatcher(args, magicLabelVote, account);
    }

    @Override
    public boolean match(ChangeData changeData) {
      return matcher.match(changeData);
    }
  }

  private abstract static class Matcher {
    protected final LabelPredicate.Args args;
    protected final MagicLabelVote magicLabelVote;
    protected final Account.Id account;

    public Matcher(LabelPredicate.Args args, MagicLabelVote magicLabelVote) {
      this(args, magicLabelVote, null);
    }

    public Matcher(LabelPredicate.Args args, MagicLabelVote magicLabelVote, Account.Id account) {
      this.account = account;
      this.args = args;
      this.magicLabelVote = magicLabelVote;
    }

    public boolean match(ChangeData cd) {
      Change change = cd.change();
      if (change == null) {
        return false; // The change has disappeared.
      }

      Optional<ProjectState> project = args.projectCache.get(change.getDest().project());
      if (!project.isPresent()) {
        return false; // The project has disappeared.
      }

      LabelType labelType = type(project.get().getLabelTypes(), magicLabelVote.label());
      if (labelType == null) {
        return false; // Label is not defined by this project.
      }

      switch (magicLabelVote.value()) {
        case ANY:
          return matchAny(cd, labelType);
        case MIN:
          return matchNumeric(cd, magicLabelVote.label(), labelType.getMin().getValue());
        case MAX:
          return matchNumeric(cd, magicLabelVote.label(), labelType.getMax().getValue());
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
      return Predicate.or(predicates).asMatchable().match(changeData);
    }

    private boolean matchNumeric(ChangeData changeData, String label, short value) {
      return numericPredicate(label, value).asMatchable().match(changeData);
    }

    protected abstract Predicate<ChangeData> numericPredicate(String label, short value);
  }
}
