// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.PredicateResult;
import com.google.gerrit.extensions.common.EvaluateChangeQueryExpressionResultInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

/** REST endpoint to evaluate whether a change query expression matches the change. */
public class EvaluateChangeQueryExpression implements RestReadView<ChangeResource> {
  @Option(
      name = "--expression",
      usage = "Change query expression for which it should be checked if the change matches.")
  public String expression;

  @Option(
      name = "--use-index",
      usage =
          "Whether the change query expression should be evaluated against the change state in the"
              + " index.")
  public boolean useIndex;

  private final Provider<ChangeQueryBuilder> queryBuilder;
  private final Provider<InternalChangeQuery> internalChangeQuery;

  @Inject
  EvaluateChangeQueryExpression(
      Provider<ChangeQueryBuilder> queryBuilder,
      Provider<InternalChangeQuery> internalChangeQuery) {
    this.queryBuilder = queryBuilder;
    this.internalChangeQuery = internalChangeQuery;
  }

  @Override
  public Response<EvaluateChangeQueryExpressionResultInfo> apply(ChangeResource rsrc)
      throws BadRequestException {
    if (Strings.isNullOrEmpty(expression)) {
      throw new BadRequestException("expression is required");
    }

    Predicate<ChangeData> predicate = parseExpression(expression);
    PredicateResult predicateResult = getChangeData(rsrc).evaluatePredicateTree(predicate);
    return Response.ok(toInfo(predicateResult));
  }

  private ChangeData getChangeData(ChangeResource rsrc) {
    if (useIndex) {
      // Loading the change from the index populates ChangeData with the data that is stored in the
      // index, including submit requirement results.
      List<ChangeData> changeDatas =
          internalChangeQuery.get().byProjectChangeNumber(rsrc.getProject(), rsrc.getId());
      checkState(
          changeDatas.size() == 1,
          "Got %s matches for change %s, expected 1",
          changeDatas.size() == 1,
          rsrc.getId());
      return Iterables.getOnlyElement(changeDatas);
    }

    // The ChangeData in ChangeResource has been loaded from NoteDb. It doesn't contain submit
    // requirement results yet. Submit requirement results are loaded lazily by executing the submit
    // requirements. Executing the submit requirements is rather expensive. This means if an
    // expression is evaluated that requires checking if the change is submittable (e.g.
    // "is:submittable") this is slower than using the change data from the index where submit
    // requirement results are already present.
    return rsrc.getChangeData();
  }

  private Predicate<ChangeData> parseExpression(String expression) throws BadRequestException {
    try {
      return queryBuilder.get().parse(expression);
    } catch (QueryParseException e) {
      throw new BadRequestException(String.format("invalid query expression: %s", e.getMessage()));
    }
  }

  private static EvaluateChangeQueryExpressionResultInfo toInfo(PredicateResult predicateResult) {
    EvaluateChangeQueryExpressionResultInfo info = new EvaluateChangeQueryExpressionResultInfo();
    info.status = predicateResult.status();
    info.passingAtoms = predicateResult.getPassingAtoms();
    info.failingAtoms = predicateResult.getFailingAtoms();
    ImmutableMap<String, String> atomExplanations =
        predicateResult.getAtomExplanations().entrySet().stream()
            .filter(e -> !Strings.isNullOrEmpty(e.getValue()))
            .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    info.atomExplanations = !atomExplanations.isEmpty() ? atomExplanations : null;
    return info;
  }
}
