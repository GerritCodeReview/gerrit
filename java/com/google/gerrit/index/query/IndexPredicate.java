// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.StreamSupport;

/** Predicate that is mapped to a field in the index. */
public abstract class IndexPredicate<I> extends OperatorPredicate<I> implements Matchable<I> {
  /**
   * Text segmentation to be applied to both the query string and the indexed field for full-text
   * queries. This is inspired by http://unicode.org/reports/tr29/ which is what Lucene uses, but
   * complexity was reduced to the bare minimum at the cost of small discrepancies to the Unicode
   * spec.
   */
  private static final Splitter FULL_TEXT_SPLITTER = Splitter.on(CharMatcher.anyOf(" ,.-:\\/_=\n"));

  private final SchemaField<I, ?> def;

  protected IndexPredicate(SchemaField<I, ?> def, String value) {
    super(def.getName(), value);
    this.def = def;
  }

  protected IndexPredicate(SchemaField<I, ?> def, String name, String value) {
    super(name, value);
    this.def = def;
  }

  public SchemaField<I, ?> getField() {
    return def;
  }

  public FieldType<?> getType() {
    return def.getType();
  }

  /**
   * This method matches documents without calling an index subsystem. For primitive fields (e.g.
   * integer, long) , the matching logic is consistent across this method and all known index
   * implementations. For text fields (i.e. prefix and full-text) the semantics vary between this
   * implementation and known index implementations:
   * <li>Prefix: Lucene as well as {@link #match(Object)} matches terms as true prefixes (prefix:foo
   *     -> `foo bar` matches, but `baz foo bar` does not match). The index implementation at Google
   *     tokenizes both the query and the indexed text and matches tokens individually (prefix:fo ba
   *     -> `baz foo bar` matches).
   * <li>Full text: Lucene uses a {@code PhraseQuery} to search for terms in full text fields
   *     in-order. The index implementation at Google as well as {@link #match(Object)} tokenizes
   *     both the query and the indexed text and matches tokens individually.
   *
   * @return true if the predicate matches the provided {@code I}.
   */
  @Override
  public boolean match(I doc) {
    if (getField().isRepeatable()) {
      Iterable<?> values = (Iterable<?>) getField().get(doc);
      for (Object v : values) {
        if (matchesSingleObject(v)) {
          return true;
        }
      }
      return false;
    }
    return matchesSingleObject(getField().get(doc));
  }

  @Override
  public int getCost() {
    return 1;
  }

  private boolean matchesSingleObject(Object fieldValueFromObject) {
    String fieldTypeName = getField().getType().getName();
    if (fieldTypeName.equals(FieldType.INTEGER.getName())) {
      return Objects.equals(fieldValueFromObject, Ints.tryParse(value));
    } else if (fieldTypeName.equals(FieldType.EXACT.getName())) {
      return Objects.equals(fieldValueFromObject, value);
    } else if (fieldTypeName.equals(FieldType.LONG.getName())) {
      return Objects.equals(fieldValueFromObject, Longs.tryParse(value));
    } else if (fieldTypeName.equals(FieldType.PREFIX.getName())) {
      return String.valueOf(fieldValueFromObject).startsWith(value);
    } else if (fieldTypeName.equals(FieldType.FULL_TEXT.getName())) {
      ImmutableList<String> tokenizedField = tokenizeString(String.valueOf(fieldValueFromObject));
      ImmutableList<String> tokenizedValue = tokenizeString(value);
      return !tokenizedValue.isEmpty() && containsSublist(tokenizedField, tokenizedValue);
    } else if (fieldTypeName.equals(FieldType.STORED_ONLY.getName())) {
      throw new IllegalStateException("can't filter for storedOnly field " + getField().getName());
    } else if (fieldTypeName.equals(FieldType.TIMESTAMP.getName())) {
      throw new IllegalStateException("timestamp queries must be handled in subclasses");
    } else if (fieldTypeName.equals(FieldType.INTEGER_RANGE.getName())) {
      throw new IllegalStateException("integer range queries must be handled in subclasses");
    } else {
      throw new IllegalStateException("unrecognized field " + fieldTypeName);
    }
  }

  private static ImmutableList<String> tokenizeString(String value) {
    return StreamSupport.stream(
            FULL_TEXT_SPLITTER.split(value.toLowerCase(Locale.US)).spliterator(), false)
        .filter(s -> !s.trim().isEmpty())
        .collect(toImmutableList());
  }

  /**
   * Implementation of Knuth-Morris-Pratt algorithm for lists.
   *
   * <p>https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm
   */
  private static boolean containsSublist(
      ImmutableList<String> superlist, ImmutableList<String> sublist) {
    int[] prefix = new int[sublist.size()];
    for (int i = 1; i < sublist.size(); ++i) {
      int currentPrefix = prefix[i - 1];
      while (currentPrefix != 0 && !sublist.get(i).equals(sublist.get(currentPrefix))) {
        currentPrefix = prefix[currentPrefix - 1];
      }
      if (sublist.get(i).equals(sublist.get(currentPrefix))) {
        currentPrefix += 1;
      }
      prefix[i] = currentPrefix;
    }

    int currentPrefix = 0;
    for (int i = 0; i < superlist.size(); ++i) {
      while (currentPrefix != 0 && !superlist.get(i).equals(sublist.get(currentPrefix))) {
        currentPrefix = prefix[currentPrefix - 1];
      }
      if (superlist.get(i).equals(sublist.get(currentPrefix))) {
        ++currentPrefix;
        if (currentPrefix == sublist.size()) {
          return true;
        }
      }
    }

    return false;
  }
}
