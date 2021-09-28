// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.conditions;

import com.google.common.collect.Iterables;
import java.util.Collections;

/** Delayed evaluation of a boolean condition. */
public abstract class BooleanCondition {
  public static final BooleanCondition TRUE = new Value(true);
  public static final BooleanCondition FALSE = new Value(false);

  public static BooleanCondition valueOf(boolean a) {
    return a ? TRUE : FALSE;
  }

  public static BooleanCondition and(BooleanCondition a, BooleanCondition b) {
    return a == FALSE || b == FALSE ? FALSE : new And(a, b);
  }

  public static BooleanCondition and(boolean a, BooleanCondition b) {
    return and(valueOf(a), b);
  }

  public static BooleanCondition or(BooleanCondition a, BooleanCondition b) {
    return a == TRUE || b == TRUE ? TRUE : new Or(a, b);
  }

  public static BooleanCondition or(boolean a, BooleanCondition b) {
    return or(valueOf(a), b);
  }

  public static BooleanCondition not(BooleanCondition bc) {
    return bc == TRUE ? FALSE : bc == FALSE ? TRUE : new Not(bc);
  }

  BooleanCondition() {}

  /** Evaluates the condition and return its value. */
  public abstract boolean value();

  /**
   * Recursively collect all children of type {@code type}.
   *
   * @param type implementation type of the conditions to collect and return.
   * @return non-null, unmodifiable iteration of children of type {@code type}.
   */
  public abstract <T> Iterable<T> children(Class<T> type);

  /**
   * Reduce evaluation tree by cutting off branches that evaluate trivially and replacing them with
   * a leave note corresponding to the value the branch evaluated to.
   *
   * <p>
   *
   * <pre>{@code
   * Example 1 (T=True, F=False, C=non-trivial check):
   *      OR
   *     /  \    =>    T
   *    C   T
   * Example 2 (cuts off a not-trivial check):
   *      AND
   *     /  \    =>    F
   *    C   F
   * Example 3:
   *      AND
   *     /  \    =>    F
   *    T   F
   * }</pre>
   *
   * <p>There is no guarantee that the resulting tree is minimal. The only guarantee made is that
   * branches that evaluate trivially will be cut off and replaced by primitive values.
   */
  public abstract BooleanCondition reduce();

  /**
   * Check if the condition evaluates to either {@code true} or {@code false} without providing
   * additional information to the evaluation tree, e.g. through checks to a remote service such as
   * {@code PermissionBackend}.
   *
   * <p>In this case, the tree can be reduced to skip all non-trivial checks resulting in a
   * performance gain.
   */
  protected abstract boolean evaluatesTrivially();

  private static final class And extends BooleanCondition {
    private final BooleanCondition a;
    private final BooleanCondition b;

    And(BooleanCondition a, BooleanCondition b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public boolean value() {
      if (evaluatesTriviallyToExpectedValue(a, false)
          || evaluatesTriviallyToExpectedValue(b, false)) {
        return false;
      }
      return a.value() && b.value();
    }

    @Override
    public <T> Iterable<T> children(Class<T> type) {
      return Iterables.concat(a.children(type), b.children(type));
    }

    @Override
    public BooleanCondition reduce() {
      if (evaluatesTrivially()) {
        return Value.valueOf(value());
      }
      return new And(a.reduce(), b.reduce());
    }

    @Override
    public int hashCode() {
      return a.hashCode() * 31 + b.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof And) {
        And o = (And) other;
        return a.equals(o.a) && b.equals(o.b);
      }
      return false;
    }

    @Override
    public String toString() {
      return "(" + maybeTrim(a, getClass()) + " && " + maybeTrim(a, getClass()) + ")";
    }

    @Override
    protected boolean evaluatesTrivially() {
      return evaluatesTriviallyToExpectedValue(a, false)
          || evaluatesTriviallyToExpectedValue(b, false)
          || (a.evaluatesTrivially() && b.evaluatesTrivially());
    }
  }

  private static final class Or extends BooleanCondition {
    private final BooleanCondition a;
    private final BooleanCondition b;

    Or(BooleanCondition a, BooleanCondition b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public boolean value() {
      if (evaluatesTriviallyToExpectedValue(a, true)
          || evaluatesTriviallyToExpectedValue(b, true)) {
        return true;
      }
      return a.value() || b.value();
    }

    @Override
    public <T> Iterable<T> children(Class<T> type) {
      return Iterables.concat(a.children(type), b.children(type));
    }

    @Override
    public BooleanCondition reduce() {
      if (evaluatesTrivially()) {
        return Value.valueOf(value());
      }
      return new Or(a.reduce(), b.reduce());
    }

    @Override
    public int hashCode() {
      return a.hashCode() * 31 + b.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof Or) {
        Or o = (Or) other;
        return a.equals(o.a) && b.equals(o.b);
      }
      return false;
    }

    @Override
    public String toString() {
      return "(" + maybeTrim(a, getClass()) + " || " + maybeTrim(a, getClass()) + ")";
    }

    @Override
    protected boolean evaluatesTrivially() {
      return evaluatesTriviallyToExpectedValue(a, true)
          || evaluatesTriviallyToExpectedValue(b, true)
          || (a.evaluatesTrivially() && b.evaluatesTrivially());
    }
  }

  private static final class Not extends BooleanCondition {
    private final BooleanCondition cond;

    Not(BooleanCondition bc) {
      cond = bc;
    }

    @Override
    public boolean value() {
      return !cond.value();
    }

    @Override
    public <T> Iterable<T> children(Class<T> type) {
      return cond.children(type);
    }

    @Override
    public BooleanCondition reduce() {
      if (evaluatesTrivially()) {
        return Value.valueOf(value());
      }
      return this;
    }

    @Override
    public int hashCode() {
      return cond.hashCode() * 31;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Not ? cond.equals(((Not) other).cond) : false;
    }

    @Override
    public String toString() {
      return "!" + cond;
    }

    @Override
    protected boolean evaluatesTrivially() {
      return cond.evaluatesTrivially();
    }
  }

  private static final class Value extends BooleanCondition {
    private final boolean value;

    Value(boolean v) {
      value = v;
    }

    @Override
    public boolean value() {
      return value;
    }

    @Override
    public <T> Iterable<T> children(Class<T> type) {
      return Collections.emptyList();
    }

    @Override
    public BooleanCondition reduce() {
      return this;
    }

    @Override
    public int hashCode() {
      return value ? 1 : 0;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Value ? value == ((Value) other).value : false;
    }

    @Override
    public String toString() {
      return Boolean.toString(value);
    }

    @Override
    protected boolean evaluatesTrivially() {
      return true;
    }
  }

  /**
   * Helper for use in toString methods. Remove leading '(' and trailing ')' if the type is the same
   * as the parent.
   */
  static String maybeTrim(BooleanCondition cond, Class<? extends BooleanCondition> type) {
    String s = cond.toString();
    if (cond.getClass() == type
        && s.length() > 2
        && s.charAt(0) == '('
        && s.charAt(s.length() - 1) == ')') {
      s = s.substring(1, s.length() - 1);
    }
    return s;
  }

  private static boolean evaluatesTriviallyToExpectedValue(
      BooleanCondition cond, boolean expectedValue) {
    return cond.evaluatesTrivially() && (cond.value() == expectedValue);
  }
}
