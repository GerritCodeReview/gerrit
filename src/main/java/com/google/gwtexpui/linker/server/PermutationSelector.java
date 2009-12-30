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

package com.google.gwtexpui.linker.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

/**
 * Selects a permutation based on the HTTP request.
 * <p>
 * To use this class the application's GWT module must include our linker by
 * inheriting our module:
 *
 * <pre>
 *   &lt;inherits name='com.google.gwtexpui.linker.ServerPlannedIFrameLinker'/&gt;
 * </pre>
 */
public class PermutationSelector {
  private final String moduleName;
  private final Map<String, Rule> rulesByName;
  private final List<Rule> ruleOrder;
  private final List<Permutation> permutations;
  private final List<String> css;

  /**
   * Create an empty selector for a module.
   * <p>
   * {@link UserAgentRule} rule is automatically registered. Additional custom
   * selector rules may be registered before {@link #init(ServletContext)} is
   * called to finish the selector setup.
   *
   * @param moduleName the name of the module within the context.
   */
  public PermutationSelector(final String moduleName) {
    this.moduleName = moduleName;

    this.rulesByName = new HashMap<String, Rule>();
    this.ruleOrder = new ArrayList<Rule>();
    this.permutations = new ArrayList<Permutation>();
    this.css = new ArrayList<String>();

    register(new UserAgentRule());
  }

  private void notInitialized() {
    if (!ruleOrder.isEmpty()) {
      throw new IllegalStateException("Already initialized");
    }
  }

  /**
   * Register a property selection rule.
   *
   * @param r the rule implementation.
   */
  public void register(Rule r) {
    notInitialized();
    rulesByName.put(r.getName(), r);
  }

  /**
   * Initialize the selector by reading the module's {@code permutations} file.
   *
   * @param ctx context to load the module data from.
   * @throws ServletException
   * @throws IOException
   */
  public void init(ServletContext ctx) throws ServletException, IOException {
    notInitialized();

    final String tableName = "/" + moduleName + "/permutations";
    final InputStream in = ctx.getResourceAsStream(tableName);
    if (in == null) {
      throw new ServletException("No " + tableName + " in context");
    }
    try {
      BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      for (;;) {
        final String strongName = r.readLine();
        if (strongName == null) {
          break;
        }

        if (strongName.startsWith("css ")) {
          css.add(strongName.substring("css ".length()));
          continue;
        }

        Map<String, String> selections = new LinkedHashMap<String, String>();
        for (;;) {
          String permutation = r.readLine();
          if (permutation == null || permutation.isEmpty()) {
            break;
          }

          int eq = permutation.indexOf('=');
          if (eq < 0) {
            throw new ServletException(tableName + " has malformed content");
          }

          String k = permutation.substring(0, eq).trim();
          String v = permutation.substring(eq + 1);

          Rule rule = get(k);
          if (!ruleOrder.contains(rule)) {
            ruleOrder.add(rule);
          }

          if (selections.put(k, v) != null) {
            throw new ServletException("Table " + tableName
                + " has multiple values for " + k + " within permutation "
                + strongName);
          }
        }

        String cacheHtml = strongName + ".cache.html";
        String[] values = new String[ruleOrder.size()];
        for (int i = 0; i < values.length; i++) {
          values[i] = selections.get(ruleOrder.get(i).getName());
        }
        permutations.add(new Permutation(this, cacheHtml, values));
      }
    } finally {
      in.close();
    }
  }

  private Rule get(final String name) {
    Rule r = rulesByName.get(name);
    if (r == null) {
      r = new ClientSideRule(name);
      register(r);
    }
    return r;
  }

  /** @return name of the module (within the application context). */
  public String getModuleName() {
    return moduleName;
  }

  /** @return all possible permutations */
  public List<Permutation> getPermutations() {
    return Collections.unmodifiableList(permutations);
  }

  /**
   * Select the permutation that best matches the browser request.
   *
   * @param req current request.
   * @return the selected permutation; null if no permutation can be fit to the
   *         request and the standard {@code nocache.js} loader must be used.
   */
  public Permutation select(HttpServletRequest req) {
    final String[] values = new String[ruleOrder.size()];
    for (int i = 0; i < values.length; i++) {
      final String value = ruleOrder.get(i).select(req);
      if (value == null) {
        // If the rule returned null it doesn't know how to compute
        // the value for this HTTP request. Since we can't do that
        // defer to JavaScript by not picking a permutation.
        //
        return null;
      }
      values[i] = value;
    }

    for (Permutation p : permutations) {
      if (p.matches(values)) {
        return p;
      }
    }

    return null;
  }

  Collection<String> getCSS() {
    return css;
  }
}
