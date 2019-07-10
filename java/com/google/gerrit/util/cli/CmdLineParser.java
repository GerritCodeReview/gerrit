/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * (Taken from JGit org.eclipse.jgit.pgm.opt.CmdLineParser.)
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Git Development Community nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.gerrit.util.cli;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.util.cli.Localizable.localizable;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.IllegalAnnotationError;
import org.kohsuke.args4j.NamedOptionDef;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.args4j.spi.EnumOptionHandler;
import org.kohsuke.args4j.spi.MethodSetter;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.Setters;

/**
 * Extended command line parser which handles --foo=value arguments.
 *
 * <p>The args4j package does not natively handle --foo=value and instead prefers to see --foo value
 * on the command line. Many users are used to the GNU style --foo=value long option, so we convert
 * from the GNU style format to the args4j style format prior to invoking args4j for parsing.
 */
public class CmdLineParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    CmdLineParser create(Object bean);
  }

  /**
   * This may be used by an option handler during parsing to "call" additional parameters simulating
   * as if they had been passed from the command line originally.
   *
   * <p>To call additional parameters from within an option handler, instantiate this class with the
   * parameters and then call callParameters() with the additional parameters to be parsed.
   * OptionHandlers may optionally pass this class to other methods which may then both
   * parse/consume more parameters and call additional parameters.
   */
  public static class Parameters implements org.kohsuke.args4j.spi.Parameters {
    protected final String[] args;
    protected MyParser parser;
    protected int consumed = 0;

    public Parameters(org.kohsuke.args4j.spi.Parameters args, MyParser parser)
        throws CmdLineException {
      this.args = new String[args.size()];
      for (int i = 0; i < args.size(); i++) {
        this.args[i] = args.getParameter(i);
      }
      this.parser = parser;
    }

    public Parameters(String[] args, MyParser parser) {
      this.args = args;
      this.parser = parser;
    }

    @Override
    public String getParameter(int idx) throws CmdLineException {
      return args[idx];
    }

    /**
     * get and consume (consider parsed) a parameter
     *
     * @return the consumed parameter
     */
    public String consumeParameter() throws CmdLineException {
      return getParameter(consumed++);
    }

    @Override
    public int size() {
      return args.length;
    }

    /**
     * Add 'count' to the value of parsed parameters. May be called more than once.
     *
     * @param count How many parameters were just parsed.
     */
    public void consume(int count) {
      consumed += count;
    }

    /**
     * Reports handlers how many parameters were parsed
     *
     * @return the count of parsed parameters
     */
    public int getConsumed() {
      return consumed;
    }

    /**
     * Use during parsing to call additional parameters simulating as if they had been passed from
     * the command line originally.
     *
     * @param args A variable amount of parameters to call immediately
     *     <p>The parameters will be parsed immediately, before the remaining parameter will be
     *     parsed.
     *     <p>Note: Since this is done outside of the arg4j parsing loop, it will not match exactly
     *     what would happen if they were actually passed from the command line, but it will be
     *     pretty close. If this were moved to args4j, the interface could be the same and it could
     *     match exactly the behavior as if passed from the command line originally.
     */
    public void callParameters(String... args) throws CmdLineException {
      Parameters impl = new Parameters(Arrays.copyOfRange(args, 1, args.length), parser);
      parser.findOptionByName(args[0]).parseArguments(impl);
    }
  }

  private final OptionHandlers handlers;
  private final MyParser parser;

  @SuppressWarnings("rawtypes")
  private Map<String, OptionHandler> options;

  /**
   * Creates a new command line owner that parses arguments/options and set them into the given
   * object.
   *
   * @param bean instance of a class annotated by {@link org.kohsuke.args4j.Option} and {@link
   *     org.kohsuke.args4j.Argument}. this object will receive values.
   * @throws IllegalAnnotationError if the option bean class is using args4j annotations
   *     incorrectly.
   */
  @Inject
  public CmdLineParser(OptionHandlers handlers, @Assisted final Object bean)
      throws IllegalAnnotationError {
    this.handlers = handlers;
    this.parser = new MyParser(bean);
  }

  public void addArgument(Setter<?> setter, Argument a) {
    parser.addArgument(setter, a);
  }

  public void addOption(Setter<?> setter, Option o) {
    parser.addOption(setter, o);
  }

  public void printSingleLineUsage(Writer w, ResourceBundle rb) {
    parser.printSingleLineUsage(w, rb);
  }

  public void printUsage(Writer out, ResourceBundle rb) {
    parser.printUsage(out, rb);
  }

  public void printDetailedUsage(String name, StringWriter out) {
    out.write(name);
    printSingleLineUsage(out, null);
    out.write('\n');
    out.write('\n');
    printUsage(out, null);
    out.write('\n');
  }

  public void printQueryStringUsage(String name, StringWriter out) {
    out.write(name);

    char next = '?';
    List<NamedOptionDef> booleans = new ArrayList<>();
    for (@SuppressWarnings("rawtypes") OptionHandler handler : parser.optionsList) {
      if (handler.option instanceof NamedOptionDef) {
        NamedOptionDef n = (NamedOptionDef) handler.option;

        if (handler instanceof BooleanOptionHandler) {
          booleans.add(n);
          continue;
        }

        if (!n.required()) {
          out.write('[');
        }
        out.write(next);
        next = '&';
        if (n.name().startsWith("--")) {
          out.write(n.name().substring(2));
        } else if (n.name().startsWith("-")) {
          out.write(n.name().substring(1));
        } else {
          out.write(n.name());
        }
        out.write('=');

        out.write(metaVar(handler, n));
        if (!n.required()) {
          out.write(']');
        }
        if (n.isMultiValued()) {
          out.write('*');
        }
      }
    }
    for (NamedOptionDef n : booleans) {
      if (!n.required()) {
        out.write('[');
      }
      out.write(next);
      next = '&';
      if (n.name().startsWith("--")) {
        out.write(n.name().substring(2));
      } else if (n.name().startsWith("-")) {
        out.write(n.name().substring(1));
      } else {
        out.write(n.name());
      }
      if (!n.required()) {
        out.write(']');
      }
    }
  }

  private static String metaVar(OptionHandler<?> handler, NamedOptionDef n) {
    String var = n.metaVar();
    if (Strings.isNullOrEmpty(var)) {
      var = handler.getDefaultMetaVariable();
      if (handler instanceof EnumOptionHandler) {
        var = var.substring(1, var.length() - 1).replace(" ", "");
      }
    }
    return var;
  }

  public boolean wasHelpRequestedByOption() {
    return parser.help;
  }

  public void parseArgument(String... args) throws CmdLineException {
    List<String> tmp = Lists.newArrayListWithCapacity(args.length);
    for (int argi = 0; argi < args.length; argi++) {
      final String str = args[argi];
      if (str.equals("--")) {
        while (argi < args.length) {
          tmp.add(args[argi++]);
        }
        break;
      }

      if (str.startsWith("--")) {
        final int eq = str.indexOf('=');
        if (eq > 0) {
          tmp.add(str.substring(0, eq));
          tmp.add(str.substring(eq + 1));
          continue;
        }
      }

      tmp.add(str);
    }
    parser.parseArgument(tmp.toArray(new String[tmp.size()]));
  }

  public void parseOptionMap(Map<String, String[]> parameters) throws CmdLineException {
    ListMultimap<String, String> map = MultimapBuilder.hashKeys().arrayListValues().build();
    for (Map.Entry<String, String[]> ent : parameters.entrySet()) {
      for (String val : ent.getValue()) {
        map.put(ent.getKey(), val);
      }
    }
    parseOptionMap(map);
  }

  public void parseOptionMap(ListMultimap<String, String> params) throws CmdLineException {
    logger.atFinest().log("Command-line parameters: %s", params.keySet());
    List<String> knownArgs = Lists.newArrayListWithCapacity(2 * params.size());
    for (String key : params.keySet()) {
      String name = makeOption(key);

      if (isKnownOption(name)) {
        if (isBoolean(name)) {
          boolean on = false;
          for (String value : params.get(key)) {
            on = toBoolean(key, value);
          }
          if (on) {
            knownArgs.add(name);
          }
        } else {
          for (String value : params.get(key)) {
            knownArgs.add(name);
            knownArgs.add(value);
          }
        }
      } else {
        for (String value : params.get(key)) {
          parser.handleUnknownOption(name, value);
        }
      }
    }
    parser.parseArgument(knownArgs.toArray(new String[knownArgs.size()]));
  }

  public boolean isBoolean(String name) {
    return findHandler(makeOption(name)) instanceof BooleanOptionHandler;
  }

  public void parseWithPrefix(String prefix, Object bean) {
    parser.parseWithPrefix(prefix, bean);
  }

  public void drainOptionQueue() {
    parser.addOptionsWithMetRequirements();
  }

  private String makeOption(String name) {
    if (!name.startsWith("-")) {
      if (name.length() == 1) {
        name = "-" + name;
      } else {
        name = "--" + name;
      }
    }
    return name;
  }

  private boolean isKnownOption(String name) {
    return findHandler(name) != null;
  }

  @SuppressWarnings("rawtypes")
  private OptionHandler findHandler(String name) {
    if (options == null) {
      options = index(parser.optionsList);
    }
    return options.get(name);
  }

  @SuppressWarnings("rawtypes")
  private static Map<String, OptionHandler> index(List<OptionHandler> in) {
    Map<String, OptionHandler> m = new HashMap<>();
    for (OptionHandler handler : in) {
      if (handler.option instanceof NamedOptionDef) {
        NamedOptionDef def = (NamedOptionDef) handler.option;
        if (!def.isArgument()) {
          m.put(def.name(), handler);
          for (String alias : def.aliases()) {
            m.put(alias, handler);
          }
        }
      }
    }
    return m;
  }

  private boolean toBoolean(String name, String value) throws CmdLineException {
    if ("true".equals(value)
        || "t".equals(value)
        || "yes".equals(value)
        || "y".equals(value)
        || "on".equals(value)
        || "1".equals(value)
        || value == null
        || "".equals(value)) {
      return true;
    }

    if ("false".equals(value)
        || "f".equals(value)
        || "no".equals(value)
        || "n".equals(value)
        || "off".equals(value)
        || "0".equals(value)) {
      return false;
    }

    throw new CmdLineException(parser, localizable("invalid boolean \"%s=%s\""), name, value);
  }

  private static Option newPrefixedOption(String prefix, Option o) {
    requireNonNull(prefix);
    checkArgument(o.name().startsWith("-"), "Option name must start with '-': %s", o);
    String[] aliases = Arrays.stream(o.aliases()).map(prefix::concat).toArray(String[]::new);
    return OptionUtil.newOption(
        prefix + o.name(),
        aliases,
        o.usage(),
        o.metaVar(),
        o.required(),
        false,
        o.hidden(),
        o.handler(),
        o.depends(),
        new String[0]);
  }

  public class MyParser extends org.kohsuke.args4j.CmdLineParser {
    private final Object bean;

    boolean help;

    @SuppressWarnings("rawtypes")
    private List<OptionHandler> optionsList;

    private Map<String, QueuedOption> queuedOptionsByName = new LinkedHashMap<>();

    private class QueuedOption {
      public final Option option;

      @SuppressWarnings("rawtypes")
      public final Setter setter;

      public final String[] requiredOptions;

      private QueuedOption(
          Option option,
          @SuppressWarnings("rawtypes") Setter setter,
          RequiresOptions requiresOptions) {
        this.option = option;
        this.setter = setter;
        this.requiredOptions = requiresOptions != null ? requiresOptions.value() : new String[0];
      }
    }

    MyParser(Object bean) {
      super(bean, ParserProperties.defaults().withAtSyntax(false));
      this.bean = bean;
      parseAdditionalOptions(bean, new HashSet<>());
      addOptionsWithMetRequirements();
      ensureOptionsInitialized();
    }

    public void handleUnknownOption(String name, String value) throws CmdLineException {
      if (bean instanceof UnknownOptionHandler
          && ((UnknownOptionHandler) bean).accept(name, Strings.emptyToNull(value))) {
        return;
      }

      // Parse argument to trigger a CmdLineException for the unknown option.
      parseArgument(name, value);
    }

    public int addOptionsWithMetRequirements() {
      int count = 0;
      for (Iterator<Map.Entry<String, QueuedOption>> it = queuedOptionsByName.entrySet().iterator();
          it.hasNext(); ) {
        QueuedOption queuedOption = it.next().getValue();
        if (hasAllRequiredOptions(queuedOption)) {
          addOption(queuedOption.setter, queuedOption.option);
          it.remove();
          count++;
        }
      }
      if (count > 0) {
        count += addOptionsWithMetRequirements();
      }
      return count;
    }

    private boolean hasAllRequiredOptions(QueuedOption queuedOption) {
      for (String name : queuedOption.requiredOptions) {
        if (findOptionByName(name) == null) {
          return false;
        }
      }
      return true;
    }

    // NOTE: Argument annotations on bean are ignored.
    public void parseWithPrefix(String prefix, Object bean) {
      parseWithPrefix(prefix, bean, new HashSet<>());
    }

    private void parseWithPrefix(String prefix, Object bean, Set<Object> parsedBeans) {
      if (!parsedBeans.add(bean)) {
        return;
      }
      // recursively process all the methods/fields.
      for (Class<?> c = bean.getClass(); c != null; c = c.getSuperclass()) {
        for (Method m : c.getDeclaredMethods()) {
          Option o = m.getAnnotation(Option.class);
          if (o != null) {
            queueOption(
                newPrefixedOption(prefix, o),
                new MethodSetter(this, bean, m),
                m.getAnnotation(RequiresOptions.class));
          }
        }
        for (Field f : c.getDeclaredFields()) {
          Option o = f.getAnnotation(Option.class);
          if (o != null) {
            queueOption(
                newPrefixedOption(prefix, o),
                Setters.create(f, bean),
                f.getAnnotation(RequiresOptions.class));
          }
          if (f.isAnnotationPresent(Options.class)) {
            try {
              parseWithPrefix(
                  prefix + f.getAnnotation(Options.class).prefix(), f.get(bean), parsedBeans);
            } catch (IllegalAccessException e) {
              throw new IllegalAnnotationError(e);
            }
          }
        }
      }
    }

    private void parseAdditionalOptions(Object bean, Set<Object> parsedBeans) {
      for (Class<?> c = bean.getClass(); c != null; c = c.getSuperclass()) {
        for (Field f : c.getDeclaredFields()) {
          if (f.isAnnotationPresent(Options.class)) {
            Object additionalBean;
            try {
              additionalBean = f.get(bean);
            } catch (IllegalAccessException e) {
              throw new IllegalAnnotationError(e);
            }
            parseWithPrefix(f.getAnnotation(Options.class).prefix(), additionalBean, parsedBeans);
          }
        }
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected OptionHandler createOptionHandler(OptionDef option, Setter setter) {
      if (isHandlerSpecified(option) || isEnum(setter) || isPrimitive(setter)) {
        return add(super.createOptionHandler(option, setter));
      }

      OptionHandlerFactory<?> factory = handlers.get(setter.getType());
      if (factory != null) {
        return factory.create(this, option, setter);
      }
      return add(super.createOptionHandler(option, setter));
    }

    /**
     * Finds a registered {@code OptionHandler} by its name or its alias.
     *
     * @param name name
     * @return the {@code OptionHandler} or {@code null}
     *     <p>Note: this is cut & pasted from the parent class in arg4j, it was private and it
     *     needed to be exposed.
     */
    @SuppressWarnings("rawtypes")
    public OptionHandler findOptionByName(String name) {
      for (OptionHandler h : optionsList) {
        NamedOptionDef option = (NamedOptionDef) h.option;
        if (name.equals(option.name())) {
          return h;
        }
        for (String alias : option.aliases()) {
          if (name.equals(alias)) {
            return h;
          }
        }
      }
      return null;
    }

    private void queueOption(
        Option option,
        @SuppressWarnings("rawtypes") Setter setter,
        RequiresOptions requiresOptions) {
      if (queuedOptionsByName.put(option.name(), new QueuedOption(option, setter, requiresOptions))
          != null) {
        throw new IllegalAnnotationError(
            "Option name " + option.name() + " is used more than once");
      }
    }

    @SuppressWarnings("rawtypes")
    private OptionHandler add(OptionHandler handler) {
      ensureOptionsInitialized();
      optionsList.add(handler);
      return handler;
    }

    private void ensureOptionsInitialized() {
      if (optionsList == null) {
        optionsList = new ArrayList<>();
        addOption(newHelpSetter(), newHelpOption());
      }
    }

    private Setter<?> newHelpSetter() {
      try {
        return Setters.create(getClass().getDeclaredField("help"), this);
      } catch (NoSuchFieldException e) {
        throw new IllegalStateException(e);
      }
    }

    private Option newHelpOption() {
      return OptionUtil.newOption(
          "--help",
          new String[] {"-h"},
          "display this help text",
          "",
          false,
          false,
          false,
          BooleanOptionHandler.class,
          new String[0],
          new String[0]);
    }

    private boolean isHandlerSpecified(OptionDef option) {
      return option.handler() != OptionHandler.class;
    }

    private <T> boolean isEnum(Setter<T> setter) {
      return Enum.class.isAssignableFrom(setter.getType());
    }

    private <T> boolean isPrimitive(Setter<T> setter) {
      return setter.getType().isPrimitive();
    }
  }

  public CmdLineException reject(String message) {
    return new CmdLineException(parser, localizable(message));
  }
}
