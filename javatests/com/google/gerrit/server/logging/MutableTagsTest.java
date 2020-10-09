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

package com.google.gerrit.server.logging;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class MutableTagsTest {
  private MutableTags tags;

  @Before
  public void setup() {
    tags = new MutableTags();
  }

  @Test
  public void addTag() {
    assertThat(tags.add("name", "value")).isTrue();
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value")));
  }

  @Test
  public void addTagsWithDifferentName() {
    assertThat(tags.add("name1", "value1")).isTrue();
    assertThat(tags.add("name2", "value2")).isTrue();
    assertTags(
        ImmutableMap.of("name1", ImmutableSet.of("value1"), "name2", ImmutableSet.of("value2")));
  }

  @Test
  public void addTagsWithSameNameButDifferentValues() {
    assertThat(tags.add("name", "value1")).isTrue();
    assertThat(tags.add("name", "value2")).isTrue();
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value1", "value2")));
  }

  @Test
  public void addTagsWithSameNameAndSameValue() {
    assertThat(tags.add("name", "value")).isTrue();
    assertThat(tags.add("name", "value")).isFalse();
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value")));
  }

  @Test
  public void getEmptyTags() {
    assertThat(tags.getTags().isEmpty()).isTrue();
    assertTags(ImmutableMap.of());
  }

  @Test
  public void isEmpty() {
    assertThat(tags.isEmpty()).isTrue();

    tags.add("foo", "bar");
    assertThat(tags.isEmpty()).isFalse();

    tags.remove("foo", "bar");
    assertThat(tags.isEmpty()).isTrue();
  }

  @Test
  public void removeTags() {
    tags.add("name1", "value1");
    tags.add("name1", "value2");
    tags.add("name2", "value");
    assertTags(
        ImmutableMap.of(
            "name1", ImmutableSet.of("value1", "value2"), "name2", ImmutableSet.of("value")));

    tags.remove("name2", "value");
    assertTags(ImmutableMap.of("name1", ImmutableSet.of("value1", "value2")));

    tags.remove("name1", "value1");
    assertTags(ImmutableMap.of("name1", ImmutableSet.of("value2")));

    tags.remove("name1", "value2");
    assertTags(ImmutableMap.of());
  }

  @Test
  public void removeNonExistingTag() {
    tags.add("name", "value");
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value")));

    tags.remove("foo", "bar");
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value")));

    tags.remove("name", "foo");
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value")));
  }

  @Test
  public void setTags() {
    tags.add("name", "value");
    assertTags(ImmutableMap.of("name", ImmutableSet.of("value")));

    tags.set(ImmutableSetMultimap.of("foo", "bar", "foo", "baz", "bar", "baz"));
    assertTags(
        ImmutableMap.of("foo", ImmutableSet.of("bar", "baz"), "bar", ImmutableSet.of("baz")));
  }

  @Test
  public void asMap() {
    tags.add("name", "value");
    assertThat(tags.asMap()).containsExactlyEntriesIn(ImmutableSetMultimap.of("name", "value"));

    tags.set(ImmutableSetMultimap.of("foo", "bar", "foo", "baz", "bar", "baz"));
    assertThat(tags.asMap())
        .containsExactlyEntriesIn(
            ImmutableSetMultimap.of("foo", "bar", "foo", "baz", "bar", "baz"));
  }

  @Test
  public void clearTags() {
    tags.add("name1", "value1");
    tags.add("name1", "value2");
    tags.add("name2", "value");
    assertTags(
        ImmutableMap.of(
            "name1", ImmutableSet.of("value1", "value2"), "name2", ImmutableSet.of("value")));

    tags.clear();
    assertTags(ImmutableMap.of());
  }

  @Test
  public void addInvalidTag() {
    assertNullPointerException("tag name is required", () -> tags.add(null, "foo"));
    assertNullPointerException("tag value is required", () -> tags.add("foo", null));
  }

  @Test
  public void removeInvalidTag() {
    assertNullPointerException("tag name is required", () -> tags.remove(null, "foo"));
    assertNullPointerException("tag value is required", () -> tags.remove("foo", null));
  }

  private void assertTags(ImmutableMap<String, ImmutableSet<String>> expectedTagMap) {
    Map<String, ? extends Set<Object>> actualTagMap = tags.getTags().asMap();
    assertThat(actualTagMap.keySet()).containsExactlyElementsIn(expectedTagMap.keySet());
    for (Map.Entry<String, ImmutableSet<String>> expectedEntry : expectedTagMap.entrySet()) {
      assertThat(actualTagMap.get(expectedEntry.getKey()))
          .containsExactlyElementsIn(expectedEntry.getValue());
    }
  }

  private void assertNullPointerException(String expectedMessage, Runnable r) {
    NullPointerException thrown = assertThrows(NullPointerException.class, () -> r.run());
    assertThat(thrown).hasMessageThat().isEqualTo(expectedMessage);
  }
}
