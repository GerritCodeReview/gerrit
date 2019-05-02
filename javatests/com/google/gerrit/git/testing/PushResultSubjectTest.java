// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.git.testing;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.git.testing.PushResultSubject.parseProcessed;
import static com.google.gerrit.git.testing.PushResultSubject.trimMessages;

import org.junit.Test;

public class PushResultSubjectTest {
  @Test
  public void testTrimMessages() {
    assertThat(trimMessages(null)).isNull();
    assertThat(trimMessages("")).isEqualTo("");
    assertThat(trimMessages(" \n ")).isEqualTo("");
    assertThat(trimMessages("\n Foo\nBar\n\nProcessing changes: 1, 2, 3 done   \n"))
        .isEqualTo("Foo\nBar");
  }

  @Test
  public void testParseProcessed() {
    assertThat(parseProcessed(null)).isEmpty();
    assertThat(parseProcessed("some other output")).isEmpty();
    assertThat(parseProcessed("Processing changes: done\n")).isEmpty();
    assertThat(parseProcessed("Processing changes: refs: 1, done \n")).containsExactly("refs", 1);
    assertThat(parseProcessed("Processing changes: new: 1, updated: 2, refs: 3, done \n"))
        .containsExactly("new", 1, "updated", 2, "refs", 3)
        .inOrder();
    assertThat(
            parseProcessed(
                "Some\nlonger\nmessage\nProcessing changes: new: 1\r"
                    + "Processing changes: new: 1, updated: 1\r"
                    + "Processing changes: new: 1, updated: 2, done"))
        .containsExactly("new", 1, "updated", 2)
        .inOrder();

    // Atypical, but could potentially happen if there is an uncaught exception.
    assertThat(parseProcessed("Processing changes: refs: 1")).containsExactly("refs", 1);
  }
}
