// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.query;

import com.google.inject.name.Named;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Rewrites a Predicate tree by applying rewrite rules.
 * <p>
 * Subclasses may document their rewrite rules by declaring public methods with
 * {@link Rewrite} annotations, such as:
 *
 * <pre>
 * &#064;Rewrite(&quot;A=(owner:*) B=(status:*)&quot;)
 * public Predicate r1_ownerStatus(@Named(&quot;A&quot;) OperatorPredicate owner,
 *     &#064;Named(&quot;B&quot;) OperatorPredicate status) {
 * }
 * </pre>
 * <p>
 * Rewrite methods are applied in order by declared name, so naming methods with
 * a numeric prefix to ensure a specific ordering (if required) is suggested.
 *
 * @type <T> type of object the predicate can evaluate in memory.
 */
public abstract class QueryRewriter<T> {
  /**
   * Defines the rewrite rules known by a QueryRewriter.
   *
   * This class is thread-safe and may be reused or cached.
   *
   * @param <T> type of object the predicates can evaluate in memory.
   * @param <R> type of the rewriter subclass.
   */
  public static class Definition<T, R extends QueryRewriter<T>> {
    private final List<RewriteRule<T>> rewriteRules;

    public Definition(Class<R> clazz, QueryBuilder<T> qb) {
      rewriteRules = new ArrayList<RewriteRule<T>>();

      Class<?> c = clazz;
      while (c != QueryRewriter.class) {
        final Method[] declared = c.getDeclaredMethods();
        Arrays.sort(declared, new Comparator<Method>() {
          @Override
          public int compare(Method o1, Method o2) {
            return o1.getName().compareTo(o2.getName());
          }
        });
        for (Method m : declared) {
          final Rewrite rp = m.getAnnotation(Rewrite.class);
          if ((m.getModifiers() & Modifier.ABSTRACT) != Modifier.ABSTRACT
              && (m.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC
              && rp != null) {
            rewriteRules.add(new MethodRewrite(qb, rp.value(), m));
          }
        }
        c = c.getSuperclass();
      }
    }
  }

  private final List<RewriteRule<T>> rewriteRules;

  protected QueryRewriter(final Definition<T, ? extends QueryRewriter<T>> def) {
    this.rewriteRules = def.rewriteRules;
  }

  /** Combine the passed predicates into a single AND node. */
  public Predicate<T> and(Collection<? extends Predicate<T>> that) {
    return Predicate.and(that);
  }

  /** Combine the passed predicates into a single AND node. */
  public Predicate<T> and(Predicate<T>... that) {
    return and(Arrays.asList(that));
  }

  /** Combine the passed predicates into a single OR node. */
  public Predicate<T> or(Collection<? extends Predicate<T>> that) {
    return Predicate.or(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public Predicate<T> or(Predicate<T>... that) {
    return or(Arrays.asList(that));
  }

  /** Invert the passed node. */
  public Predicate<T> not(Predicate<T> that) {
    return Predicate.not(that);
  }

  /**
   * Apply rewrites to a graph until it stops changing.
   *
   * @param in the graph to rewrite.
   * @return the rewritten graph.
   */
  public Predicate<T> rewrite(Predicate<T> in) {
    Predicate<T> old;
    do {
      old = in;
      in = rewriteOne(in);

      if (old.equals(in) && in.getChildCount() > 0) {
        List<Predicate<T>> n = new ArrayList<Predicate<T>>(in.getChildCount());
        for (Predicate<T> p : in.getChildren()) {
          n.add(rewrite(p));
        }
        n = removeDuplicates(n);
        if (n.size() == 1 && (isAND(in) || isOR(in))) {
          in = n.get(0);
        } else {
          in = in.copy(n);
        }
      }

    } while (!old.equals(in));
    return replaceGenericNodes(in);
  }

  protected Predicate<T> replaceGenericNodes(final Predicate<T> in) {
    if (in.getClass() == NotPredicate.class) {
      return not(replaceGenericNodes(in.getChild(0)));

    } else if (in.getClass() == AndPredicate.class) {
      List<Predicate<T>> n = new ArrayList<Predicate<T>>(in.getChildCount());
      for (Predicate<T> c : in.getChildren()) {
        n.add(replaceGenericNodes(c));
      }
      return and(n);

    } else if (in.getClass() == OrPredicate.class) {
      List<Predicate<T>> n = new ArrayList<Predicate<T>>(in.getChildCount());
      for (Predicate<T> c : in.getChildren()) {
        n.add(replaceGenericNodes(c));
      }
      return or(n);

    } else {
      return in;
    }
  }

  private Predicate<T> rewriteOne(Predicate<T> input) {
    Predicate<T> best = null;
    for (RewriteRule<T> r : rewriteRules) {
      Predicate<T> n = r.rewrite(this, input);
      if (n == null) {
        continue;
      }

      if (!r.useBestCost()) {
        return n;
      }

      if (best == null || n.getCost() < best.getCost()) {
        best = n;
        continue;
      }
    }
    return best != null ? best : input;
  }

  private static class MatchResult<T> {
    private static final MatchResult<?> FAIL = new MatchResult<Object>(null);
    private static final MatchResult<?> OK = new MatchResult<Object>(null);

    @SuppressWarnings("unchecked")
    static <T> MatchResult<T> fail() {
      return (MatchResult<T>) FAIL;
    }

    @SuppressWarnings("unchecked")
    static <T> MatchResult<T> ok() {
      return (MatchResult<T>) OK;
    }

    final Predicate<T> extra;

    MatchResult(Predicate<T> extra) {
      this.extra = extra;
    }

    boolean success() {
      return this != FAIL;
    }
  }

  private MatchResult<T> match(final Map<String, Predicate<T>> outVars,
      final Predicate<T> pattern, final Predicate<T> actual) {
    if (pattern instanceof VariablePredicate) {
      final VariablePredicate<T> v = (VariablePredicate<T>) pattern;
      final MatchResult<T> r = match(outVars, v.getChild(0), actual);
      if (r.success()) {
        Predicate<T> old = outVars.get(v.getName());
        if (old == null) {
          outVars.put(v.getName(), actual);
          return r;
        } else if (old.equals(actual)) {
          return r;
        } else {
          return MatchResult.fail();
        }
      } else {
        return MatchResult.fail();
      }
    }

    if ((isAND(pattern) && isAND(actual)) //
        || (isOR(pattern) && isOR(actual)) //
        || (isNOT(pattern) && isNOT(actual)) //
    ) {
      // Order doesn't actually matter here. That does make our logic quite
      // a bit more complex as we need to consult each child at most once,
      // but in any order.
      //
      final LinkedList<Predicate<T>> have = dup(actual);
      final LinkedList<Predicate<T>> extra = new LinkedList<Predicate<T>>();
      for (final Predicate<T> pat : pattern.getChildren()) {
        boolean found = false;
        for (final Iterator<Predicate<T>> i = have.iterator(); i.hasNext();) {
          final MatchResult<T> r = match(outVars, pat, i.next());
          if (r.success()) {
            found = true;
            i.remove();
            if (r.extra != null) {
              extra.add(r.extra);
            }
            break;
          }
        }
        if (!found) {
          return MatchResult.fail();
        }
      }
      have.addAll(extra);
      switch (have.size()) {
        case 0:
          return MatchResult.ok();
        case 1:
          if (isNOT(actual)) {
            return new MatchResult<T>(actual.copy(have));
          }
          return new MatchResult<T>(have.get(0));
        default:
          return new MatchResult<T>(actual.copy(have));
      }

    } else if (pattern.equals(actual)) {
      return MatchResult.ok();

    } else if (pattern instanceof WildPatternPredicate
        && actual instanceof OperatorPredicate
        && ((OperatorPredicate<T>) pattern).getOperator().equals(
            ((OperatorPredicate<T>) actual).getOperator())) {
      return MatchResult.ok();

    } else {
      return MatchResult.fail();
    }
  }

  private static <T> LinkedList<Predicate<T>> dup(final Predicate<T> actual) {
    return new LinkedList<Predicate<T>>(actual.getChildren());
  }

  /**
   * Denotes a method which wants to replace a predicate expression.
   * <p>
   * This annotation must be applied to a public method which returns
   * {@link Predicate}. The arguments of the method should {@link Predicate}, or
   * any subclass of it. The annotation value is a query language string which
   * describes the subtree this rewrite applies to. Method arguments should be
   * named with a {@link Named} annotation, and the same names should be used in
   * the query.
   * <p>
   * For example:
   *
   * <pre>
   * &#064;Rewrite(&quot;A=(owner:*) B=(status:*)&quot;)
   * public Predicate ownerStatus(@Named(&quot;A&quot;) OperatorPredicate owner,
   *     &#064;Named(&quot;B&quot;) OperatorPredicate status) {
   * }
   * </pre>
   *
   * matches an AND Predicate with at least two children, one being an operator
   * predicate called "owner" and the other being an operator predicate called
   * "status". The variables in the query are matched by name against the
   * parameters.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  protected @interface Rewrite {
    String value();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  protected @interface NoCostComputation {
  }

  /** Applies a rewrite rule to a Predicate. */
  protected interface RewriteRule<T> {
    /**
     * Apply a rewrite rule to the Predicate.
     *
     * @param input the input predicate to be tested, and possibly rewritten.
     * @return a rewritten form of the predicate if this rule matches with the
     *         tree {@code input} and has a rewrite for it; {@code null} if this
     *         rule does not want this predicate.
     */
    Predicate<T> rewrite(QueryRewriter<T> rewriter, Predicate<T> input);

    /** @return true if the best cost should be selected. */
    boolean useBestCost();
  }

  /** Implements the magic behind {@link Rewrite} annotations. */
  private static class MethodRewrite<T> implements RewriteRule<T> {
    private final Method method;
    private final Predicate<T> pattern;
    private final String[] argNames;
    private final Class<? extends Predicate<T>>[] argTypes;
    private final boolean useBestCost;

    @SuppressWarnings("unchecked")
    MethodRewrite(QueryBuilder<T> queryBuilder, String patternText, Method m) {
      method = m;
      useBestCost = m.getAnnotation(NoCostComputation.class) == null;

      Predicate<T> p;
      try {
        p = queryBuilder.parse(patternText);
      } catch (QueryParseException e) {
        throw new RuntimeException("Bad @Rewrite(\"" + patternText + "\")"
            + " on " + m.toGenericString() + " in " + m.getDeclaringClass()
            + ": " + e.getMessage(), e);
      }
      if (!Predicate.class.isAssignableFrom(m.getReturnType())) {
        throw new RuntimeException(m.toGenericString() + " in "
            + m.getDeclaringClass() + " must return " + Predicate.class);
      }

      pattern = p;
      argNames = new String[method.getParameterTypes().length];
      argTypes = new Class[argNames.length];
      for (int i = 0; i < argNames.length; i++) {
        Named name = null;
        for (Annotation a : method.getParameterAnnotations()[i]) {
          if (a instanceof Named) {
            name = (Named) a;
            break;
          }
        }
        if (name == null) {
          throw new RuntimeException("Argument " + (i + 1) + " of "
              + m.toGenericString() + " in " + m.getDeclaringClass()
              + " has no @Named annotation");
        }
        if (!Predicate.class.isAssignableFrom(method.getParameterTypes()[i])) {
          throw new RuntimeException("Argument " + (i + 1) + " of "
              + m.toGenericString() + " in " + m.getDeclaringClass()
              + " must be of type " + Predicate.class);
        }
        argNames[i] = name.value();
        argTypes[i] = (Class<Predicate<T>>) method.getParameterTypes()[i];
      }
    }

    @Override
    public boolean useBestCost() {
      return useBestCost;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Predicate<T> rewrite(QueryRewriter<T> rewriter,
        final Predicate<T> input) {
      final HashMap<String, Predicate<T>> args =
          new HashMap<String, Predicate<T>>();
      final MatchResult<T> res = rewriter.match(args, pattern, input);
      if (!res.success()) {
        return null;
      }

      final Predicate[] argList = new Predicate[argNames.length];
      for (int i = 0; i < argList.length; i++) {
        argList[i] = args.get(argNames[i]);
        if (argList[i] == null) {
          final String a = "@Named(\"" + argNames[i] + "\")";
          throw error(new IllegalStateException("No value bound for " + a));
        }
        if (!argTypes[i].isInstance(argList[i])) {
          return null;
        }
      }

      final Predicate<T> rep;
      try {
        rep = (Predicate<T>) method.invoke(rewriter, (Object[]) argList);
      } catch (IllegalArgumentException e) {
        throw error(e);
      } catch (IllegalAccessException e) {
        throw error(e);
      } catch (InvocationTargetException e) {
        throw error(e.getCause());
      }

      if (rep instanceof RewritePredicate) {
        ((RewritePredicate) rep).init(method.getName(), argList);
      }

      if (res.extra == null) {
        return rep;
      }

      Predicate<T> extra = removeDuplicates(res.extra);
      Predicate<T>[] newArgs = new Predicate[] {extra, rep};
      return input.copy(Arrays.asList(newArgs));
    }

    private IllegalArgumentException error(Throwable e) {
      final String msg = "Cannot apply " + method.getName();
      return new IllegalArgumentException(msg, e);
    }
  }

  private static <T> Predicate<T> removeDuplicates(Predicate<T> in) {
    if (in.getChildCount() > 0) {
      List<Predicate<T>> n = removeDuplicates(in.getChildren());
      if (n.size() == 1 && (isAND(in) || isOR(in))) {
        in = n.get(0);
      } else {
        in = in.copy(n);
      }
    }
    return in;
  }

  private static <T> List<Predicate<T>> removeDuplicates(List<Predicate<T>> n) {
    List<Predicate<T>> r = new ArrayList<Predicate<T>>();
    for (Predicate<T> p : n) {
      if (!r.contains(p)) {
        r.add(p);
      }
    }
    return r;
  }

  private static <T> void expand(final List<Predicate<T>> out,
      final List<Predicate<T>> allOR, final List<Predicate<T>> tmp,
      final List<Predicate<T>> nonOR) {
    if (tmp.size() == allOR.size()) {
      final int sz = nonOR.size() + tmp.size();
      final List<Predicate<T>> newList = new ArrayList<Predicate<T>>(sz);
      newList.addAll(nonOR);
      newList.addAll(tmp);
      out.add(Predicate.and(newList));

    } else {
      for (final Predicate<T> c : allOR.get(tmp.size()).getChildren()) {
        try {
          tmp.add(c);
          expand(out, allOR, tmp, nonOR);
        } finally {
          tmp.remove(tmp.size() - 1);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> boolean isAND(final Predicate<T> p) {
    return p instanceof AndPredicate;
  }

  @SuppressWarnings("unchecked")
  private static <T> boolean isOR(final Predicate<T> p) {
    return p instanceof OrPredicate;
  }

  @SuppressWarnings("unchecked")
  private static <T> boolean isNOT(final Predicate<T> p) {
    return p instanceof NotPredicate;
  }
}
