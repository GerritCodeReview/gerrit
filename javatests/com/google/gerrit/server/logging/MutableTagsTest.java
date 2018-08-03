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

import java.util.SortedMap;
import java.util.SortedSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MutableTagsTest {
  private MutableTags tags;

  @Rule public ExpectedException exception = ExpectedException.none();

  @Before
  public void setup() {
    tags = new MutableTags();
  }

  @Test
  public void addTag() {
    assertThat(tags.add("name", "value")).isTrue();

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name");
    assertThat(tagMap.get("name")).containsExactly("value");
  }

  @Test
  public void addTagsWithDifferentName() {
    assertThat(tags.add("name1", "value1")).isTrue();
    assertThat(tags.add("name2", "value2")).isTrue();

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name1", "name2");
    assertThat(tagMap.get("name1")).containsExactly("value1");
    assertThat(tagMap.get("name2")).containsExactly("value2");
  }

  @Test
  public void addTagsWithSameNameButDifferentValues() {
    assertThat(tags.add("name", "value1")).isTrue();
    assertThat(tags.add("name", "value2")).isTrue();

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name");
    assertThat(tagMap.get("name")).containsExactly("value1", "value2");
  }

  @Test
  public void addTagsWithSameNameAndSameValue() {
    assertThat(tags.add("name", "value")).isTrue();
    assertThat(tags.add("name", "value")).isFalse();

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name");
    assertThat(tagMap.get("name")).containsExactly("value");
  }

  @Test
  public void getEmptyTags() {
    assertThat(tags.getTags().isEmpty()).isTrue();
  }

  @Test
  public void getTagsIsSorted() {
    assertThat(tags.add("b", "1")).isTrue();
    assertThat(tags.add("a", "2")).isTrue();
    assertThat(tags.add("a", "1")).isTrue();
    assertThat(tags.add("b", "2")).isTrue();

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("a", "b").inOrder();
    assertThat(tagMap.get("a")).containsExactly("1", "2").inOrder();
    assertThat(tagMap.get("b")).containsExactly("1", "2").inOrder();
  }

  @Test
  public void removeTags() {
    tags.add("name1", "value1");
    tags.add("name1", "value2");
    tags.add("name2", "value");

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name1", "name2");
    assertThat(tagMap.get("name1")).containsExactly("value1", "value2");
    assertThat(tagMap.get("name2")).containsExactly("value");

    tags.remove("name2", "value");

    tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name1");
    assertThat(tagMap.get("name1")).containsExactly("value1", "value2");

    tags.remove("name1", "value1");

    tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name1");
    assertThat(tagMap.get("name1")).containsExactly("value2");

    tags.remove("name1", "value2");
    tagMap = tags.getTags().asMap();
    assertThat(tagMap).isEmpty();
  }

  @Test
  public void removeNonExistingTag() {
    tags.add("name", "value");

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name");
    assertThat(tagMap.get("name")).containsExactly("value");

    tags.remove("foo", "bar");

    tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name");
    assertThat(tagMap.get("name")).containsExactly("value");

    tags.remove("name", "foo");

    tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name");
    assertThat(tagMap.get("name")).containsExactly("value");
  }

  @Test
  public void clearTags() {
    tags.add("name1", "value1");
    tags.add("name1", "value2");
    tags.add("name2", "value");

    SortedMap<String, SortedSet<Object>> tagMap = tags.getTags().asMap();
    assertThat(tagMap.keySet()).containsExactly("name1", "name2");
    assertThat(tagMap.get("name1")).containsExactly("value1", "value2");
    assertThat(tagMap.get("name2")).containsExactly("value");

    tags.clear();

    assertThat(tags.getTags().isEmpty()).isTrue();
  }

  @Test
  public void addTagNullNameNotAllowed() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("tag name is required");
    tags.add(null, "foo");
  }

  @Test
  public void addTagNullValueNotAllowed() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("tag value is required");
    tags.add("foo", null);
  }

  @Test
  public void removeTagNullNameNotAllowed() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("tag name is required");
    tags.remove(null, "foo");
  }

  @Test
  public void removeTagNullValueNotAllowed() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("tag value is required");
    tags.remove("foo", null);
  }
}
