// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.submitrequirement.predicate;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A submit requirement predicate that allows checking for distinct voters across labels.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>[Label-Name1,Label-Name2],value=MAX,count=2
 *   <li>[Label-Name1,Label-Name2,Label-Name3],count=5
 * </ul>
 */
public class DistinctVotersPredicate extends SubmitRequirementPredicate {
  public interface Factory {
    DistinctVotersPredicate create(String value) throws QueryParseException;
  }

  private static final Pattern PATTERN =
      Pattern.compile(
          "\\[(?<labels>[^\\]]+)\\](,value=(?<value>MAX|MIN|-?[0-9]+))?,count\\>(?<count>[0-9]+)");

  private final ProjectCache projectCache;
  private final ImmutableList<String> labelNames;
  private final boolean enforceMaxVote;
  private final boolean enforceMinVote;
  private final Optional<Integer> enforceIntegerVote;
  private final int numDistinctVotes;

  @Inject
  public DistinctVotersPredicate(ProjectCache projectCache, @Assisted String value)
      throws QueryParseException {
    super("distinctvoters", value);
    this.projectCache = projectCache;
    Matcher m = PATTERN.matcher(value);
    if (!m.matches()) {
      throw new QueryParseException("input " + value + " invalid");
    }
    labelNames = ImmutableList.copyOf(Splitter.on(',').split(m.group("labels")));
    Integer votes = Ints.tryParse(m.group("count"));
    if (votes == null) {
      throw new QueryParseException("unable to parse number of required votes");
    }
    numDistinctVotes = votes + 1; // Regex has > sign
    if (m.group("value") != null) {
      enforceMaxVote = "MAX".equals(m.group("value"));
      enforceMinVote = "MIN".equals(m.group("value"));
      enforceIntegerVote = Optional.ofNullable(Ints.tryParse(m.group("value")));
    } else {
      enforceMaxVote = false;
      enforceMinVote = false;
      enforceIntegerVote = Optional.empty();
    }
  }

  @Override
  public boolean match(ChangeData cd) {
    ProjectState projectState =
        projectCache
            .get(cd.project())
            .orElseThrow(() -> new IllegalStateException("project absent " + cd.project()));
    return cd.currentApprovals().stream()
            .filter(psa -> filterPatchSetApproval(psa, projectState))
            .map(PatchSetApproval::accountId)
            .distinct()
            .count()
        >= numDistinctVotes;
  }

  @Override
  public int getCost() {
    return 1;
  }

  private boolean filterPatchSetApproval(PatchSetApproval psa, ProjectState projectState) {
    if (!labelNames.contains(psa.label())) {
      return false;
    }

    Optional<LabelType> labelType = projectState.getLabelTypes().byLabel(psa.labelId());
    if (labelType.isEmpty()) {
      // Label is not configured in this project
      return false;
    }

    if (enforceMaxVote && psa.value() != labelType.get().getMaxPositive()) {
      return false;
    }
    if (enforceMinVote && psa.value() != labelType.get().getMaxNegative()) {
      return false;
    }
    if (enforceIntegerVote.isPresent() && psa.value() != enforceIntegerVote.get()) {
      return false;
    }
    return true;
  }
}
