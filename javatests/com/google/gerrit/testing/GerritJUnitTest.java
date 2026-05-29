// Copyright (C) 2019 The Android Open Source Project
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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import org.junit.Test;

public class GerritJUnitTest {
  private static class MyException extends Exception {
    private static final long serialVersionUID = 1L;

    MyException(String msg) {
      super(msg);
    }
  }

  private static class MySubException extends MyException {
    private static final long serialVersionUID = 1L;

    MySubException(String msg) {
      super(msg);
    }
  }

  @Test
  public void assertThrowsCatchesSpecifiedExceptionType() {
    MyException e =
        assertThrows(
            MyException.class,
            () -> {
              throw new MyException("foo");
            });
    assertThat(e).hasMessageThat().isEqualTo("foo");
  }

  @Test
  public void assertThrowsCatchesSubclassOfSpecifiedExceptionType() {
    MyException e =
        assertThrows(
            MyException.class,
            () -> {
              throw new MySubException("foo");
            });
    assertThat(e).isInstanceOf(MySubException.class);
    assertThat(e).hasMessageThat().isEqualTo("foo");
  }

  @Test
  public void assertThrowsConvertsUnexpectedExceptionTypeToAssertionError() {
    try {
      assertThrows(
          IllegalStateException.class,
          () -> {
            throw new MyException("foo");
          });
      assertWithMessage("expected AssertionError").fail();
    } catch (AssertionError e) {
      assertThat(e).hasMessageThat().contains(IllegalStateException.class.getSimpleName());
      assertThat(e).hasMessageThat().contains(MyException.class.getSimpleName());
      assertThat(e).hasCauseThat().isInstanceOf(MyException.class);
      assertThat(e).hasCauseThat().hasMessageThat().isEqualTo("foo");
    }
  }

  @Test
  public void assertThrowsThrowsAssertionErrorWhenNothingThrown() {
    try {
      assertThrows(MyException.class, () -> {});
      assertWithMessage("expected AssertionError").fail();
    } catch (AssertionError e) {
      assertThat(e).hasMessageThat().contains(MyException.class.getSimpleName());
      assertThat(e).hasCauseThat().isNull();
    }
  }
}
