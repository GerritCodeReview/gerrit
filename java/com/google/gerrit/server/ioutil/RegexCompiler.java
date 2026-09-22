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

import com.google.inject.ImplementedBy;
import dk.brics.automaton.Automaton;

/** Compiles regular expressions into automata. */
@FunctionalInterface
// NOTE: This is left only for stable branches for not breaking compatibility
// with all the existing code. It will be removed on master and check if all the references
// to RegexCompiler needs to be annotated with @TrustedRegex or @UntrustedRegex instead.
@ImplementedBy(DefaultRegexCompiler.class)
public interface RegexCompiler {
  Automaton toAutomaton(String pattern);
}
