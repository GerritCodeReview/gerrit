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

package com.google.gerrit.server.change;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HashtagsUtil {
  private static final CharMatcher LEADER = CharMatcher.whitespace().or(CharMatcher.is('#'));
  private static final String PATTERN = "(?:\\s|\\A)#[\\p{L}[0-9]-_]+";

  public static String cleanupHashtag(String hashtag) {
    hashtag = LEADER.trimLeadingFrom(hashtag);
    hashtag = CharMatcher.whitespace().trimTrailingFrom(hashtag);
    return hashtag;
  }

  public static Set<String> extractTags(String input) {
    Set<String> result = new HashSet<>();
    if (!Strings.isNullOrEmpty(input)) {
      Matcher matcher = Pattern.compile(PATTERN).matcher(input);
      while (matcher.find()) {
        result.add(cleanupHashtag(matcher.group()));
      }
    }
    return result;
  }

  static Set<String> extractTags(Set<String> input) throws IllegalArgumentException {
    if (input == null) {
      return Collections.emptySet();
    }
    HashSet<String> result = new HashSet<>();
    for (String hashtag : input) {
      if (hashtag.contains(",")) {
        throw new IllegalArgumentException("Hashtags may not contain commas");
      }
      hashtag = cleanupHashtag(hashtag);
      if (!hashtag.isEmpty()) {
        result.add(hashtag);
      }
    }
    return result;
  }

  private HashtagsUtil() {}
}
