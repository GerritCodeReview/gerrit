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

package com.google.gerrit.server.group;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class ProjectOwnerGroupBackendTest {

  private static final AccountGroup.UUID PROJECT_OWNERS_GROUP =
      new AccountGroup.UUID("global:Project-Owners");
  private static final AccountGroup.UUID OTHER_GROUP = new AccountGroup.UUID(
      "unknown");
  private static final AccountGroup.UUID OWNING_GROUP = new AccountGroup.UUID(
      "other");;

  private SystemGroupBackend classUnderTest;
  private IdentifiedUser user;
  private ProjectState ownProject;
  private ProjectState otherProject;

  @Before
  public void setup() {
    classUnderTest = new SystemGroupBackend();
    user = createNiceMock(IdentifiedUser.class);
    expect(user.getEffectiveGroups()).andStubReturn(
        new ListGroupMembership(asList(OWNING_GROUP)));
    replay(user);

    ownProject = createProject(OWNING_GROUP);
    otherProject = createProject(OTHER_GROUP);
  }

  private ProjectState createProject(UUID owningGroup) {
    ProjectState project = createMock(ProjectState.class);
    expect(project.getAllOwners()).andStubReturn(singleton(owningGroup));
    replay(project);

    return  project;
  }

  @Test
  public void testMembershipContainsFalseForUnknownProject() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.contains(OTHER_GROUP));
  }

  @Test
  public void testMembershipContainsFalseForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.contains(PROJECT_OWNERS_GROUP));
  }

  @Test
  public void testMembershipContainsTrueForProjectOwner() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.contains(PROJECT_OWNERS_GROUP));
  }

  @Test
  public void testMembershipAnyFalseForUnknownProject() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.containsAnyOf(asList(OTHER_GROUP)));
  }

  @Test
  public void testMembershipAnyFalseForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.containsAnyOf(asList(OTHER_GROUP,
        PROJECT_OWNERS_GROUP)));
  }

  @Test
  public void testMembershipAnyTrueForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker
        .containsAnyOf(asList(OTHER_GROUP, PROJECT_OWNERS_GROUP)));
  }

  @Test
  public void testMembershipIntersectionEmptyForUnknownProject()
      throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.intersection(asList(OTHER_GROUP)).isEmpty());
  }

  @Test
  public void testMembershipIntersectionEmptyForNonProjectOwner()
      throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker
        .intersection(asList(OTHER_GROUP, PROJECT_OWNERS_GROUP)).isEmpty());
  }

  @Test
  public void testMembershipIntersectionNotEmptyForProjectOwner()
      throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    Set<AccountGroup.UUID> intersection =
        checker.intersection(asList(OTHER_GROUP, PROJECT_OWNERS_GROUP));
    assertEquals(1, intersection.size());
    assertEquals(PROJECT_OWNERS_GROUP, intersection.iterator().next());
  }

  @Test
  public void testKnownGroupsEmptyForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.getKnownGroups().isEmpty());
  }

  @Test
  public void testKnownGroupsNonEMptyForProjectOwner() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    Set<AccountGroup.UUID> intersection = checker.getKnownGroups();
    assertEquals(1, intersection.size());
    assertEquals(PROJECT_OWNERS_GROUP, intersection.iterator().next());
  }

  private ProjectControl createControl(ProjectState state) {
    ProjectControl control = createMock(ProjectControl.class);
    expect(control.getCurrentUser()).andStubReturn(user);
    expect(control.getProjectState()).andStubReturn(state);
    replay(control);
    return control;
  }

  @After
  public void verifyMocks() {
    verify(user, otherProject, ownProject);
  }

}
