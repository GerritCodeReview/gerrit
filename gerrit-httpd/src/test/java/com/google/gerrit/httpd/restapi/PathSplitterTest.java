// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.restapi.IdString;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PathSplitterTest {

  @Test
  public void emptyPathYieldsEmptyList() {
    assertThat(PathSplitter.split("")).isEmpty();
    assertThat(PathSplitter.split(null)).isEmpty();
  }

  @Test
  public void trailingSlashIsOmitted() {
    assertThat(PathSplitter.split("some/path/"))
        .containsExactlyElementsIn(idStrings("some", "path"));
  }

  @Test
  public void splitPath() {
    assertThat(PathSplitter.split("some-endpoint"))
        .containsExactlyElementsIn(idStrings("some-endpoint"));
    assertThat(PathSplitter.split("1234/get")).containsExactlyElementsIn(idStrings("1234", "get"));
    assertThat(PathSplitter.split("foo/bar/end/point"))
        .containsExactlyElementsIn(idStrings("foo", "bar", "end", "point"));
  }

  @Test
  public void useOfDelimiterWithoutNumberSplitsOnSlashes() {
    assertThat(PathSplitter.split("foo/bar/+/"))
        .containsExactlyElementsIn(idStrings("foo", "bar", "+"));
    assertThat(PathSplitter.split("foo/bar/+"))
        .containsExactlyElementsIn(idStrings("foo", "bar", "+"));
    assertThat(PathSplitter.split("/+/foo/bar"))
        .containsExactlyElementsIn(idStrings("", "+", "foo", "bar"));
    assertThat(PathSplitter.split("+/foo/bar"))
        .containsExactlyElementsIn(idStrings("+", "foo", "bar"));
    assertThat(PathSplitter.split("foo/bar/+/+/bar"))
        .containsExactlyElementsIn(idStrings("foo", "bar", "+", "+", "bar"));
  }

  @Test
  public void splitPathWithProjectChangeIdDelimiter() {
    assertThat(PathSplitter.split("my-project/+/1234/get"))
        .containsExactlyElementsIn(idStrings("my-project/+/1234", "get"));

    // Check the first element followed by a number is parsed as one IdString together with the
    // number.
    assertThat(PathSplitter.split("project/contains/+/delimiter/+/1234/get"))
        .containsExactlyElementsIn(idStrings("project/contains/+/delimiter/+/1234", "get"));
  }

  @Test
  public void splitDelimiterIfFollowUpPartIsNotNumeric() {
    assertThat(PathSplitter.split("my-project/+/foo/get"))
        .containsExactlyElementsIn(idStrings("my-project", "+", "foo", "get"));
  }

  private List<IdString> idStrings(String... strings) {
    List<IdString> idStringList = new ArrayList<>();
    for (String s : strings) {
      idStringList.add(IdString.fromUrl(s));
    }
    return idStringList;
  }
}
