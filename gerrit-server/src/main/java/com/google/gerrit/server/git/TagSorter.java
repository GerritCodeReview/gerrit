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

package com.google.gerrit.server.git;

import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.extensions.api.projects.TagInfo;

import com.github.zafarkhaja.semver.ParseException;
import com.github.zafarkhaja.semver.Version;

import java.util.Comparator;

public class TagSorter {
  private static Version toVersion(String ref) {
    return Version.valueOf(ref.startsWith(R_TAGS) ?
        ref.substring(R_TAGS.length()) : ref);
  }

  public static final Comparator<TagInfo> BY_TAG_INFO =
    new Comparator<TagInfo>() {
      @Override
      public int compare(TagInfo a, TagInfo b) {
        try {
          return toVersion(a.ref).compareTo(toVersion(b.ref));
        } catch (IllegalArgumentException | ParseException e) {
          return a.ref.compareTo(b.ref);
        }
      }
    };

  public static final Comparator<String> BY_REF = new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
      try {
        return toVersion(a).compareTo(toVersion(b));
      } catch (IllegalArgumentException | ParseException e) {
        return a.compareTo(b);
      }
    }
  };
}
