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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

public class ListGroupIncludesIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  private RestSession session;

  @Before
  public void setUp() throws Exception {
    TestAccount admin = accounts.create("admin", "Administrators");
    session = new RestSession(admin);
  }

  @Test
  public void listNonExistingGroupIncludes_NotFound() throws Exception {
    assertEquals(HttpStatus.SC_NOT_FOUND,
      session.get("/groups/non-existing/groups/").getStatusCode());
  }

  @Test
  public void listEmptyGroupIncludes() throws Exception {
    assertTrue(GET("/groups/Administrators/groups/").isEmpty());
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

    assertEquals(GET_ONE("/groups/Administrators/groups/gx").name, "gx");
  }

  private List<GroupInfo> GET(String endpoint) throws IOException {
    RestResponse r = session.get(endpoint);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return (new Gson()).fromJson(r.getReader(),
        new TypeToken<List<GroupInfo>>() {}.getType());
  }

  private GroupInfo GET_ONE(String endpoint) throws IOException {
    RestResponse r = session.get(endpoint);
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    return (new Gson()).fromJson(r.getReader(),
        new TypeToken<GroupInfo>() {}.getType());
  }

  private void PUT(String endpoint) throws IOException {
    session.put(endpoint).consume();
  }

  private void group(String name, String ownerGroup) throws IOException {
    GroupInput in = new GroupInput();
    in.owner_id = ownerGroup;
    session.put("/groups/" + name, in).consume();
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
    assertTrue(includeNames.contains(name));
    for (String n : names) {
      assertTrue(includeNames.contains(n));
    }
    assertEquals(includes.size(), names.length + 1);
  }
}
