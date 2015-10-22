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

import static com.google.gerrit.server.query.Predicate.and;
import static com.google.gerrit.server.query.Predicate.not;
import static com.google.gerrit.server.query.Predicate.or;
import static com.google.gerrit.server.query.QueryParser.AND;
import static com.google.gerrit.server.query.QueryParser.DEFAULT_FIELD;
import static com.google.gerrit.server.query.QueryParser.EXACT_PHRASE;
import static com.google.gerrit.server.query.QueryParser.FIELD_NAME;
import static com.google.gerrit.server.query.QueryParser.NOT;
import static com.google.gerrit.server.query.QueryParser.OR;
import static com.google.gerrit.server.query.QueryParser.SINGLE_WORD;

import org.antlr.runtime.tree.Tree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class to support writing parsers for query languages.
 * <p>
 * Subclasses may document their supported query operators by declaring public
 * methods that perform the query conversion into a {@link Predicate}. For
 * example, to support "is:starred", "is:unread", and nothing else, a subclass
 * may write:
 *
 * <pre>
 * &#064;Operator
 * public Predicate is(final String value) {
 *   if (&quot;starred&quot;.equals(value)) {
 *     return new StarredPredicate();
 *   }
 *   if (&quot;unread&quot;.equals(value)) {
 *     return new UnreadPredicate();
 *   }
 *   throw new IllegalArgumentException();
 * }
 * </pre>
 * <p>
 * The available operator methods are discovered at runtime via reflection.
 * Method names (after being converted to lowercase), correspond to operators in
 * the query language, method string values correspond to the operator argument.
 * Methods must be declared {@code public}, returning {@link Predicate},
 * accepting one {@link String}, and annotated with the {@link Operator}
 * annotation.
 * <p>
 * Subclasses may also declare a handler for values which appear without
 * operator by overriding {@link #defaultField(String)}.
 *
 * @param <T> type of object the predicates can evaluate in memory.
 */
public abstract class QueryBuilder<T> {
  /** Denotes a method which is a query operator. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Operator {
  }

  /** Converts a value string passed to an operator into a {@link Predicate}. */
  public interface OperatorFactory<T, Q extends QueryBuilder<T>> {
    Predicate<T> create(Q builder, String value) throws QueryParseException;
  }

  /**
   * Denotes a builder for dynamic operators.
   *
   * To use dynamic operators, override the getDynamicBuilders() method
   * to return an Iterable of DynamicBuilders which define dynamic
   * operators.  Unlike the static list of operators, the DynamicBuilders
   * will be consulted on every operator lookup during query building to
   * find out if, and how, an operator is defined.
   */
  public interface DynamicBuilder<T> {
  }

  /**
   * Defines the operators known by a QueryBuilder.
   *
   * This class is thread-safe and may be reused or cached.
   *
   * @param <T> type of object the predicates can evaluate in memory.
   * @param <Q> type of the query builder subclass.
   */
  public static class Definition<T, Q extends QueryBuilder<T>> {
    private final Map<String, OperatorFactory<T, Q>> opFactories =
        new HashMap<>();

    //** Define the static operators */
    public Definition(Class<Q> clazz) {
      if (clazz != null) {
        Class<?> c = clazz;
        while (c != QueryBuilder.class) {
          registerOperatorFactories(c, null);
          c = c.getSuperclass();
        }
      }
    }

    //** Define the dynamic operators */
    public Definition(Iterable<? extends DynamicBuilder<T>> builders) {
      if (builders != null) {
        for (DynamicBuilder<T> builder : builders) {
          registerOperatorFactories(builder);
        }
      }
    }

    public void registerOperatorFactories(DynamicBuilder<T> instance) {
      Class<?> c = instance.getClass();
      registerOperatorFactories(c, instance);
    }

    public void registerOperatorFactories(Class<?> c, DynamicBuilder<T> instance) {
      // Guess at the supported operators by scanning methods.
      //
      for (final Method method : c.getDeclaredMethods()) {
        if (method.getAnnotation(Operator.class) != null
            && Predicate.class.isAssignableFrom(method.getReturnType())
            && method.getParameterTypes().length == 1
            && method.getParameterTypes()[0] == String.class
            && (method.getModifiers() & Modifier.ABSTRACT) == 0
            && (method.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC) {
          final String name = method.getName().toLowerCase();
          if (!opFactories.containsKey(name)) {
            opFactories.put(name,
                new ReflectionFactory<T, Q>(name, instance, method));
          }
        }
      }
    }

    public OperatorFactory<T, Q> getOperatorFactory(String name) {
      return opFactories.get(name);
    }
  }

  /**
   * Locate a predicate in the predicate tree.
   *
   * @param p the predicate to find.
   * @param clazz type of the predicate instance.
   * @return the predicate, null if not found.
   */
  @SuppressWarnings("unchecked")
  public static <T, P extends Predicate<T>> P find(Predicate<T> p, Class<P> clazz) {
    if (clazz.isAssignableFrom(p.getClass())) {
      return (P) p;
    }

    for (Predicate<T> c : p.getChildren()) {
      P r = find(c, clazz);
      if (r != null) {
        return r;
      }
    }

    return null;
  }

  /**
   * Locate a predicate in the predicate tree.
   *
   * @param p the predicate to find.
   * @param clazz type of the predicate instance.
   * @param name name of the operator.
   * @return the first instance of a predicate having the given type, as found
   *     by a depth-first search.
   */
  @SuppressWarnings("unchecked")
  public static <T, P extends OperatorPredicate<T>> P find(Predicate<T> p,
      Class<P> clazz, String name) {
    if (p instanceof OperatorPredicate
        && ((OperatorPredicate<?>) p).getOperator().equals(name)
        && clazz.isAssignableFrom(p.getClass())) {
      return (P) p;
    }

    for (Predicate<T> c : p.getChildren()) {
      P r = find(c, clazz, name);
      if (r != null) {
        return r;
      }
    }

    return null;
  }

  protected final Definition<T, ? extends QueryBuilder<T>> builderDef;

  @SuppressWarnings("rawtypes")
  private final Map<String, OperatorFactory> opFactories;

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected QueryBuilder(Definition<T, ? extends QueryBuilder<T>> def) {
    builderDef = def;
    opFactories = (Map) def.opFactories;
  }

  /**
   * Parse a user-supplied query string into a predicate.
   *
   * @param query the query string.
   * @return predicate representing the user query.
   * @throws QueryParseException the query string is invalid and cannot be
   *         parsed by this parser. This may be due to a syntax error, may be
   *         due to an operator not being supported, or due to an invalid value
   *         being passed to a recognized operator.
   */
  public Predicate<T> parse(final String query) throws QueryParseException {
    return toPredicate(QueryParser.parse(query));
  }

  /**
   * Parse multiple user-supplied query strings into a list of predicates.
   *
   * @param queries the query strings.
   * @return predicates representing the user query, in the same order as the
   *         input.
   * @throws QueryParseException one of the query strings is invalid and cannot
   *         be parsed by this parser. This may be due to a syntax error, may be
   *         due to an operator not being supported, or due to an invalid value
   *         being passed to a recognized operator.
   *
   */
  public List<Predicate<T>> parse(final List<String> queries)
      throws QueryParseException {
    List<Predicate<T>> predicates = new ArrayList<>(queries.size());
    for (String query : queries) {
      predicates.add(parse(query));
    }
    return predicates;
  }

  private Predicate<T> toPredicate(final Tree r) throws QueryParseException,
      IllegalArgumentException {
    switch (r.getType()) {
      case AND:
        return and(children(r));
      case OR:
        return or(children(r));
      case NOT:
        return not(toPredicate(onlyChildOf(r)));

      case DEFAULT_FIELD:
        return defaultField(onlyChildOf(r));

      case FIELD_NAME:
        return operator(r.getText(), onlyChildOf(r));

      default:
        throw error("Unsupported operator: " + r);
    }
  }

  private Predicate<T> operator(final String name, final Tree val)
      throws QueryParseException {
    switch (val.getType()) {
      // Expand multiple values, "foo:(a b c)", as though they were written
      // out with the longer form, "foo:a foo:b foo:c".
      //
      case AND:
      case OR: {
        List<Predicate<T>> p = new ArrayList<>(val.getChildCount());
        for (int i = 0; i < val.getChildCount(); i++) {
          final Tree c = val.getChild(i);
          if (c.getType() != DEFAULT_FIELD) {
            throw error("Nested operator not expected: " + c);
          }
          p.add(operator(name, onlyChildOf(c)));
        }
        return val.getType() == AND ? and(p) : or(p);
      }

      case SINGLE_WORD:
      case EXACT_PHRASE:
        if (val.getChildCount() != 0) {
          throw error("Expected no children under: " + val);
        }
        return operator(name, val.getText());

      default:
        throw error("Unsupported node in operator " + name + ": " + val);
    }
  }

  @SuppressWarnings("unchecked")
  protected Predicate<T> operator(final String name, final String value)
      throws QueryParseException {
    @SuppressWarnings("rawtypes")
    OperatorFactory f = opFactories.get(name);
    if (f == null) {
      f = getDynamicDefinition().getOperatorFactory(name);
    }

    if (f != null) {
      return f.create(this, value);
    }

    throw error("Unsupported operator " + name + ":" + value);
  }

  private Predicate<T> defaultField(final Tree r) throws QueryParseException {
    switch (r.getType()) {
      case SINGLE_WORD:
      case EXACT_PHRASE:
        if (r.getChildCount() != 0) {
          throw error("Expected no children under: " + r);
        }
        return defaultField(r.getText());

      default:
        throw error("Unsupported node: " + r);
    }
  }

  /**
   * Handle a value present outside of an operator.
   * <p>
   * This default implementation always throws an "Unsupported query: " message
   * containing the input text. Subclasses may override this method to perform
   * do-what-i-mean guesses based on the input string.
   *
   * @param value the value supplied by itself in the query.
   * @return predicate representing this value.
   * @throws QueryParseException the parser does not recognize this value.
   */
  protected Predicate<T> defaultField(final String value)
      throws QueryParseException {
    throw error("Unsupported query:" + value);
  }

  public Definition<T, ? extends QueryBuilder<T>> getDynamicDefinition() {
    return new Definition<T, QueryBuilder<T>>(getDynamicBuilders());
  }

  protected Iterable<? extends DynamicBuilder<T>> getDynamicBuilders() {
    return null;
  }

  @SuppressWarnings("unchecked")
  private Predicate<T>[] children(final Tree r) throws QueryParseException,
      IllegalArgumentException {
    final Predicate<T>[] p = new Predicate[r.getChildCount()];
    for (int i = 0; i < p.length; i++) {
      p[i] = toPredicate(r.getChild(i));
    }
    return p;
  }

  private Tree onlyChildOf(final Tree r) throws QueryParseException {
    if (r.getChildCount() != 1) {
      throw error("Expected exactly one child: " + r);
    }
    return r.getChild(0);
  }

  protected static QueryParseException error(String msg) {
    return new QueryParseException(msg);
  }

  protected static QueryParseException error(String msg, Throwable why) {
    return new QueryParseException(msg, why);
  }

  protected static class ReflectionFactory<T, Q extends QueryBuilder<T>>
      implements OperatorFactory<T, Q> {
    private final String name;
    private DynamicBuilder<T> dBuilder;
    private final Method method;

    ReflectionFactory(String name, DynamicBuilder<T> dBuilder, Method method) {
      this.name = name;
      this.dBuilder = dBuilder;
      this.method = method;
    }

    ReflectionFactory(final String name, final Method method) {
      this.name = name;
      this.method = method;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Predicate<T> create(Q builder, String value)
        throws QueryParseException {
      try {
        return (Predicate<T>) method.invoke(
            (dBuilder == null) ? builder : dBuilder, value);
      } catch (RuntimeException | IllegalAccessException e) {
        throw error("Error in operator " + name + ":" + value, e);
      } catch (InvocationTargetException e) {
        throw error("Error in operator " + name + ":" + value, e.getCause());
      }
    }
  }
}
