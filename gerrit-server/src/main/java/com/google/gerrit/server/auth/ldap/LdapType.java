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

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

abstract class LdapType {
  static final LdapType RFC_2307 = new Rfc2307();

  static LdapType guessType(DirContext ctx) throws NamingException {
    final Attributes rootAtts = ctx.getAttributes("");
    Attribute supported = rootAtts.get("supportedCapabilities");
    if (supported != null
        && (supported.contains("1.2.840.113556.1.4.800")
            || supported.contains("1.2.840.113556.1.4.1851"))) {
      return new ActiveDirectory();
    }

    supported = rootAtts.get("supportedExtension");
    if (supported != null && supported.contains("2.16.840.1.113730.3.8.10.1")) {
      return new FreeIPA();
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

  abstract boolean accountMemberExpandGroups();

  abstract String accountPattern();

  private static class Rfc2307 extends LdapType {
    @Override
    String groupPattern() {
      return "(cn=${groupname})";
    }

    @Override
    String groupMemberPattern() {
      return "(|(memberUid=${username})(gidNumber=${gidNumber}))";
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
      return "(uid=${username})";
    }

    @Override
    boolean accountMemberExpandGroups() {
      return true;
    }
  }

  private static class ActiveDirectory extends LdapType {
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
      return "(&(objectClass=user)(sAMAccountName=${username}))";
    }

    @Override
    boolean accountMemberExpandGroups() {
      return true;
    }
  }

  private static class FreeIPA extends LdapType {

    @Override
    String groupPattern() {
      return "(cn=${groupname})";
    }

    @Override
    String groupName() {
      return "cn";
    }

    @Override
    String groupMemberPattern() {
      return null; // FreeIPA uses memberOf in the account
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
      return "memberOf";
    }

    @Override
    String accountPattern() {
      return "(uid=${username})";
    }

    @Override
    boolean accountMemberExpandGroups() {
      return false;
    }
  }
}
