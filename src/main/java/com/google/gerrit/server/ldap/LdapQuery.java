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

import com.google.gerrit.server.ParamertizedString;

import java.util.ArrayList;
import java.util.Collections;
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
  static final Set<String> ALL_ATTRIBUTES = null;

  private final String base;
  private final SearchScope searchScope;
  private final ParamertizedString pattern;
  private final String[] returnAttributes;

  LdapQuery(final String base, final SearchScope searchScope,
      final String pattern, final Set<String> returnAttributes) {
    this.base = base;
    this.searchScope = searchScope;

    this.pattern = new ParamertizedString(pattern);

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

  List<Result> query(final DirContext ctx, final Map<String, String> params)
      throws NamingException {
    final SearchControls sc = new SearchControls();
    final NamingEnumeration<SearchResult> res;

    sc.setSearchScope(searchScope.scope());
    sc.setReturningAttributes(returnAttributes);
    res = ctx.search(base, pattern.getRawPattern(), pattern.bind(params), sc);
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

  class Result {
    private final Map<String, String> atts = new HashMap<String, String>();

    Result(final SearchResult sr) throws NamingException {
      if (returnAttributes != null) {
        for (final String attName : returnAttributes) {
          final Attribute a = sr.getAttributes().get(attName);
          if (a != null && a.size() > 0) {
            atts.put(attName, String.valueOf(a.get(0)));
          }
        }
      } else {
        NamingEnumeration<? extends Attribute> e = sr.getAttributes().getAll();
        while (e.hasMoreElements()) {
          final Attribute a = e.nextElement();
          if (a.size() == 1) {
            atts.put(a.getID(), String.valueOf(a.get(0)));
          }
        }
      }
      atts.put("dn", sr.getNameInNamespace());
    }

    String get(final String attName) {
      return atts.get(attName);
    }

    Set<String> keySet() {
      return Collections.unmodifiableSet(atts.keySet());
    }

    @Override
    public String toString() {
      return atts.get("dn");
    }
  }
}
