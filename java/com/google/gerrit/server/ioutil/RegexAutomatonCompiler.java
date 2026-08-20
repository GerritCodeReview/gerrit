// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.ioutil;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.RunAutomaton;

/** Compiles regular expressions into automata. */
public interface RegexAutomatonCompiler {
  static String stripRedundantAnchors(String pattern) {
    String stripped = pattern;
    if (stripped.startsWith("^")) {
      stripped = stripped.substring(1);
    }
    if (stripped.endsWith("$") && !stripped.endsWith("\\$")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    return stripped;
  }

  Automaton compile(String pattern);

  Automaton compile(String pattern, int syntaxFlags);

  default RunAutomaton compileMatcher(String pattern) {
    return new RunAutomaton(compile(stripRedundantAnchors(pattern)));
  }
}
