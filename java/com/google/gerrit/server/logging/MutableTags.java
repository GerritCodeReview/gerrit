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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.flogger.backend.Tags;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class MutableTags {
  private final SortedMap<String, SortedSet<String>> tagMap = new TreeMap<>();
  private Tags tags = Tags.empty();

  public Tags getTags() {
    return tags;
  }

  /**
   * Adds a tag if a tag with the same name and value doesn't exist yet.
   *
   * @param name the name of the tag
   * @param value the value of the tag
   * @return {@code true} if the tag was added, {@code false} if the tag was not added because it
   *     already exists
   */
  public synchronized boolean add(String name, String value) {
    checkNotNull(name, "tag name is required");
    checkNotNull(value, "tag value is required");

    SortedSet<String> values = tagMap.get(name);
    if (values != null && values.contains(value)) {
      return false;
    }
    if (values == null) {
      values = new TreeSet<>();
      tagMap.put(name, values);
    }
    values.add(value);
    buildTags();
    return true;
  }

  /**
   * Removes the tag with the given name and value.
   *
   * @param name the name of the tag
   * @param value the value of the tag
   */
  public synchronized void remove(String name, String value) {
    checkNotNull(name, "tag name is required");
    checkNotNull(value, "tag value is required");

    SortedSet<String> values = tagMap.get(name);
    if (values == null || !values.contains(value)) {
      return;
    }
    values.remove(value);
    if (values.isEmpty()) {
      tagMap.remove(name);
    }
    buildTags();
  }

  /** Clears all tags. */
  public synchronized void clear() {
    tagMap.clear();
    tags = Tags.empty();
  }

  /**
   * Replaces the existing tags with the tags from the given Tags object. The provided Tags object
   * must only contain String tags. If other tag types are contained this method throws {@link
   * IllegalArgumentException}.
   *
   * @param tags the tags that should be set.
   * @throws IllegalArgumentException if the provide Tags object contains any non-String tags
   */
  synchronized void set(Tags tags) {
    tagMap.clear();
    for (Map.Entry<String, SortedSet<Object>> e : tags.asMap().entrySet()) {
      String tagName = e.getKey();
      if (!tagMap.containsKey(tagName)) {
        tagMap.put(tagName, new TreeSet<>());
      }
      for (Object value : e.getValue()) {
        checkArgument(
            value instanceof String,
            "tag %s has an unsupported type: %s",
            tagName,
            value.getClass().getName());
        tagMap.get(tagName).add((String) value);
      }
    }
    buildTags();
  }

  private void buildTags() {
    if (tagMap.isEmpty()) {
      if (tags.isEmpty()) {
        return;
      }
      tags = Tags.empty();
      return;
    }

    Tags.Builder tagsBuilder = Tags.builder();
    for (Map.Entry<String, SortedSet<String>> e : tagMap.entrySet()) {
      for (String value : e.getValue()) {
        tagsBuilder.addTag(e.getKey(), value);
      }
    }
    tags = tagsBuilder.build();
  }
}
