// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client;

import static com.google.gerrit.client.DateTimeReplacer.replaceTimestamps;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DateTimeReplacerTest {
  @Test
  public void addTimeZone() {
    assertEquals("before:\"2006-01-02 15:04:05 Z\"",
        replaceTimestamps("before:\"2006-01-02 15:04:05\"", "Z"));
  }

  @Test
  public void addTimeAndZone() {
    assertEquals("before:\"2006-01-02 00:00:00 Z\"",
        replaceTimestamps("before:2006-01-02", "Z"));
    assertEquals("before:\"2006-01-02 00:00:00 Z\"",
        replaceTimestamps("before:\"2006-01-02\"", "Z"));
  }

  @Test
  public void hasTimeZone() {
    String q = "before:\"2006-01-02 15:04:05 -0700\"";
    assertEquals(q, replaceTimestamps(q));
    q = "before:\"2006-01-02 15:04:05.999999 -0700\"";
    assertEquals(q, replaceTimestamps(q));
  }

  @Test
  public void afterNonTimestampPredicate() {
    assertEquals("branch:foo before:\"2006-01-02 00:00:00 Z\"",
        replaceTimestamps("branch:foo before:2006-01-02", "Z"));
  }

  @Test
  public void beforeNonTimestampPredicate() {
    assertEquals("before:\"2006-01-09 00:00:00 Z\" branch:foo",
        replaceTimestamps("before:2006-01-09 branch:foo", "Z"));
    assertEquals("before:\"2006-01-09 00:00:00 Z\" branch:foo",
        replaceTimestamps("before:\"2006-01-09 00:00:00 Z\" branch:foo", "Z"));
  }

  @Test
  public void betweenNonTimestampPredicates() {
    assertEquals("project:bar before:\"2006-01-02 00:00:00 Z\" branch:foo",
        replaceTimestamps("project:bar before:2006-01-02 branch:foo", "Z"));
  }

  @Test
  public void multiplePredicates() {
    assertEquals(
        "before:\"2006-01-02 00:00:00 Z\" after:\"2005-01-02 00:00:00 Z\"",
        replaceTimestamps("before:2006-01-02 after:2005-01-02", "Z"));
    assertEquals(
        "before:\"2006-01-02 00:00:00 Z\" after:\"2005-01-02 00:00:00 Z\"",
        replaceTimestamps(
            "before:\"2006-01-02 00:00:00 Z\" after:2005-01-02", "Z"));
    assertEquals(
        "before:\"2006-01-02 00:00:00 Z\" after:\"2005-01-02 00:00:00 Z\"",
        replaceTimestamps(
            "before:2006-01-02 after:\"2005-01-02 00:00:00 Z\"", "Z"));
  }

  @Test
  public void predicateInQuotes() {
    String q = "message:\"before:2006-01-02\"";
    assertEquals(q, replaceTimestamps(q));
  }

  @Test
  public void predicateAfterSpace() {
    String q = "message:\"something before:2006-01-02\"";
    assertEquals(q, replaceTimestamps(q));
  }

  @Test
  public void predicateInWord() {
    String q = "messagebefore:2006-01-02";
    assertEquals(q, replaceTimestamps(q));
  }
}
