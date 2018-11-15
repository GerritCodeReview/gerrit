// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HighlightSuggestionTest {

  @Test
  public void singleHighlight() throws Exception {
    String keyword = "key";
    String value = "somethingkeysomething";
    HighlightSuggestion suggestion = new HighlightSuggestion(keyword, value);
    assertEquals("something<strong>key</strong>something", suggestion.getDisplayString());
    assertEquals(value, suggestion.getReplacementString());
  }

  @Test
  public void noHighlight() throws Exception {
    String keyword = "key";
    String value = "something";
    HighlightSuggestion suggestion = new HighlightSuggestion(keyword, value);
    assertEquals(value, suggestion.getDisplayString());
    assertEquals(value, suggestion.getReplacementString());
  }

  @Test
  public void doubleHighlight() throws Exception {
    String keyword = "key";
    String value = "somethingkeysomethingkeysomething";
    HighlightSuggestion suggestion = new HighlightSuggestion(keyword, value);
    assertEquals(
        "something<strong>key</strong>something<strong>key</strong>something",
        suggestion.getDisplayString());
    assertEquals(value, suggestion.getReplacementString());
  }
}
