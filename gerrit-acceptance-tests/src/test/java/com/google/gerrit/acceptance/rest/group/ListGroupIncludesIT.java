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
    String name = name("non-existing");
    assertThat(adminSession.get("/groups/" + name + "/groups/").getStatusCode())
      .isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void listEmptyGroupIncludes() throws Exception {
    String gx = group("gx", "Administrators");
    PUT("/groups/" + gx + "/groups/" + name("emptygroup"));
    assertThat(GET("/groups/Administrators/groups/")).isEmpty();
  }

  @Test
  public void listNonEmptyGroupIncludes() throws Exception {
    String gx = group("gx", "Administrators");
    String gy = group("gy", "Administrators");
    String gz = group("gz", "Administrators");
    PUT("/groups/" + gx + "/groups/" + gy);
    PUT("/groups/" + gx + "/groups/" + gz);

    assertIncludes(GET("/groups/" + gx + "/groups/"), gy, gz);
  }

  @Test
  public void listOneIncludeMember() throws Exception {
    String gx = group("gx", "Administrators");
    String gy = group("gy", "Administrators");
    PUT("/groups/" + gx + "/groups/" + gy);

    assertThat(GET_ONE("/groups/" + gx + "/groups/" + gy).name).isEqualTo(gy);
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

  private String group(String name, String ownerGroup) throws IOException {
    name = name(name);
    CreateGroup.Input in = new CreateGroup.Input();
    in.ownerId = ownerGroup;
    adminSession.put("/groups/" + name, in).consume();
    return name;
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
