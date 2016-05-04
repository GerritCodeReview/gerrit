// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.errors.InvalidNameException;

import dk.brics.automaton.RegExp;

import org.eclipse.jgit.lib.Repository;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RefPattern {
  public static final String USERNAME = "username";

  public static String shortestExample(String refPattern) {
    if (isRE(refPattern)) {
      // Since Brics will substitute dot [.] with \0 when generating
      // shortest example, any usage of dot will fail in
      // Repository.isValidRefName() if not combined with star [*].
      // To get around this, we substitute the \0 with an arbitrary
      // accepted character.
      return toRegExp(refPattern).toAutomaton().getShortestExample(true)
          .replace('\0', '-');
    } else if (refPattern.endsWith("/*")) {
      return refPattern.substring(0, refPattern.length() - 1) + '1';
    } else {
      return refPattern;
    }
  }

  public static boolean isRE(String refPattern) {
    return refPattern.startsWith(AccessSection.REGEX_PREFIX);
  }

  public static RegExp toRegExp(String refPattern) {
    if (isRE(refPattern)) {
      refPattern = refPattern.substring(1);
    }
    return new RegExp(refPattern, RegExp.NONE);
  }

  public static void validate(String refPattern)
      throws InvalidNameException {
    if (refPattern.startsWith(RefConfigSection.REGEX_PREFIX)) {
      if (!Repository.isValidRefName(shortestExample(refPattern))) {
        throw new InvalidNameException(refPattern);
      }
    } else if (refPattern.equals(RefConfigSection.ALL)) {
      // This is a special case we have to allow, it fails below.
    } else if (refPattern.endsWith("/*")) {
      String prefix = refPattern.substring(0, refPattern.length() - 2);
      if (!Repository.isValidRefName(prefix)) {
        throw new InvalidNameException(refPattern);
      }
    } else if (!Repository.isValidRefName(refPattern)) {
      throw new InvalidNameException(refPattern);
    }
    validateRegExp(refPattern);
  }

  public static void validateRegExp(String refPattern)
      throws InvalidNameException {
    try {
      Pattern.compile(refPattern.replace("${" + USERNAME + "}/", ""));
    } catch (PatternSyntaxException e) {
      throw new InvalidNameException(refPattern + " " + e.getMessage());
    }
  }
}
