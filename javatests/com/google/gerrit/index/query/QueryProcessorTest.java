// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.index.query;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexRewriter;
import com.google.gerrit.index.SchemaDefinitions;
import java.util.function.IntSupplier;
import org.junit.Test;

public class QueryProcessorTest {

  private boolean noLimit = false;

  private int userQueryLimit = 1000;

  private int userProvidedLimit = 0;

  private int maxLimit = 5000;

  private int defaultLimit = Integer.MAX_VALUE;

  private String limitField = null;

  private boolean enforceVisibility = true;

  public QueryProcessor createProcessor() {
    QueryProcessor.Metrics metrics = mock(QueryProcessor.Metrics.class);
    SchemaDefinitions schemaDef = mock(SchemaDefinitions.class);
    IndexConfig indexConfig =
        IndexConfig.builder().maxLimit(maxLimit).defaultLimit(defaultLimit).build();
    IndexCollection indexes = mock(IndexCollection.class);
    IndexRewriter rewriter = mock(IndexRewriter.class);
    IntSupplier userQueryLimit =
        new IntSupplier() {
          @Override
          public int getAsInt() {
            return QueryProcessorTest.this.userQueryLimit;
          }
        };

    QueryProcessor processor =
        new QueryProcessor<String>(
            metrics, schemaDef, indexConfig, indexes, rewriter, limitField, userQueryLimit) {
          @Override
          protected Predicate<String> enforceVisibility(Predicate<String> pred) {
            return Predicate.any();
          }

          @Override
          protected String formatForLogging(String o) {
            return "";
          }

          @Override
          protected int getIndexSize() {
            return 0;
          }

          @Override
          protected int getBatchSize() {
            return 0;
          }
        };
    processor.setNoLimit(noLimit);
    processor.setUserProvidedLimit(userProvidedLimit, /* applyDefaultLimit */ true);
    processor.enforceVisibility(enforceVisibility);
    return processor;
  }

  @Test
  public void getEffectiveLimit() {
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(1000);
  }

  @Test
  public void getEffectiveLimit_UserQueryLimit() {
    userQueryLimit = 314;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(314);
  }

  @Test
  public void getEffectiveLimit_UserProvidedLimit() {
    userProvidedLimit = 314;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(314);
  }

  @Test
  public void getEffectiveLimit_MaxLimit() {
    maxLimit = 314;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(314);
  }

  @Test
  public void getEffectiveLimit_DefaultLimit() {
    defaultLimit = 314;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(314);

    // Prefer user provided limit over default limit.
    userProvidedLimit = 333;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(333);
  }

  @Test
  public void getEffectiveLimit_NoVisibilityChecks() {
    // These two limits do not take effect, if enforceVisibility is false (i.e. internal query).
    userQueryLimit = 314;
    defaultLimit = 314;
    enforceVisibility = false;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(maxLimit);
  }

  @Test
  public void getEffectiveLimit_LimitField() throws QueryParseException {
    limitField = "limit";
    assertThat(createProcessor().getEffectiveLimit(new LimitPredicate(limitField, 314)))
        .isEqualTo(314);
  }

  @Test
  public void getEffectiveLimit_SmallestWins() throws QueryParseException {
    limitField = "limit";
    int[] limits = {271, 314, 499, 666};

    LimitPredicate p = null;
    for (int i = 0; i < 4; i++) {
      userProvidedLimit = limits[0];
      userQueryLimit = limits[1];
      maxLimit = limits[2];
      p = new LimitPredicate(limitField, limits[3]);

      // "rotate" the array of limits
      int l = limits[0];
      for (int j = 0; j < 3; j++) limits[j] = limits[j + 1];
      limits[3] = l;

      assertThat(createProcessor().getEffectiveLimit(p)).isEqualTo(271);
    }
  }

  @Test
  public void getEffectiveLimit_NoLimit() {
    noLimit = true;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(Integer.MAX_VALUE);

    // noLimit has precedence over all other limits
    userProvidedLimit = 1;
    userQueryLimit = 1;
    maxLimit = 1;
    defaultLimit = 1;
    assertThat(createProcessor().getEffectiveLimit(Predicate.any())).isEqualTo(Integer.MAX_VALUE);
  }
}
