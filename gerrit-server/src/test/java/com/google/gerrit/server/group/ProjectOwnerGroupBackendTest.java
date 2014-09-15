package com.google.gerrit.server.group;

import static java.util.Arrays.asList;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Predicate;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

//Copyright (C) 2014 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

public class ProjectOwnerGroupBackendTest {

  private static final AccountGroup.UUID UUID = new AccountGroup.UUID(
      "global:Project-Owners");
  private static final AccountGroup.UUID UNKNOWN_UUID = new AccountGroup.UUID(
      "unknown");

  private SystemGroupBackend classUnderTest;
  private IdentifiedUser user;
  private ProjectState ownProject;
  private ProjectState otherProject;

  @Before
  @SuppressWarnings("unchecked")
  public void setup() {
    classUnderTest = new SystemGroupBackend();
    user = createNiceMock(IdentifiedUser.class);
    ownProject = createMock(ProjectState.class);
    expect(ownProject.anyOwner(anyObject(Predicate.class)))
        .andStubReturn(true);
    otherProject = createMock(ProjectState.class);
    expect(otherProject.anyOwner(anyObject(Predicate.class))).andStubReturn(
        false);
    replay(user, ownProject, otherProject);
  }

  @Test
  public void testMembershipContainsFalseForUnknownProject() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.contains(UNKNOWN_UUID));
  }
  @Test

  public void testMembershipContainsFalseForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.contains(UUID));
  }

  @Test
  public void testMembershipContainsTrueForProjectOwner() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.contains(UUID));
  }

  @Test
  public void testMembershipAnyFalseForUnknownProject() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.containsAnyOf(asList(UNKNOWN_UUID)));
  }

  @Test
  public void testMembershipAnyFalseForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertFalse(checker.containsAnyOf(asList(UNKNOWN_UUID, UUID)));
  }

  @Test
  public void testMembershipAnyTrueForNonProjectOwner() throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.containsAnyOf(asList(UNKNOWN_UUID, UUID)));
  }

  @Test
  public void testMembershipIntersectionEmptyForUnknownProject()
      throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.intersection(asList(UNKNOWN_UUID)).isEmpty());
  }

  @Test
  public void testMembershipIntersectionEmptyForNonProjectOwner()
      throws Exception {
    ProjectControl control = createControl(otherProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    assertTrue(checker.intersection(asList(UNKNOWN_UUID, UUID)).isEmpty());
  }

  @Test
  public void testMembershipIntersectionNotEmptyForProjectOwner()
      throws Exception {
    ProjectControl control = createControl(ownProject);
    GroupMembership checker = classUnderTest.membershipsOf(control);
    Set<AccountGroup.UUID> intersection =
        checker.intersection(asList(UNKNOWN_UUID, UUID));
    assertEquals(1, intersection.size());
    assertEquals(UUID, intersection.iterator().next());
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
    assertEquals(UUID, intersection.iterator().next());
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
