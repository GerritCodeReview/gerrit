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

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ListGroupIncludesIT extends AbstractDaemonTest {

  @Test
  public void listNonExistingGroupIncludes_NotFound() throws Exception {
    assertThat(adminSession.get("/groups/non-existing/groups/").getStatusCode())
      .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void listEmptyGroupIncludes() throws Exception {
    assertThat(GET("/groups/Administrators/groups/")).isEmpty();
  }

  @Test
  public void listNonEmptyGroupIncludes() throws Exception {
    group("gx", "Administrators");
    group("gy", "Administrators");
    PUT("/groups/Administrators/groups/gx");
    PUT("/groups/Administrators/groups/gy");

    assertIncludes(GET("/groups/Administrators/groups/"), "gx", "gy");
  }

  @Test
  public void listOneIncludeMember() throws Exception {
    group("gx", "Administrators");
    group("gy", "Administrators");
    PUT("/groups/Administrators/groups/gx");
    PUT("/groups/Administrators/groups/gy");

    assertThat(GET_ONE("/groups/Administrators/groups/gx").name).isEqualTo("gx");
  }

  private List<GroupInfo> GET(String endpoint) throws IOException {
    RestResponse r = adminSession.get(endpoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    return newGson().fromJson(r.getReader(),
        new TypeToken<List<GroupInfo>>() {}.getType());
  }

  private GroupInfo GET_ONE(String endpoint) throws IOException {
    RestResponse r = adminSession.get(endpoint);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    return newGson().fromJson(r.getReader(), GroupInfo.class);
  }

  private void PUT(String endpoint) throws IOException {
    adminSession.put(endpoint).consume();
  }

  private void group(String name, String ownerGroup) throws IOException {
    CreateGroup.Input in = new CreateGroup.Input();
    in.ownerId = ownerGroup;
    adminSession.put("/groups/" + name, in).consume();
  }

  private void assertIncludes(List<GroupInfo> includes, String name,
      String... names) {
    Collection<String> includeNames = Collections2.transform(includes,
        new Function<GroupInfo, String>() {
          @Override
          public String apply(@Nullable GroupInfo info) {
            return info.name;
          }
        });
    assertThat((Iterable<?>)includeNames).contains(name);
    for (String n : names) {
      assertThat((Iterable<?>)includeNames).contains(n);
    }
    assertThat(includes).hasSize(names.length + 1);
  }
}
