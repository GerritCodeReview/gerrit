// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.project.RefPattern.isRE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import dk.brics.automaton.Automaton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class RefPatternMatcher {
  public static RefPatternMatcher getMatcher(String pattern) {
    if (pattern.contains("${")) {
      return new ExpandParameters(pattern);
    } else if (isRE(pattern)) {
      return new Regexp(pattern);
    } else if (pattern.endsWith("/*")) {
      return new Prefix(pattern.substring(0, pattern.length() - 1));
    } else {
      return new Exact(pattern);
    }
  }

  public abstract boolean match(String ref, CurrentUser user);

  private static class Exact extends RefPatternMatcher {
    private final String expect;

    Exact(String name) {
      expect = name;
    }

    @Override
    public boolean match(String ref, CurrentUser user) {
      return expect.equals(ref);
    }
  }

  private static class Prefix extends RefPatternMatcher {
    private final String prefix;

    Prefix(String pfx) {
      prefix = pfx;
    }

    @Override
    public boolean match(String ref, CurrentUser user) {
      return ref.startsWith(prefix);
    }
  }

  private static class Regexp extends RefPatternMatcher {
    private final Pattern pattern;

    Regexp(String re) {
      pattern = Pattern.compile(re);
    }

    @Override
    public boolean match(String ref, CurrentUser user) {
      return pattern.matcher(ref).matches();
    }
  }

  static class ExpandParameters extends RefPatternMatcher {
    private final ParameterizedString template;
    private final String prefix;

    ExpandParameters(String pattern) {
      template = new ParameterizedString(pattern);

      if (isRE(pattern)) {
        // Replace ${username} and ${shardeduserid} with ":PLACEHOLDER:"
        // as : is not legal in a reference and the string :PLACEHOLDER:
        // is not likely to be a valid part of the regex. This later
        // allows the pattern prefix to be clipped, saving time on
        // evaluation.
        String replacement = ":PLACEHOLDER:";
        Map<String, String> params =
            ImmutableMap.of(
                RefPattern.USERID_SHARDED, replacement,
                RefPattern.USERNAME, replacement);
        Automaton am = RefPattern.toRegExp(template.replace(params)).toAutomaton();
        String rePrefix = am.getCommonPrefix();
        prefix = rePrefix.substring(0, rePrefix.indexOf(replacement));
      } else {
        prefix = pattern.substring(0, pattern.indexOf("${"));
      }
    }

    @Override
    public boolean match(String ref, CurrentUser user) {
      if (!ref.startsWith(prefix)) {
        return false;
      }

      for (String username : getUsernames(user)) {
        String u;
        if (isRE(template.getPattern())) {
          u = Pattern.quote(username);
        } else {
          u = username;
        }

        Account.Id accountId = user.isIdentifiedUser() ? user.getAccountId() : null;
        RefPatternMatcher next = getMatcher(expand(template, u, accountId));
        if (next != null && next.match(expand(ref, u, accountId), user)) {
          return true;
        }
      }
      return false;
    }

    private Iterable<String> getUsernames(CurrentUser user) {
      if (user.isIdentifiedUser()) {
        Set<String> emails = user.asIdentifiedUser().getEmailAddresses();
        if (user.getUserName() == null) {
          return emails;
        } else if (emails.isEmpty()) {
          return ImmutableSet.of(user.getUserName());
        }
        return Iterables.concat(emails, ImmutableSet.of(user.getUserName()));
      }
      if (user.getUserName() != null) {
        return ImmutableSet.of(user.getUserName());
      }
      return ImmutableSet.of();
    }

    boolean matchPrefix(String ref) {
      return ref.startsWith(prefix);
    }

    private String expand(String parameterizedRef, String userName, Account.Id accountId) {
      if (parameterizedRef.contains("${")) {
        return expand(new ParameterizedString(parameterizedRef), userName, accountId);
      }
      return parameterizedRef;
    }

    private String expand(
        ParameterizedString parameterizedRef, String userName, Account.Id accountId) {
      Map<String, String> params = new HashMap<>();
      params.put(RefPattern.USERNAME, userName);
      if (accountId != null) {
        params.put(RefPattern.USERID_SHARDED, RefNames.shard(accountId.get()));
      }
      return parameterizedRef.replace(params);
    }
  }
}
