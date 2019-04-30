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
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.verify;

import java.io.IOException;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.server.git.PermissionAwareRefDatabase;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;

public class PermissionAwareRefDatabaseTest {
	private Repository repository;
	private PermissionBackend.ForProject forProject;
	private PermissionAwareRefDatabase refDatabase;

	private RefDatabase delegateRefDb;
	
	
  @Before
  public void setUp() {
    repository = createMock(Repository.class);
    delegateRefDb = createMock(RefDatabase.class);
    forProject = createMock(PermissionBackend.ForProject.class);

    replay(repository, delegateRefDb, forProject);

    refDatabase = new PermissionAwareRefDatabase(repository, forProject);

    reset(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldNotReturnExactRefsFromDelegateIfNotAuthorised() throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(filteredName, expectedName))
        .andReturn(ImmutableMap.of(filteredName, filtered, expectedName, expected));
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.exactRef(filteredName, expectedName))
        .isEqualTo(ImmutableMap.of(expectedName, expected));
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnFirstNotFilteredExactRefsFromDelegate() throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

    Ref filtered = aRef(filteredName);
    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(filteredName)).andReturn(filtered);
    expect(delegateRefDb.exactRef(expectedName)).andReturn(expected);
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
    		.andReturn(ImmutableMap.of())
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.firstExactRef(filteredName, expectedName)).isEqualTo(expected);
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnFirstExactRefsFromDelegateIfAuthorised() throws IOException, Exception {
	  String filteredName = "filteredName";
	    String expectedName = "expectedName";

	    Ref filtered = aRef(filteredName);
	    Ref expected = aRef(expectedName);

    expect(repository.getRefDatabase()).andReturn(delegateRefDb).anyTimes();
    expect(delegateRefDb.exactRef(filteredName)).andReturn(filtered);
    expect(delegateRefDb.exactRef(expectedName)).andReturn(expected);
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of())
        .andReturn(ImmutableMap.of(expectedName, expected));

    replay(repository, delegateRefDb, forProject);

    assertThat(refDatabase.firstExactRef(filteredName, expectedName)).isEqualTo(expected);
    
    verify(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedRefsFromDelegate() throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

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
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings({"unchecked", "deprecation"})
  @Test
  public void shouldReturnOnlyAuthorisedRefsGivenAPrefixFromDelegate()
      throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

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
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedRefsByPrefixFromDelegate() throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

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
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedRefsByPrefixesFromDelegate() throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

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
    
    verify(repository, delegateRefDb, forProject);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnOnlyAuthorisedAdditionalRefsFromDelegate() throws IOException, Exception {
    String filteredName = "filteredName";
    String expectedName = "expectedName";

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
    
    verify(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
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
    
    verify(repository, delegateRefDb, forProject);
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

    //the filter is called only once since the if passes at the first ref being filtered
    expect(
            forProject.filter(
                anyObject(Map.class), same(repository), anyObject(RefFilterOptions.class)))
        .andReturn(ImmutableMap.of());

    replay(delegateRefDb, repository, forProject);

    RefRename refRename = refDatabase.newRename(fromName, toName);

    assertThat(refRename.rename()).isEqualTo(Result.REJECTED_OTHER_REASON);
    
    verify(repository, delegateRefDb, forProject);
  }
  
  protected Ref aRef(String name) {
    return new SimpleRef(name);
  }
  
  public static class SimpleRef implements Ref {
	    private final String name;

	    SimpleRef(String name) {
	      this.name = name;
	    }

	    @Override
	    public String getName() {
	      return name;
	    }

	    @Override
	    public boolean isSymbolic() {
	      return false;
	    }

	    @Override
	    public Ref getLeaf() {
	      return null;
	    }

	    @Override
	    public Ref getTarget() {
	      return null;
	    }

	    @Override
	    public ObjectId getObjectId() {
	      return ObjectId.zeroId();
	    }

	    @Override
	    public ObjectId getPeeledObjectId() {
	      return ObjectId.zeroId();
	    }

	    @Override
	    public boolean isPeeled() {
	      return false;
	    }

	    @Override
	    public Storage getStorage() {
	      return Storage.NETWORK;
	    }

	    @Override
	    public String toString() {
	      return String.format("SimpleRef(%s)", name);
	    }
	  }
}
