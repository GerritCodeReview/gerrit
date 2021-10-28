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

package com.google.gerrit.server.query.change;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.gerrit.index.query.QueryParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used to extract comma separated values in a predicate.
 *
 * <p>If tags for the values are present (e.g. "branch=jb_2.3,vote=approved") then the args are
 * placed in a map that maps tag to value (e.g., "branch" to "jb_2.3"). If no tag is present (e.g.
 * "jb_2.3,approved") then the args are placed into a positional list. Args may be mixed so some may
 * appear in the map and others in the positional list (e.g. "vote=approved,jb_2.3).
 */
public class PredicateArgs {
  private static final Pattern SPLIT_PATTERN = Pattern.compile("(>|>=|=|<|<=)([^=].*)$");

  public List<String> positional;
  public Map<String, ValOp> keyValue;

  enum Operator {
    EQUAL("="),
    GREATER_EQUAL(">="),
    GREATER(">"),
    LESS_EQUAL("<="),
    LESS("<");

    final String op;

    Operator(String op) {
      this.op = op;
    }
  };

  @AutoValue
  public abstract static class ValOp {
    abstract String value();

    abstract Operator operator();

    static ValOp create(String value, Operator operator) {
      return new AutoValue_PredicateArgs_ValOp(value, operator);
    }
  }

  /**
   * Parses query arguments into {@link #keyValue} and/or {@link #positional}..
   *
   * <p>Labels for these arguments should be kept in ChangeQueryBuilder as {@code ARG_ID_[argument
   * name]}.
   *
   * @param args arguments to be parsed
   */
  PredicateArgs(String args) throws QueryParseException {
    positional = new ArrayList<>();
    keyValue = new HashMap<>();

    for (String arg : Splitter.on(',').split(args)) {
      Matcher m = SPLIT_PATTERN.matcher(arg);

      if (!m.find()) {
        positional.add(arg);
      } else if (m.groupCount() == 2) {
        String key = arg.substring(0, m.start());
        String op = m.group(1);
        String val = m.group(2);
        if (!keyValue.containsKey(key)) {
          keyValue.put(key, ValOp.create(val, getOperator(op)));
        } else {
          throw new QueryParseException("Duplicate key " + key);
        }
      } else {
        throw new QueryParseException("Invalid arg " + arg);
      }
    }
  }

  private Operator getOperator(String operator) {
    switch (operator) {
      case "<":
        return Operator.LESS;
      case "<=":
        return Operator.LESS_EQUAL;
      case "=":
        return Operator.EQUAL;
      case ">=":
        return Operator.GREATER_EQUAL;
      case ">":
        return Operator.GREATER;
      default:
        throw new IllegalArgumentException("Invalid Operator " + operator);
    }
  }
}
