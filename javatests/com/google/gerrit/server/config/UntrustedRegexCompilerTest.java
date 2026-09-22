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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.UntrustedRegexCompiler.RegexPermissionException;
import com.google.gerrit.server.ioutil.DefaultRegexCompiler;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.gerrit.server.permissions.RegexPermissionPolicy.RegexNotAllowedException;
import com.google.inject.util.Providers;
import dk.brics.automaton.Automaton;
import org.junit.Test;

public class UntrustedRegexCompilerTest {
  private final RegexPermissionPolicy regexSearchPolicy = mock(RegexPermissionPolicy.class);
  private final UntrustedRegexCompiler compiler =
      new UntrustedRegexCompiler(new DefaultRegexCompiler(), Providers.of(regexSearchPolicy));

  @Test
  public void allowedUserCompilesExpression() {
    when(regexSearchPolicy.isAllowed()).thenReturn(true);

    Automaton automaton = compiler.toAutomaton("abc");

    assertThat(automaton.run("abc")).isTrue();
  }

  @Test
  public void deniedUserDoesNotCompileExpression() {
    when(regexSearchPolicy.isAllowed()).thenReturn(false);

    RegexPermissionException thrown =
        assertThrows(RegexPermissionException.class, () -> compiler.toAutomaton("["));

    assertThat(thrown.getCause()).isInstanceOf(RegexNotAllowedException.class);
  }
}
