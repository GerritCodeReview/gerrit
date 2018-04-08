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

package com.google.gerrit.server.project;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.git.ValidationError;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class GroupListTest {
  private static final Project.NameKey PROJECT = new Project.NameKey("project");
  private static final String TEXT =
      "# UUID                                  \tGroup Name\n"
          + "#\n"
          + "d96b998f8a66ff433af50befb975d0e2bb6e0999\tNon-Interactive Users\n"
          + "ebe31c01aec2c9ac3b3c03e87a47450829ff4310\tAdministrators\n";

  private GroupList groupList;

  @Before
  public void setup() throws IOException {
    ValidationError.Sink sink = createNiceMock(ValidationError.Sink.class);
    replay(sink);
    groupList = GroupList.parse(PROJECT, TEXT, sink);
  }

  @Test
  public void byUUID() throws Exception {
    AccountGroup.UUID uuid = new AccountGroup.UUID("d96b998f8a66ff433af50befb975d0e2bb6e0999");

    GroupReference groupReference = groupList.byUUID(uuid);

    assertEquals(uuid, groupReference.getUUID());
    assertEquals("Non-Interactive Users", groupReference.getName());
  }

  @Test
  public void put() {
    AccountGroup.UUID uuid = new AccountGroup.UUID("abc");
    GroupReference groupReference = new GroupReference(uuid, "Hutzliputz");

    groupList.put(uuid, groupReference);

    assertEquals(3, groupList.references().size());
    GroupReference found = groupList.byUUID(uuid);
    assertEquals(groupReference, found);
  }

  @Test
  public void references() throws Exception {
    Collection<GroupReference> result = groupList.references();

    assertEquals(2, result.size());
    AccountGroup.UUID uuid = new AccountGroup.UUID("ebe31c01aec2c9ac3b3c03e87a47450829ff4310");
    GroupReference expected = new GroupReference(uuid, "Administrators");

    assertTrue(result.contains(expected));
  }

  @Test
  public void uUIDs() throws Exception {
    Set<AccountGroup.UUID> result = groupList.uuids();

    assertEquals(2, result.size());
    AccountGroup.UUID expected = new AccountGroup.UUID("ebe31c01aec2c9ac3b3c03e87a47450829ff4310");
    assertTrue(result.contains(expected));
  }

  @Test
  public void validationError() throws Exception {
    ValidationError.Sink sink = createMock(ValidationError.Sink.class);
    sink.error(anyObject(ValidationError.class));
    expectLastCall().times(2);
    replay(sink);
    groupList = GroupList.parse(PROJECT, TEXT.replace("\t", "    "), sink);
    verify(sink);
  }

  @Test
  public void retainAll() throws Exception {
    AccountGroup.UUID uuid = new AccountGroup.UUID("d96b998f8a66ff433af50befb975d0e2bb6e0999");
    groupList.retainUUIDs(Collections.singleton(uuid));

    assertNotNull(groupList.byUUID(uuid));
    assertNull(groupList.byUUID(new AccountGroup.UUID("ebe31c01aec2c9ac3b3c03e87a47450829ff4310")));
  }

  @Test
  public void asText() throws Exception {
    assertTrue(TEXT.equals(groupList.asText()));
  }
}
