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

package com.google.gerrit.server.mail.receive;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ParserUtilTest {
  @Test
  public void trimQuotationLineOnMessageWithoutQuoatationLine() throws Exception {
    assertThat(ParserUtil.trimQuotation("One line")).isEqualTo("One line");
    assertThat(ParserUtil.trimQuotation("Two\nlines")).isEqualTo("Two\nlines");
    assertThat(ParserUtil.trimQuotation("Thr\nee\nlines")).isEqualTo("Thr\nee\nlines");
  }

  @Test
  public void trimQuotationLineOnMixedMessages() throws Exception {
    assertThat(
            ParserUtil.trimQuotation(
                "One line\n"
                    + "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit)\n"
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("One line");
    assertThat(
            ParserUtil.trimQuotation(
                "One line\n"
                    + "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit) "
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("One line");
  }

  @Test
  public void trimQuotationLineOnMessagesContainingQuoationLine() throws Exception {
    assertThat(
            ParserUtil.trimQuotation(
                "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit)\n"
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("");
    assertThat(
            ParserUtil.trimQuotation(
                "On Thu, Feb 9, 2017 at 8:21 AM, ekempin (Gerrit) "
                    + "<noreply-gerritcodereview-qUgXfQecoDLHwp0MldAzig@google.com> wrote:"))
        .isEqualTo("");
  }
}
