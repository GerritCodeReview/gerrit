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
import java.util.HashMap;
import java.util.Map;

/**
 * Base class to support writing parsers for query languages.
 * <p>
 * This class is thread-safe, and may be reused across threads to parse queries,
 * so implementations of this class should also strive to be thread-safe.
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
 */
public abstract class QueryBuilder {
  private final Map<String, OperatorFactory> opFactories =
      new HashMap<String, OperatorFactory>();

  protected QueryBuilder() {
    // Guess at the supported operators by scanning methods.
    //
    Class<?> c = getClass();
    while (c != QueryBuilder.class) {
      for (final Method method : c.getDeclaredMethods()) {
        if (method.getAnnotation(Operator.class) != null
            && Predicate.class.isAssignableFrom(method.getReturnType())
            && method.getParameterTypes().length == 1
            && method.getParameterTypes()[0] == String.class
            && (method.getModifiers() & Modifier.ABSTRACT) == 0
            && (method.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC) {
          final String name = method.getName().toLowerCase();
          if (!opFactories.containsKey(name)) {
            opFactories.put(name, new ReflectionFactory(name, method));
          }
        }
      }
      c = c.getSuperclass();
    }
  }

  /**
   * Parse a user supplied query string into a predicate.
   *
   * @param query the query string.
   * @return predicate representing the user query.
   * @throws QueryParseException the query string is invalid and cannot be
   *         parsed by this parser. This may be due to a syntax error, may be
   *         due to an operator not being supported, or due to an invalid value
   *         being passed to a recognized operator.
   */
  public Predicate parse(final String query) throws QueryParseException {
    return toPredicate(QueryParser.parse(query));
  }

  private Predicate toPredicate(final Tree r) throws QueryParseException,
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

  private Predicate operator(final String name, final Tree val)
      throws QueryParseException {
    switch (val.getType()) {
      // Expand multiple values, "foo:(a b c)", as though they were written
      // out with the longer form, "foo:a foo:b foo:c".
      //
      case AND:
      case OR: {
        final Predicate[] p = new Predicate[val.getChildCount()];
        for (int i = 0; i < p.length; i++) {
          final Tree c = val.getChild(i);
          if (c.getType() != DEFAULT_FIELD) {
            throw error("Nested operator not expected: " + c);
          }
          p[i] = operator(name, onlyChildOf(c));
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

  private Predicate operator(final String name, final String value)
      throws QueryParseException {
    final OperatorFactory f = opFactories.get(name);
    if (f == null) {
      throw error("Unsupported operator " + name + ":" + value);
    }
    return f.create(value);
  }

  private Predicate defaultField(final Tree r) throws QueryParseException {
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
  protected Predicate defaultField(final String value)
      throws QueryParseException {
    throw error("Unsupported query:" + value);
  }

  private Predicate[] children(final Tree r) throws QueryParseException,
      IllegalArgumentException {
    final Predicate[] p = new Predicate[r.getChildCount()];
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

  /** Converts a value string passed to an operator into a {@link Predicate}. */
  protected interface OperatorFactory {
    Predicate create(String value) throws QueryParseException;
  }

  /** Denotes a method which is a query operator. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  protected @interface Operator {
  }

  private class ReflectionFactory implements OperatorFactory {
    private final String name;
    private final Method method;

    ReflectionFactory(final String name, final Method method) {
      this.name = name;
      this.method = method;
    }

    @Override
    public Predicate create(final String value) throws QueryParseException {
      try {
        return (Predicate) method.invoke(QueryBuilder.this, value);
      } catch (RuntimeException e) {
        throw error("Error in operator " + name + ":" + value, e);
      } catch (IllegalAccessException e) {
        throw error("Error in operator " + name + ":" + value, e);
      } catch (InvocationTargetException e) {
        throw error("Error in operator " + name + ":" + value, e.getCause());
      }
    }
  }
}
