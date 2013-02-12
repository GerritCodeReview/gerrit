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

package com.google.gerrit.acceptance;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gson.Gson;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.jcraft.jsch.JSchException;

/**
 * An example test that tests presence of system groups in a newly initialized
 * review site.
 *
 * The test shows how to perform these checks via SSH, REST or using Gerrit
 * internals.
 */
public class SystemGroupsIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private AccountCreator accounts;

  protected TestAccount admin;

  @Before
  public void setUp() throws Exception {
    admin = accounts.create("admin", "admin@sap.com", "Administrator",
            "Administrators");
  }

  @Test
  public void systemGroupsCreated_ssh() throws JSchException, IOException {
    SshSession session = new SshSession(admin);
    String result = session.exec("gerrit ls-groups");
    assertTrue(result.contains("Administrators"));
    assertTrue(result.contains("Anonymous Users"));
    assertTrue(result.contains("Non-Interactive Users"));
    assertTrue(result.contains("Project Owners"));
    assertTrue(result.contains("Registered Users"));
    session.close();
  }

  @SuppressWarnings("unused")
  private static class Group {
    String id;
    String name;
    String url;
    String description;
    Integer group_id;
    String owner_id;
  };

  @Test
  public void systemGroupsCreated_rest() throws IOException {
    RestSession session = new RestSession(admin);
    Reader r = session.get("/groups/");
    Gson gson = new Gson();
    @SuppressWarnings("serial")
    Map<String, Group> result =
        gson.fromJson(r, new TypeToken<Map<String, Group>>() {}.getType());
    Set<String> names = result.keySet();
    assertTrue(names.contains("Administrators"));
    assertTrue(names.contains("Anonymous Users"));
    assertTrue(names.contains("Non-Interactive Users"));
    assertTrue(names.contains("Project Owners"));
    assertTrue(names.contains("Registered Users"));
  }

  @Test
  public void systemGroupsCreated_internals() throws OrmException {
    ReviewDb db = reviewDbProvider.open();
    try {
      Set<String> names = Sets.newHashSet();
      for (AccountGroup g : db.accountGroups().all()) {
        names.add(g.getName());
      }
      assertTrue(names.contains("Administrators"));
      assertTrue(names.contains("Anonymous Users"));
      assertTrue(names.contains("Non-Interactive Users"));
      assertTrue(names.contains("Project Owners"));
      assertTrue(names.contains("Registered Users"));
    } finally {
      db.close();
    }
  }
}
