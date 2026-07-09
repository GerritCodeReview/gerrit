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

package com.google.gerrit.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import org.junit.Test;

/**
 * Regression test for the Guice bundled-ASM incompatibility with Java 25 class files (<a
 * href="https://github.com/google/guice/issues/1926">guice#1926</a>).
 *
 * <p>When Guice formats the source location for a configuration error it reads the offending class
 * with ASM. The ASM shaded into the default Guice jar cannot read Java 25 (class major version 69)
 * bytecode: the read throws, Guice logs a warning, and the source silently degrades to {@code
 * (Unknown Source)}. Building against the {@code classes} classifier plus an external OW2 ASM makes
 * the read succeed, so the error names the real {@code File.java:line}.
 *
 * <p>The check below therefore asserts on the resolved source (present only when ASM works), not on
 * the swallowed log line — otherwise it would pass with or without the fix.
 */
public class GuiceErrorFormattingTest {
  @Test
  public void configurationErrorResolvesSourceLocationOnJava25() {
    ConfigurationException thrown =
        assertThrows(
            ConfigurationException.class,
            () -> Guice.createInjector().getInstance(MissingBinding.class));

    // Sanity: this is the missing-constructor error we set out to provoke.
    assertThat(thrown).hasMessageThat().contains("No injectable constructor for type");

    // The guard: source formatting must resolve the class's real file:line via ASM. With the
    // bundled Guice ASM on Java 25 the read fails and the source degrades to "(Unknown Source)".
    assertThat(thrown).hasMessageThat().contains("GuiceErrorFormattingTest.java:");
    assertThat(thrown).hasMessageThat().doesNotContain("Unknown Source");
  }

  private static class MissingBinding {
    @SuppressWarnings("UnusedMethod")
    MissingBinding(String value) {
      throw new AssertionError(value);
    }
  }
}
