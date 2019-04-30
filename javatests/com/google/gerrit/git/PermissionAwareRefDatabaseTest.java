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

package com.google.gerrit.git;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.same;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.git.PermissionAwareRefDatabase;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class PermissionAwareRefDatabaseTest extends PermissionAwareDelegateTestBase<RefDatabase> {
  private Repository repository;
  private RefDatabase delegateRefDb;
  private PermissionBackend.ForProject forProject;

  private PermissionAwareRefDatabase refDatabase;

  @Override
  protected Set<String> methodsWithSpecificTest() {
    return ImmutableSet.of(
        "newUpdate",
        "newRename",
        "toString",
        "exactRef",
        "firstExactRef",
        "getRefs",
        "getRefsByPrefix",
        "getAdditionalRefs");
  }

  @Override
  protected Object[] additionalMockObjectsForDelegateCheck() {
    return new Object[] {repository, forProject};
  }

  @Override
  protected void setExpectationsForAdditionalMockObjects() {
    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
  }

  @Override
  protected RefDatabase delegate() {
    return delegateRefDb;
  }

  @Before
  public void setUp() {
    repository = createMock(Repository.class);
    delegateRefDb = createMock(RefDatabase.class);
    forProject = createMock(PermissionBackend.ForProject.class);

    replay(repository, delegateRefDb, forProject);

    refDatabase = new PermissionAwareRefDatabase(repository, forProject);

    reset(repository, delegateRefDb, forProject);
  }

  @Test
  public void shouldDelegateAllPublicMethods() {
    shouldDelegateAllPublicMethods(() -> refDatabase);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnExactRefFromDelegateIfAuthorised() throws IOException, Exception {
    String refName = "name";
    Ref expected = aRef(refName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(refName)).andReturn(expected);
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(refName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.exactRef(refName)).isSameInstanceAs(expected);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnNoExactRefFromDelegateIfNotAuthorised() throws IOException, Exception {
    String refName = "name";
    Ref expected = aRef(refName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(refName)).andReturn(expected);
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of());

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.exactRef(refName)).isNull();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnExactRefsFromDelegateIfAuthorised() throws IOException, Exception {
    String refName1 = "name1";
    String refName2 = "name2";

    Ref expected1 = aRef(refName1);
    Ref expected2 = aRef(refName2);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(refName1, refName2))
        .andReturn(ImmutableMap.of(refName1, expected1, refName2, expected2));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(refName1, expected1, refName2, expected2));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.exactRef(refName1, refName2))
        .isEqualTo(ImmutableMap.of(refName1, expected1, refName2, expected2));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotReturnExactRefsFromDelegateIfNotAuthorised() throws IOException, Exception {
    String refName1 = "name1";
    String expectedRefName = "name2";

    Ref filtered = aRef(refName1);
    Ref expected = aRef(expectedRefName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(refName1, expectedRefName))
        .andReturn(ImmutableMap.of(refName1, filtered, expectedRefName, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedRefName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.exactRef(refName1, expectedRefName))
        .isEqualTo(ImmutableMap.of(expectedRefName, expected));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnFirstNotFilteredExactRefsFromDelegate() throws IOException, Exception {
    String refName1 = "name1";
    String refName2 = "name2";

    Ref filtered = aRef(refName2);
    Ref expected = aRef(refName2);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(refName1)).andReturn(filtered);
    expect(delegateRefDb.exactRef(refName2)).andReturn(expected);
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(refName2, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.firstExactRef(refName1, refName2)).isEqualTo(expected);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnFirstExactRefsFromDelegateIfAuthorised() throws IOException, Exception {
    String refName1 = "name1";
    String refName2 = "name2";

    Ref filtered = aRef(refName1);
    Ref expected = aRef(refName2);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(refName1)).andReturn(null);
    expect(delegateRefDb.exactRef(refName2)).andReturn(expected);
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(refName2, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.firstExactRef(refName1, refName2)).isEqualTo(expected);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnRefsFromDelegateIfAuthorised() throws IOException, Exception {
    String refName1 = "name1";
    String refName2 = "name2";

    Ref expected1 = aRef(refName1);
    Ref expected2 = aRef(refName2);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.getRefs()).andReturn(ImmutableList.of(expected1, expected2));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(refName1, expected1, refName2, expected2));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.getRefs()).isEqualTo(ImmutableList.of(expected1, expected2));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedRefsFromDelegate() throws IOException, Exception {
    String filteredName = "name1";
    String expectedName = "name2";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.getRefs()).andReturn(ImmutableList.of(filtered, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.getRefs()).isEqualTo(ImmutableList.of(expected));
  }

  @SuppressWarnings({"unchecked", "deprecation"})
  @Test
  public void shouldReturnOnlyAuthorisedRefsGivenAPrefixFromDelegate()
      throws IOException, Exception {
    String filteredName = "name1";
    String expectedName = "name2";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.getRefs("name"))
        .andReturn(ImmutableMap.of(filteredName, filtered, expectedName, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.getRefs("name")).isEqualTo(ImmutableMap.of(expectedName, expected));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedRefsByPrefixFromDelegate() throws IOException, Exception {
    String filteredName = "name1";
    String expectedName = "name2";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.getRefsByPrefix("name")).andReturn(ImmutableList.of(filtered, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.getRefsByPrefix("name")).isEqualTo(ImmutableList.of(expected));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedRefsByPrefixesFromDelegate() throws IOException, Exception {
    String filteredName = "name1";
    String expectedName = "name2";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.getRefsByPrefix("name", "otherPrefix"))
        .andReturn(ImmutableList.of(filtered, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.getRefsByPrefix("name", "otherPrefix"))
        .isEqualTo(ImmutableList.of(expected));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedAdditionalRefsFromDelegate() throws IOException, Exception {
    String filteredName = "name1";
    String expectedName = "name2";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.getAdditionalRefs()).andReturn(ImmutableList.of(filtered, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.getAdditionalRefs()).isEqualTo(ImmutableList.of(expected));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateANewUpdateIfRefIsAccessible() throws Exception {
    final String name = "testRefName";

    final RefUpdate aRefUpdate = createMock(RefUpdate.class);
    final Ref aRef = aRef(name);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb);

    expect(delegateRefDb.getRef(name)).andReturn(aRef);

    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(name, aRef));

    expect(delegateRefDb.newUpdate(name, true)).andReturn(aRefUpdate);

    replay(aRefUpdate, delegateRefDb, repository, forProject);

    assertThat(refDatabase.newUpdate(name, true)).isSameInstanceAs(aRefUpdate);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateAFailOnlyNewUpdateIfRefIsNotAccessible() throws Exception {
    final String name = "testRefName";

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();

    expect(delegateRefDb.getRef(name)).andReturn(aRef(name));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of());

    replay(delegateRefDb, forProject, repository);

    RefUpdate refUpdate = refDatabase.newUpdate(name, true);

    assertThat(refUpdate.forceUpdate()).isEqualTo(Result.REJECTED_OTHER_REASON);
    assertThat(refUpdate.update()).isEqualTo(Result.REJECTED_OTHER_REASON);
    assertThat(refUpdate.delete()).isEqualTo(Result.REJECTED_OTHER_REASON);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateANewRenameIfRefIsAccessible() throws Exception {
    final String fromName = "fromName";
    final String toName = "toName";
    final RefRename aRefRename = createMock(RefRename.class);
    final Ref fromRef = new SimpleRef(fromName);
    final Ref toRef = new SimpleRef(toName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();

    expect(delegateRefDb.getRef(fromName)).andReturn(fromRef);
    expect(delegateRefDb.getRef(toName)).andReturn(toRef);

    // Bit of a hack to avoid to create a matcher on the parameter of the filter method
    // exploiting the fact that post filtering we collect the entry in the map with the
    // given ref name to handle the result of filtering returning symlinks resolutions
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(fromName, fromRef, toName, toRef))
        .times(2);

    expect(delegateRefDb.newRename(fromName, toName)).andReturn(aRefRename);

    replay(delegateRefDb, aRefRename, forProject, repository);

    assertThat(refDatabase.newRename(fromName, toName)).isSameInstanceAs(aRefRename);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldCreateAFailOnlyNewRenameIfRefIsNotAccessible() throws Exception {
    final String fromName = "fromName";
    final String toName = "toName";
    final Ref fromRef = new SimpleRef(fromName);
    final Ref toRef = new SimpleRef(toName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();

    expect(delegateRefDb.getRef(fromName)).andReturn(fromRef);
    expect(delegateRefDb.getRef(toName)).andReturn(toRef);

    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of())
        .times(2);

    replay(delegateRefDb, repository, forProject);

    RefRename refRename = refDatabase.newRename(fromName, toName);

    assertThat(refRename.rename()).isEqualTo(Result.REJECTED_OTHER_REASON);
  }
}
