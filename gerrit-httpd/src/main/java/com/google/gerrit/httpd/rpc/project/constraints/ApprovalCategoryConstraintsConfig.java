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

package com.google.gerrit.httpd.rpc.project.constraints;

import com.google.gerrit.common.errors.ForbiddenRefRightException;
import com.google.gerrit.common.errors.InvalidRegExpException;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class represents the parsed "access" (sub)sections of the gerrit.config
 * file, plus the logic to check if a (new) access right matches corresponding
 * set of constraints.
 */
@Singleton
public class ApprovalCategoryConstraintsConfig {

  private final Map<ApprovalCategory.Id, List<Allow>> constraints =
      new HashMap<ApprovalCategory.Id, List<Allow>>();

  @Inject
  ApprovalCategoryConstraintsConfig(@GerritServerConfig final Config cfg)
      throws InvalidConstraintException {
    parseConstraints(cfg);
  }

  /**
   * Checks if setting an access right (ref, min, max) for the given categoryId
   * is allowed.
   *
   * @param categoryId
   * @param ref
   * @param min
   * @param max
   * @throws ForbiddenRefRightException if setting access right is forbidden
   * @throws InvalidRegExpException if the given ref is an invalid regular
   *         expression
   */
  public void checkIfAllowed(ApprovalCategory.Id categoryId, String ref,
      short min, short max) throws ForbiddenRefRightException,
      InvalidRegExpException {
    List<Allow> allows = constraints.get(categoryId);
    if (allows == null) {
      return;
    }

    for (Allow a : allows) {
      if (a.isAllowed(ref, min, max)) {
        return;
      }
    }

    List<String> allowStrings = new ArrayList<String>(allows.size());
    for (Allow a : allows) {
      allowStrings.add(a.toString());
    }
    throw new ForbiddenRefRightException(allowStrings);
  }

  private void parseConstraints(Config cfg) throws InvalidConstraintException {
    Set<String> subsections = cfg.getSubsections("access");
    for (String s : subsections) {
      parse(cfg, new ApprovalCategory.Id(s));
    }
  }

  private void parse(Config cfg, ApprovalCategory.Id categoryId)
      throws InvalidConstraintException {
    String[] allows = cfg.getStringList("access", categoryId.get(), "allow");
    List<Allow> allowList = new ArrayList<Allow>();
    constraints.put(categoryId, allowList);
    for (String allow : allows) {
      allowList.add(parseAllow(allow, categoryId));
    }
  }

  static private Allow parseAllow(String value, ApprovalCategory.Id categoryId)
      throws InvalidConstraintException {
    try {
      String refPattern;
      short min = Short.MIN_VALUE;
      short max = Short.MAX_VALUE;

      int i = value.lastIndexOf(":");
      if (i >= 0) {
        refPattern = value.substring(0, i);
        if (i + 1 < value.length()) {
          String range = value.substring(i + 1);
          i = range.indexOf(",");
          if (i == -1) {
            max = parseMax(range);
          } else {
            min = parseMin(range.substring(0, i));
            if (i + 1 < range.length()) {
              max = parseMax(range.substring(i + 1));
            }
          }
        }
      } else {
        refPattern = value;
      }

      refPattern = refPattern.trim();
      if (min <= max) {
        return new Allow(refPattern, min, max);
      } else {
        return new Allow(refPattern, max, min);
      }
    } catch (NumberFormatException e) {
      throw new InvalidConstraintException(value, categoryId, e);
    } catch (InvalidRegExpException e) {
      throw new InvalidConstraintException(value, categoryId, e);
    } catch (InvalidRangeException e) {
      throw new InvalidConstraintException(value, categoryId, e);
    }
  }

  static private short parseMin(String value) {
    return parseShort(value, Short.MIN_VALUE);
  }

  static private short parseMax(String value) {
    return parseShort(value, Short.MAX_VALUE);
  }

  static private short parseShort(String value, short defaultIfEmpty) {
    value = value.trim();
    if (value.length() > 0) {
      if (value.startsWith("+")) {
        return Short.parseShort(value.substring(1));
      } else {
        return Short.parseShort(value);
      }
    } else {
      return defaultIfEmpty;
    }
  }
}
