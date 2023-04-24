// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.truth.ThrowableSubject;
import java.util.Collection;
import java.util.Objects;
import org.junit.Test;

public class QueryBuilderTest {
  private static class TestPredicate extends Predicate<Object> {
    private final String field;
    private final String value;

    TestPredicate(String field, String value) {
      this.field = field;
      this.value = value;
    }

    @Override
    public Predicate<Object> copy(Collection<? extends Predicate<Object>> children) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, value);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof TestPredicate)) {
        return false;
      }
      TestPredicate p = (TestPredicate) o;
      return Objects.equals(field, p.field) && Objects.equals(value, p.value);
    }
  }

  private static class TestQueryBuilder extends QueryBuilder<Object, TestQueryBuilder> {
    TestQueryBuilder() {
      super(new QueryBuilder.Definition<>(TestQueryBuilder.class), null);
    }

    @Operator
    @SuppressWarnings("unused")
    public Predicate<Object> a(String value) {
      return new TestPredicate("a", value);
    }
  }

  @Test
  public void fieldNameAndValue() throws Exception {
    assertThat(parse("a:foo")).isEqualTo(new TestPredicate("a", "foo"));
  }

  @Test
  public void fieldWithParenthesizedValues() throws Exception {
    assertThatParseException("a:(foo bar)").hasMessageThat().contains("no viable alternative");
  }

  @Test
  public void fieldNameAndValueThatLooksLikeFieldNameColonValue() throws Exception {
    assertThat(parse("a:foo:bar")).isEqualTo(new TestPredicate("a", "foo:bar"));
  }

  @Test
  public void fieldNameAndValueThatLooksLikeWordColonValue() throws Exception {
    assertThat(parse("a:*:bar")).isEqualTo(new TestPredicate("a", "*:bar"));
  }

  @Test
  public void fieldNameAndValueWithMultipleColons() throws Exception {
    assertThat(parse("a:*:*:*")).isEqualTo(new TestPredicate("a", "*:*:*"));
  }

  @Test
  public void exactPhraseWithQuotes() throws Exception {
    assertThat(parse("a:\"foo bar\"")).isEqualTo(new TestPredicate("a", "foo bar"));
  }

  @Test
  public void exactPhraseWithQuotesAndColon() throws Exception {
    assertThat(parse("a:\"foo:bar\"")).isEqualTo(new TestPredicate("a", "foo:bar"));
  }

  @Test
  public void exactPhraseWithBraces() throws Exception {
    assertThat(parse("a:{foo bar}")).isEqualTo(new TestPredicate("a", "foo bar"));
  }

  @Test
  public void exactPhraseWithBracesAndColon() throws Exception {
    assertThat(parse("a:{foo:bar}")).isEqualTo(new TestPredicate("a", "foo:bar"));
  }

  private static Predicate<Object> parse(String query) throws Exception {
    return new TestQueryBuilder().parse(query);
  }

  private static ThrowableSubject assertThatParseException(String query) {
    try {
      new TestQueryBuilder().parse(query);
      throw new AssertionError("expected QueryParseException for " + query);
    } catch (QueryParseException e) {
      return assertThat(e);
    }
  }
}
