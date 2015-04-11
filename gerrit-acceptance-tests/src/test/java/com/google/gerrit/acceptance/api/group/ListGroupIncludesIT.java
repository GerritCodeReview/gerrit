// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

@NoHttpd
public class ListGroupIncludesIT extends AbstractDaemonTest {

  @Test
  public void listNonExistingGroupIncludes_NotFound() throws Exception {
    try {
      gApi.groups().id("non-existing").includedGroups();
    } catch (ResourceNotFoundException expected) {
      // Expected.
    }
  }

  @Test
  public void listEmptyGroupIncludes() throws Exception {
    String gx = group("gx");
    assertThat(gApi.groups().id(gx).includedGroups()).isEmpty();
  }

  @Test
  public void includeNonExistingGroup() throws Exception {
    String gx = group("gx");
    try {
      gApi.groups().id(gx).addGroups("non-existing");
    } catch (UnprocessableEntityException expecetd) {
      // Expected.
    }
  }

  @Test
  public void listNonEmptyGroupIncludes() throws Exception {
    String gx = group("gx");
    String gy = group("gy");
    String gz = group("gz");
    gApi.groups().id(gx).addGroups(gy);
    gApi.groups().id(gx).addGroups(gz);
    assertIncludes(gApi.groups().id(gx).includedGroups(), gy, gz);
  }

  @Test
  public void listOneIncludeMember() throws Exception {
    String gx = group("gx");
    String gy = group("gy");
    gApi.groups().id(gx).addGroups(gy);
    assertIncludes(gApi.groups().id(gx).includedGroups(), gy);
  }

  private String group(String name) throws Exception {
    name = name(name);
    gApi.groups().create(name);
    return name;
  }

  private void assertIncludes(List<GroupInfo> includes, String... names) {
    Iterable<String> includeNames = Iterables.transform(includes,
        new Function<GroupInfo, String>() {
          @Override
          public String apply(@Nullable GroupInfo info) {
            return info.name;
          }
        });
    assertThat(includeNames)
        .containsExactlyElementsIn(Arrays.asList(names)).inOrder();
  }
}
