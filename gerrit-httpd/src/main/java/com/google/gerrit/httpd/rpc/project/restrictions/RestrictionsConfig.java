// Copyright (C) 2011 The Android Open Source Project
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
package com.google.gerrit.httpd.rpc.project.restrictions;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.RuleNotAllowedException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;

/**
 * Represents the <code>[restrictions]</code> section of the gerrit.config.
 */
@Singleton
public class RestrictionsConfig {

  private final boolean denyTagUpdate;
  private Automaton refsTagsAutomaton;

  @Inject
  public RestrictionsConfig(@GerritServerConfig final Config cfg) {
    denyTagUpdate = cfg.getBoolean("restrictions", "denyTagUpdate", false);
  }

  public void checkPermissionRule(AccessSection section, Permission permission,
      PermissionRule rule) throws RuleNotAllowedException {
    if (! denyTagUpdate) {
      return;
    }

    if (intersectsWithRefsTags(section.getName())
        && Permission.PUSH.equals(permission.getName())
        && PermissionRule.Action.ALLOW == rule.getAction()
        && rule.getForce()) {
      throw new RuleNotAllowedException(section, permission, rule);
    }
  }

  private boolean intersectsWithRefsTags(String ref) {
    if (isRegExp(ref)) {
      return ! getRefsTagsAutomaton().intersection(
          toAutomaton(toRegExp(ref))).isEmpty();
    }
    return ref.startsWith(Constants.R_TAGS);
  }

  private Automaton getRefsTagsAutomaton() {
    if (refsTagsAutomaton == null) {
      refsTagsAutomaton = toAutomaton(toRegExp(Constants.R_TAGS + "*"));
    }
    return refsTagsAutomaton;
  }

  private String toRegExp(String ref) {
    if (isRegExp(ref)) {
      return ref;
    }
    if (isRefPattern(ref)) {
      return refPatternToRegExp(ref);
    }
    return "^" + escape(ref);
  }

  /**
   * Creates an automaton out of the given regExp.
   *
   * @param regExp a regular expression starting with "^" character.
   * @return
   */
  private Automaton toAutomaton(String regExp) {
    String re = regExp.substring(1);
    return new RegExp(re, RegExp.NONE).toAutomaton();
  }

  private boolean isRegExp(String refPattern) {
    return refPattern.startsWith(AccessSection.REGEX_PREFIX);
  }

  private boolean isRefPattern(String ref) {
    return !isRegExp(ref) && ref.endsWith("/*");
  }

  private String refPatternToRegExp(String refPattern) {
    return "^" + escape(refPattern.substring(0, refPattern.length() - 1))
        + ".*";
  }

  /**
   * Escapes s so it can be used to match exactly this string in a regex.
   *
   * @param s
   * @return
   */
  private String escape(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isLetterOrDigit(c)) {
        b.append("\\");
      }
      b.append(c);
    }
    return b.toString();
  }
}
