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

package com.google.gerrit.server.ldap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/** Supports issuing parameterized queries against an LDAP data source. */
class LdapQuery {
  static enum SearchScope {
    // Search only the base DN
    //
    OBJECT(SearchControls.OBJECT_SCOPE), //
    BASE(SearchControls.OBJECT_SCOPE),

    // Search all entries one level under the base DN
    //
    // Does not include the base DN, and does not include items below items
    // under the base DN.
    //
    ONE(SearchControls.ONELEVEL_SCOPE),

    // Search all entries under the base DN, including the base DN.
    //
    SUBTREE(SearchControls.SUBTREE_SCOPE), //
    SUB(SearchControls.SUBTREE_SCOPE);

    private final int scope;

    SearchScope(final int scope) {
      this.scope = scope;
    }

    int scope() {
      return scope;
    }
  }

  private final String base;
  private final SearchScope searchScope;
  private final String pattern;
  private final String[] patternArgs;
  private final String[] returnAttributes;

  LdapQuery(final String base, final SearchScope searchScope,
      final String pattern, final Set<String> returnAttributes) {
    this.base = base;
    this.searchScope = searchScope;

    final StringBuilder p = new StringBuilder();
    final List<String> a = new ArrayList<String>(4);
    int i = 0;
    while (i < pattern.length()) {
      final int b = pattern.indexOf("${", i);
      if (b < 0) {
        break;
      }
      final int e = pattern.indexOf("}", b + 2);
      if (e < 0) {
        break;
      }

      p.append(pattern.substring(i, b));
      p.append("{" + a.size() + "}");
      a.add(pattern.substring(b + 2, e));
      i = e + 1;
    }
    if (i < pattern.length()) {
      p.append(pattern.substring(i));
    }
    this.pattern = p.toString();
    this.patternArgs = new String[a.size()];
    a.toArray(this.patternArgs);

    this.returnAttributes = new String[returnAttributes.size()];
    returnAttributes.toArray(this.returnAttributes);
  }

  String[] getParameters() {
    return patternArgs;
  }

  List<Result> query(final DirContext ctx, final Map<String, String> params)
      throws NamingException {
    final SearchControls sc = new SearchControls();
    final NamingEnumeration<SearchResult> res;

    sc.setSearchScope(searchScope.scope());
    sc.setReturningAttributes(returnAttributes);
    res = ctx.search(base, pattern, bind(params), sc);
    try {
      final List<Result> r = new ArrayList<Result>();
      while (res.hasMore()) {
        r.add(new Result(res.next()));
      }
      return r;
    } finally {
      res.close();
    }
  }

  private String[] bind(final Map<String, String> params) {
    final String[] r = new String[patternArgs.length];
    for (int i = 0; i < r.length; i++) {
      r[i] = params.get(patternArgs[i]);
      if (r[i] == null) {
        r[i] = "";
      }
    }
    return r;
  }

  class Result {
    private final Map<String, String> atts = new HashMap<String, String>();

    Result(final SearchResult sr) throws NamingException {
      for (final String attName : returnAttributes) {
        final Attribute a = sr.getAttributes().get(attName);
        if (a != null && a.size() > 0) {
          atts.put(attName, String.valueOf(a.get(0)));
        }
      }
      atts.put("dn", sr.getNameInNamespace());
    }

    String get(final String attName) {
      return atts.get(attName);
    }
  }
}
