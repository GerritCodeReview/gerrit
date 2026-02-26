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

package com.google.gerrit.auth.ldap;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.metrics.Timer0;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/** Supports issuing parameterized queries against an LDAP data source. */
class LdapQuery {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final Set<String> ALL_ATTRIBUTES = null;

  private final String base;
  private final SearchScope searchScope;
  private final ParameterizedString pattern;
  private final String[] returnAttributes;

  LdapQuery(
      final String base,
      final SearchScope searchScope,
      final ParameterizedString pattern,
      final Set<String> returnAttributes) {
    this.base = base;
    this.searchScope = searchScope;

    this.pattern = pattern;

    if (returnAttributes != null) {
      this.returnAttributes = new String[returnAttributes.size()];
      returnAttributes.toArray(this.returnAttributes);
    } else {
      this.returnAttributes = null;
    }
  }

  List<String> getParameters() {
    return pattern.getParameterNames();
  }

  List<Result> query(DirContext ctx, Map<String, String> params, Timer0 queryTimer)
      throws NamingException {
    final SearchControls sc = new SearchControls();
    final NamingEnumeration<SearchResult> res;
    Object[] filterArgs = pattern.bind(params);
    String renderedFilter = formatSearchFilter(pattern.getRawPattern(), filterArgs);
    String ldapSearchCommand = buildLdapSearchCommand(ctx, renderedFilter);

    sc.setSearchScope(searchScope.scope());
    sc.setReturningAttributes(returnAttributes);
    logger.atWarning().log(
        "LDAP search request: base=%s scope=%s filter=%s filterArgs=%s renderedFilter=%s"
            + " returnAttrs=%s ldapsearch=%s",
        base,
        searchScope,
        pattern.getRawPattern(),
        filterArgs,
        renderedFilter,
        returnAttributes == null ? "<ALL>" : returnAttributes,
        ldapSearchCommand);

    try (Timer0.Context ignored = queryTimer.start()) {
      res = ctx.search(base, pattern.getRawPattern(), filterArgs, sc);
    }
    try {
      final List<Result> r = new ArrayList<>();
      try {
        while (res.hasMore()) {
          r.add(new Result(res.next()));
        }
      } catch (PartialResultException e) {
        // Ignored
      }
      return r;
    } finally {
      res.close();
    }
  }

  // Mirrors JNDI SearchFilter.format(expr, args) so logs show the exact filter string JNDI builds.
  private static String formatSearchFilter(String expr, Object[] args) {
    int where = 0;
    int start = 0;
    StringBuilder out = new StringBuilder(expr.length());
    while ((where = findUnescaped('{', expr, start)) >= 0) {
      int pstart = where + 1;
      int pend = expr.indexOf('}', pstart);
      if (pend < 0) {
        throw new IllegalArgumentException("unbalanced {: " + expr);
      }
      int param;
      try {
        param = Integer.parseInt(expr.substring(pstart, pend));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("integer expected inside {}: " + expr, e);
      }
      if (param >= args.length) {
        throw new IllegalArgumentException("number exceeds argument list: " + param);
      }
      out.append(expr, start, where).append(encodeFilterValue(args[param]));
      start = pend + 1;
    }
    if (start < expr.length()) {
      out.append(expr, start, expr.length());
    }
    return out.toString();
  }

  private static int findUnescaped(char ch, String val, int start) {
    int len = val.length();
    while (start < len) {
      int where = val.indexOf(ch, start);
      if (where == start || where == -1 || val.charAt(where - 1) != '\\') {
        return where;
      }
      start = where + 1;
    }
    return -1;
  }

  @Nullable
  private static String encodeFilterValue(@Nullable Object obj) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof byte[]) {
      return HexFormat.of().withUpperCase().withPrefix("\\").formatHex((byte[]) obj);
    }
    String str = obj instanceof String ? (String) obj : obj.toString();
    StringBuilder sb = new StringBuilder(str.length());
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      switch (ch) {
        case '*':
          sb.append("\\2a");
          break;
        case '(':
          sb.append("\\28");
          break;
        case ')':
          sb.append("\\29");
          break;
        case '\\':
          sb.append("\\5c");
          break;
        case 0:
          sb.append("\\00");
          break;
        default:
          sb.append(ch);
          break;
      }
    }
    return sb.toString();
  }

  private String buildLdapSearchCommand(DirContext ctx, String renderedFilter) {
    String providerUrl = "<LDAP_URL>";
    String bindDn = "<BIND_DN>";
    boolean useStartTls = false;
    try {
      Hashtable<?, ?> env = ctx.getEnvironment();
      Object provider = env.get(Context.PROVIDER_URL);
      if (provider != null) {
        providerUrl = provider.toString();
      }
      Object principal = env.get(Context.SECURITY_PRINCIPAL);
      if (principal != null) {
        bindDn = principal.toString();
      }
      useStartTls = env.containsKey(Helper.STARTTLS_PROPERTY);
    } catch (NamingException e) {
      // Keep placeholders when context environment cannot be read.
    }

    StringJoiner cmd = new StringJoiner(" ");
    cmd.add("ldapsearch");
    cmd.add("-x");
    if (useStartTls && providerUrl.startsWith("ldap:")) {
      cmd.add("-ZZ");
    }
    cmd.add("-H").add(shellQuote(providerUrl));
    cmd.add("-D").add(shellQuote(bindDn));
    cmd.add("-w").add(shellQuote("<PASSWORD>"));
    cmd.add("-b").add(shellQuote(base));
    cmd.add("-s").add(ldapSearchScopeFlag(searchScope));
    cmd.add(shellQuote(renderedFilter));
    if (returnAttributes != null && returnAttributes.length > 0) {
      for (String attr : returnAttributes) {
        cmd.add(shellQuote(attr));
      }
    } else {
      cmd.add("*");
    }
    return cmd.toString();
  }

  private static String ldapSearchScopeFlag(SearchScope scope) {
    switch (scope) {
      case OBJECT:
      case BASE:
        return "base";
      case ONE:
        return "one";
      case SUBTREE:
      case SUB:
        return "sub";
      default:
        return "sub";
    }
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  class Result {
    private final Map<String, Attribute> atts = new HashMap<>();

    Result(SearchResult sr) {
      if (returnAttributes != null) {
        for (String attName : returnAttributes) {
          final Attribute a = sr.getAttributes().get(attName);
          if (a != null && a.size() > 0) {
            atts.put(attName, a);
          }
        }

      } else {
        NamingEnumeration<? extends Attribute> e = sr.getAttributes().getAll();
        while (e.hasMoreElements()) {
          final Attribute a = e.nextElement();
          atts.put(a.getID(), a);
        }
      }

      atts.put("dn", new BasicAttribute("dn", sr.getNameInNamespace()));
    }

    String getDN() throws NamingException {
      return get("dn");
    }

    @Nullable
    String get(String attName) throws NamingException {
      final Attribute att = getAll(attName);
      return att != null && 0 < att.size() ? String.valueOf(att.get(0)) : null;
    }

    Attribute getAll(String attName) {
      return atts.get(attName);
    }

    Set<String> attributes() {
      return Collections.unmodifiableSet(atts.keySet());
    }

    @Override
    public String toString() {
      try {
        return getDN();
      } catch (NamingException e) {
        return "";
      }
    }
  }
}
