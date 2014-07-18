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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

abstract class LdapType {
  static final LdapType RFC_2307 = new Rfc2307();

  static LdapType guessType(final DirContext ctx) throws NamingException {
    final Attributes rootAtts = ctx.getAttributes("");
    Attribute supported = rootAtts.get("supportedCapabilities");
    if (supported != null && (supported.contains("1.2.840.113556.1.4.800")
          || supported.contains("1.2.840.113556.1.4.1851"))) {
      return new ActiveDirectory();
    }

    return RFC_2307;
  }

  abstract String groupPattern();

  abstract String groupMemberPattern();

  abstract String groupName();

  abstract String accountFullName();

  abstract String accountEmailAddress();

  abstract String accountSshUserName();

  abstract String accountMemberField();

  abstract String memberField();

  abstract String accountLoginName();

  abstract Filter groupFilter();

  abstract Filter userFilter();

  abstract String accountPattern();

  private static class Rfc2307 extends LdapType {
    @Override
    String groupPattern() {
      return "(cn=${groupname})";
    }

    @Override
    String groupMemberPattern() {
      return "(|(" + memberField() + "=${username})(gidNumber=${gidNumber}))";
    }

    @Override
    String memberField() {
      return "memberUid";
    }

    @Override
    String groupName() {
      return "cn";
    }

    @Override
    String accountFullName() {
      return "displayName";
    }

    @Override
    String accountEmailAddress() {
      return "mail";
    }

    @Override
    String accountSshUserName() {
      return "uid";
    }

    @Override
    String accountMemberField() {
      return null; // Not defined in RFC 2307
    }

    @Override
    String accountPattern() {
      return "(" + accountLoginName() + "=${username})";
    }

    @Override
    String accountLoginName() {
      return "uid";
    }

    @Override
    Filter groupFilter() {
      return null;// RFC 2307 does not allow nested groups.
    }

    @Override
    Filter userFilter() {
      return null;
    }
  }

  private static class ActiveDirectory extends LdapType {
    private static Filter gf;
    private static Filter uf;

    @Override
    String groupPattern() {
      return "(&(objectClass=group)(cn=${groupname}))";
    }

    @Override
    String groupName() {
      return "cn";
    }

    @Override
    String groupMemberPattern() {
      return null; // Active Directory uses memberOf in the account
    }

    @Override
    String memberField() {
      return "member";
    }

    @Override
    String accountFullName() {
      return "${givenName} ${sn}";
    }

    @Override
    String accountEmailAddress() {
      return "mail";
    }

    @Override
    String accountSshUserName() {
      return "${sAMAccountName.toLowerCase}";
    }

    @Override
    String accountMemberField() {
      return "memberOf";
    }

    @Override
    String accountPattern() {
      return "(&(objectClass=user)(" + accountLoginName() + "=${username}))";
    }

    @Override
    String accountLoginName() {
      return "sAMAccountName";
    }

    @Override
    Filter groupFilter() {
      if (gf == null) {
        Map<String, String[]> r = Maps.newHashMap();
        r.put("objectClass", new String[] {"group"});
        gf = new Filter(r);
      }
      return gf;
    }

    @Override
    Filter userFilter() {
      if (uf == null) {
        Map<String, String[]> r = Maps.newHashMap();
        r.put("objectClass", new String[] {"user"});
        r.put("objectClass", new String[] {"!computer"});
        uf = new Filter(r);
      }
      return uf;
    }
  }
}

class Filter {
  private final Map<String, Set<String>> equalitys;
  private final Map<String, Set<String>> nots ;
  private final Map<String, String[]> origin;
  private final Set<String> attrIds;

  Filter(Map<String, String[]> filter) {
    origin = filter;
    equalitys = Maps.newHashMap();
    nots = Maps.newHashMap();
    for (Map.Entry<String, String[]> e : filter.entrySet()) {
      ImmutableSet.Builder<String> not = ImmutableSet.builder();
      ImmutableSet.Builder<String> equal = ImmutableSet.builder();
      for (String v : e.getValue()) {
        if (v.startsWith("!")) {
          not.add(v.substring("!".length()));
        }else{
          equal.add(v);
        }
      }
      Set<String> s = not.build();
      if (s.size() > 0) {
        nots.put(e.getKey(), s);
      }
      s = equal.build();
      if (s.size() > 0) {
        equalitys.put(e.getKey(), s);
      }
    }
    attrIds = origin.keySet();
  }

  Set<String> attrIds() {
    return attrIds;
  }

  boolean accept(Attributes entry) {
    // limited support:
    // "(&(|(type1=val1)(type1=val2)...)(!(type1=val3))(!(type1=val4))...)(type2...))".
    return matchEquals(entry) && matchNots(entry);
  }

  private boolean matchEquals(Attributes entry) {
    for (Entry<String, Set<String>> e : equalitys.entrySet()) {
      if (containsAny(e.getValue(), entry.get(e.getKey()))) {
        continue;
      }
      return false;
    }
    return true;
  }

  private boolean matchNots(Attributes entry) {
    for (Entry<String, Set<String>> e : nots.entrySet()) {
      if (containsAny(e.getValue(), entry.get(e.getKey()))) {
        return false;
      }
    }
    return true;
  }

  private boolean containsAny(Set<String> values, Attribute attribute) {
    for (String v : values) {
      if (attribute.contains(v)) {
        return true;
      }
    }
    return false;
  }
}
