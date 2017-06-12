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

package com.google.gerrit.server.auth.ldap;

import com.google.gerrit.common.data.ParameterizedString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  List<Result> query(final DirContext ctx, final Map<String, String> params)
      throws NamingException {
    final SearchControls sc = new SearchControls();
    final NamingEnumeration<SearchResult> res;

    sc.setSearchScope(searchScope.scope());
    sc.setReturningAttributes(returnAttributes);
    res = ctx.search(base, pattern.getRawPattern(), pattern.bind(params), sc);
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

  class Result {
    private final Map<String, Attribute> atts = new HashMap<>();

    Result(final SearchResult sr) {
      if (returnAttributes != null) {
        for (final String attName : returnAttributes) {
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

    String get(final String attName) throws NamingException {
      final Attribute att = getAll(attName);
      return att != null && 0 < att.size() ? String.valueOf(att.get(0)) : null;
    }

    Attribute getAll(final String attName) {
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
