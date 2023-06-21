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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CommentChangeEmailDecoratorTest {
  // A 100-character long string.
  private static String chars100 = String.join("", Collections.nCopies(25, "abcd"));

  @Test
  public void shortMessageNotShortened() {
    String message = "foo bar baz";
    assertThat(CommentChangeEmailDecoratorImpl.getShortenedCommentMessage(message))
        .isEqualTo(message);

    message = "foo bar baz.";
    assertThat(CommentChangeEmailDecoratorImpl.getShortenedCommentMessage(message))
        .isEqualTo(message);
  }

  @Test
  public void longMessageIsShortened() {
    String message = chars100 + "x";
    String expected = chars100 + " […]";
    assertThat(CommentChangeEmailDecoratorImpl.getShortenedCommentMessage(message))
        .isEqualTo(expected);
  }

  @Test
  public void shortenedToFirstLine() {
    String message = "abc\n" + chars100;
    String expected = "abc […]";
    assertThat(CommentChangeEmailDecoratorImpl.getShortenedCommentMessage(message))
        .isEqualTo(expected);
  }

  @Test
  public void shortenedToFirstSentence() {
    String message = "foo bar baz. " + chars100;
    String expected = "foo bar baz. […]";
    assertThat(CommentChangeEmailDecoratorImpl.getShortenedCommentMessage(message))
        .isEqualTo(expected);
  }
}
