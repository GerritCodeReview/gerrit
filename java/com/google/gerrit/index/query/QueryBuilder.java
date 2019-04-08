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

package com.google.gerrit.index.query;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.index.query.Predicate.and;
import static com.google.gerrit.index.query.Predicate.not;
import static com.google.gerrit.index.query.Predicate.or;
import static com.google.gerrit.index.query.QueryParser.AND;
import static com.google.gerrit.index.query.QueryParser.COLON;
import static com.google.gerrit.index.query.QueryParser.DEFAULT_FIELD;
import static com.google.gerrit.index.query.QueryParser.EXACT_PHRASE;
import static com.google.gerrit.index.query.QueryParser.FIELD_NAME;
import static com.google.gerrit.index.query.QueryParser.NOT;
import static com.google.gerrit.index.query.QueryParser.OR;
import static com.google.gerrit.index.query.QueryParser.SINGLE_WORD;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.antlr.runtime.tree.Tree;

/**
 * Base class to support writing parsers for query languages.
 *
 * <p>Subclasses may document their supported query operators by declaring public methods that
 * perform the query conversion into a {@link Predicate}. For example, to support "is:starred",
 * "is:unread", and nothing else, a subclass may write:
 *
 * <pre>
 * &#064;Operator
 * public Predicate is(String value) {
 *   if (&quot;starred&quot;.equals(value)) {
 *     return new StarredPredicate();
 *   }
 *   if (&quot;unread&quot;.equals(value)) {
 *     return new UnreadPredicate();
 *   }
 *   throw new IllegalArgumentException();
 * }
 * </pre>
 *
 * <p>The available operator methods are discovered at runtime via reflection. Method names (after
 * being converted to lowercase), correspond to operators in the query language, method string
 * values correspond to the operator argument. Methods must be declared {@code public}, returning
 * {@link Predicate}, accepting one {@link String}, and annotated with the {@link Operator}
 * annotation.
 *
 * <p>Subclasses may also declare a handler for values which appear without operator by overriding
 * {@link #defaultField(String)}.
 *
 * <p>Instances are non-singletons and should only be used once, in order to rescan the {@code
 * DynamicMap} of plugin-provided operators on each query invocation.
 *
 * @param <T> type of object the predicates can evaluate in memory.
 */
public abstract class QueryBuilder<T, Q extends QueryBuilder<T, Q>> {
  /** Converts a value string passed to an operator into a {@link Predicate}. */
  public interface OperatorFactory<T, Q extends QueryBuilder<T, Q>> {
    Predicate<T> create(Q builder, String value) throws QueryParseException;
  }

  /**
   * Defines the operators known by a QueryBuilder.
   *
   * <p>Operators are discovered by scanning for methods annotated with {@link Operator}. Operator
   * methods must be public, non-abstract, return a {@code Predicate}, and take a single string as
   * an argument.
   *
   * <p>This class is deeply immutable.
   *
   * @param <T> type of object the predicates can evaluate.
   * @param <Q> type of the query builder subclass.
   */
  public static class Definition<T, Q extends QueryBuilder<T, Q>> {
    private final ImmutableMap<String, OperatorFactory<T, Q>> opFactories;

    public Definition(Class<? extends Q> clazz) {
      ImmutableMap.Builder<String, OperatorFactory<T, Q>> b = ImmutableMap.builder();
      Class<?> c = clazz;
      while (c != QueryBuilder.class) {
        for (Method method : c.getDeclaredMethods()) {
          if (method.getAnnotation(Operator.class) == null) {
            continue;
          }
          checkArgument(
              CharMatcher.ascii().matchesAllOf(method.getName()),
              "method name must be ASCII: %s",
              method.getName());
          checkArgument(
              Predicate.class.isAssignableFrom(method.getReturnType())
                  && method.getParameterTypes().length == 1
                  && method.getParameterTypes()[0] == String.class
                  && Modifier.isPublic(method.getModifiers())
                  && !Modifier.isAbstract(method.getModifiers()),
              "method must be of the form \"@%s public Predicate<T> %s(String value)\": %s",
              Operator.class.getSimpleName(),
              method.getName(),
              method);
          String name = Ascii.toLowerCase(method.getName());
          b.put(name, new ReflectionFactory<>(name, method));
        }
        c = c.getSuperclass();
      }
      opFactories = b.build();
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
   * @return the first instance of a predicate having the given type, as found by a depth-first
   *     search.
   */
  @SuppressWarnings("unchecked")
  public static <T, P extends OperatorPredicate<T>> P find(
      Predicate<T> p, Class<P> clazz, String name) {
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

  protected final Definition<T, Q> builderDef;
  private final ImmutableMap<String, OperatorFactory<T, Q>> opFactories;

  protected QueryBuilder(
      Definition<T, Q> def,
      @Nullable DynamicMap<? extends OperatorFactory<T, Q>> dynamicOpFactories) {
    builderDef = def;

    if (dynamicOpFactories != null) {
      ImmutableMap.Builder<String, OperatorFactory<T, Q>> opFactoriesBuilder =
          ImmutableMap.builder();
      opFactoriesBuilder.putAll(def.opFactories);
      for (Extension<? extends OperatorFactory<T, Q>> e : dynamicOpFactories) {
        String name = e.getExportName() + "_" + e.getPluginName();
        opFactoriesBuilder.put(name, e.getProvider().get());
      }
      opFactories = opFactoriesBuilder.build();
    } else {
      opFactories = def.opFactories;
    }
  }

  /**
   * Parse a user-supplied query string into a predicate.
   *
   * @param query the query string.
   * @return predicate representing the user query.
   * @throws QueryParseException the query string is invalid and cannot be parsed by this parser.
   *     This may be due to a syntax error, may be due to an operator not being supported, or due to
   *     an invalid value being passed to a recognized operator.
   */
  public Predicate<T> parse(String query) throws QueryParseException {
    if (Strings.isNullOrEmpty(query)) {
      throw new QueryParseException("query is empty");
    }
    return toPredicate(QueryParser.parse(query));
  }

  /**
   * Parse multiple user-supplied query strings into a list of predicates.
   *
   * @param queries the query strings.
   * @return predicates representing the user query, in the same order as the input.
   * @throws QueryParseException one of the query strings is invalid and cannot be parsed by this
   *     parser. This may be due to a syntax error, may be due to an operator not being supported,
   *     or due to an invalid value being passed to a recognized operator.
   */
  public List<Predicate<T>> parse(List<String> queries) throws QueryParseException {
    List<Predicate<T>> predicates = new ArrayList<>(queries.size());
    for (String query : queries) {
      predicates.add(parse(query));
    }
    return predicates;
  }

  private Predicate<T> toPredicate(Tree r) throws QueryParseException, IllegalArgumentException {
    switch (r.getType()) {
      case AND:
        return and(children(r));
      case OR:
        return or(children(r));
      case NOT:
        return not(toPredicate(onlyChildOf(r)));

      case DEFAULT_FIELD:
        return defaultField(concatenateChildText(r));

      case FIELD_NAME:
        return operator(r.getText(), concatenateChildText(r));

      default:
        throw error("Unsupported operator: " + r);
    }
  }

  private static String concatenateChildText(Tree r) throws QueryParseException {
    if (r.getChildCount() == 0) {
      throw error("Expected children under: " + r);
    }
    if (r.getChildCount() == 1) {
      return getFieldValue(r.getChild(0));
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < r.getChildCount(); i++) {
      sb.append(getFieldValue(r.getChild(i)));
    }
    return sb.toString();
  }

  private static String getFieldValue(Tree r) throws QueryParseException {
    if (r.getChildCount() != 0) {
      throw error("Expected no children under: " + r);
    }
    switch (r.getType()) {
      case SINGLE_WORD:
      case COLON:
      case EXACT_PHRASE:
        return r.getText();
      default:
        throw error(
            String.format(
                "Unsupported %s node in operator %s: %s",
                QueryParser.tokenNames[r.getType()], r.getParent(), r));
    }
  }

  @SuppressWarnings("unchecked")
  private Predicate<T> operator(String name, String value) throws QueryParseException {
    @SuppressWarnings("rawtypes")
    OperatorFactory f = opFactories.get(name);
    if (f == null) {
      throw error("Unsupported operator " + name + ":" + value);
    }
    return f.create(this, value);
  }

  /**
   * Handle a value present outside of an operator.
   *
   * <p>This default implementation always throws an "Unsupported query: " message containing the
   * input text. Subclasses may override this method to perform do-what-i-mean guesses based on the
   * input string.
   *
   * @param value the value supplied by itself in the query.
   * @return predicate representing this value.
   * @throws QueryParseException the parser does not recognize this value.
   */
  protected Predicate<T> defaultField(String value) throws QueryParseException {
    throw error("Unsupported query:" + value);
  }

  private List<Predicate<T>> children(Tree r) throws QueryParseException, IllegalArgumentException {
    List<Predicate<T>> p = new ArrayList<>(r.getChildCount());
    for (int i = 0; i < r.getChildCount(); i++) {
      p.add(toPredicate(r.getChild(i)));
    }
    return p;
  }

  private Tree onlyChildOf(Tree r) throws QueryParseException {
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

  /** Denotes a method which is a query operator. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  protected @interface Operator {}

  private static class ReflectionFactory<T, Q extends QueryBuilder<T, Q>>
      implements OperatorFactory<T, Q> {
    private final String name;
    private final Method method;

    ReflectionFactory(String name, Method method) {
      this.name = name;
      this.method = method;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Predicate<T> create(Q builder, String value) throws QueryParseException {
      try {
        return (Predicate<T>) method.invoke(builder, value);
      } catch (RuntimeException | IllegalAccessException e) {
        throw error("Error in operator " + name + ":" + value, e);
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof QueryParseException) {
          throw (QueryParseException) e.getCause();
        }
        throw error("Error in operator " + name + ":" + value, e.getCause());
      }
    }
  }
}
