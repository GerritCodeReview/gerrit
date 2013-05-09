// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.google.inject.Provider;

import org.easymock.IExpectationSetters;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ListBranchesTest extends LocalDiskRepositoryTestCase {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private ObjectId idA;
  private Project.NameKey name;
  private Repository realDb;
  private Repository mockDb;
  private ProjectControl.Factory pcf;
  private ProjectControl pc;
  private GitRepositoryManager grm;
  private List<RefControl> refMocks;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    idA = ObjectId.fromString("df84c2f4f7ce7e0b25cdeac84b8870bcff319885");
    name = new Project.NameKey("test");
    realDb = createBareRepository();

    mockDb = createStrictMock(Repository.class);
    pc = createStrictMock(ProjectControl.class);
    expect(pc.getProject()).andReturn(new Project(name)).anyTimes();
    pcf = createStrictMock(ProjectControl.Factory.class);
    grm = createStrictMock(GitRepositoryManager.class);
    refMocks = new ArrayList<RefControl>();
  }

  private IExpectationSetters<ProjectControl> validate()
      throws NoSuchProjectException {
    return expect(pcf.validateFor(eq(name), //
        eq(ProjectControl.OWNER | ProjectControl.VISIBLE)));
  }

  private void doReplay() {
    replay(mockDb, pc, pcf, grm);
    replay(refMocks.toArray());
  }

  private void doVerify() {
    verify(mockDb, pc, pcf, grm);
    verify(refMocks.toArray());
  }

  private void set(String branch, ObjectId id) throws IOException {
    final RefUpdate u = realDb.updateRef(R_HEADS + branch);
    u.setForceUpdate(true);
    u.setNewObjectId(id);
    switch (u.update()) {
      case NEW:
      case FAST_FORWARD:
      case FORCED:
        break;
      default:
        fail("unexpected update failure " + branch + " " + u.getResult());
    }
  }

  @Test
  public void testProjectNotVisible() throws Exception {
    final NoSuchProjectException err = new NoSuchProjectException(name);
    validate().andThrow(err);
    doReplay();
    try {
      new ListBranches(pcf, createListBranchesProvider(grm), name).call();
      fail("did not throw when expected not authorized");
    } catch (NoSuchProjectException e2) {
      assertSame(err, e2);
    }
    doVerify();
  }


  private ListBranchesResult permitted(boolean getHead)
      throws NoSuchProjectException, IOException {
    Map<String, Ref> refs = realDb.getAllRefs();

    validate().andReturn(pc);

    expect(grm.openRepository(eq(name))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andDelegateTo(realDb);
    if (getHead) {
      expect(mockDb.getRef(HEAD)).andDelegateTo(realDb);
      if (!refs.containsKey(HEAD) && realDb.getRef(HEAD) != null) {
        refs.put(HEAD, realDb.getRef(HEAD));
      }
    }

    Set<String> targets = targets(refs);
    for (Ref ref : refs.values()) {
      assumeVisible(ref, true, targets);
    }

    mockDb.close();

    expect(pc.canAddRefs()).andReturn(true);

    expectLastCall();

    doReplay();
    final ListBranchesResult r =
        new ListBranches(pcf, createListBranchesProvider(grm), name).call();
    doVerify();
    assertNotNull(r);
    assertNotNull(r.getBranches());
    return r;
  }

  private Set<String> targets(Map<String, Ref> refs) {
    Set<String> targets = new HashSet<String>();
    for (Ref ref : refs.values()) {
      if (ref.isSymbolic()) {
        targets.add(ref.getLeaf().getName());
      }
    }
    return targets;
  }

  private void assumeVisible(Ref ref, boolean visible, Set<String> targets) {
    RefControl rc = createStrictMock(RefControl.class);
    refMocks.add(rc);
    expect(rc.isVisible()).andReturn(visible);
    if (visible && !ref.isSymbolic() && !targets.contains(ref.getName())) {
      expect(rc.canDelete()).andReturn(true);
    }

    if (ref.isSymbolic()) {
      expect(pc.controlForRef(ref.getTarget().getName())).andReturn(rc);
    } else {
      expect(pc.controlForRef(ref.getName())).andReturn(rc);
    }
  }

  @Test
  public void testEmptyProject() throws Exception {
    ListBranchesResult r = permitted(true);

    assertEquals(1, r.getBranches().size());

    Branch b = r.getBranches().get(0);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());
  }

  @Test
  public void testMasterBranch() throws Exception {
    set("master", idA);

    ListBranchesResult r = permitted(false);
    assertEquals(2, r.getBranches().size());

    Branch b = r.getBranches().get(0);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());

    b = r.getBranches().get(1);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(R_HEADS + "master", b.getNameKey().get());

    assertEquals(R_HEADS + "master", b.getName());
    assertEquals("master", b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals(idA.name(), b.getRevision().get());
  }

  @Test
  public void testBranchNotHead() throws Exception {
    set("foo", idA);

    ListBranchesResult r = permitted(true);
    assertEquals(2, r.getBranches().size());

    Branch b = r.getBranches().get(0);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());
    assertFalse(b.getCanDelete());

    b = r.getBranches().get(1);
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(name, b.getNameKey().getParentKey());
    assertEquals(R_HEADS + "foo", b.getNameKey().get());

    assertEquals(R_HEADS + "foo", b.getName());
    assertEquals("foo", b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals(idA.name(), b.getRevision().get());
    assertTrue(b.getCanDelete());
  }

  @Test
  public void testSortByName() throws Exception {
    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put("foo", new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "foo", idA));
    u.put("bar", new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "bar", idA));
    u.put(HEAD, new SymbolicRef(HEAD, new ObjectIdRef.Unpeeled(LOOSE, R_HEADS
        + "master", null)));

    validate().andReturn(pc);
    expect(grm.openRepository(eq(name))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    for (Ref ref : u.values()) {
      assumeVisible(ref, true, targets(u));
    }
    expect(pc.canAddRefs()).andReturn(true);
    mockDb.close();
    expectLastCall();

    doReplay();
    final ListBranchesResult r =
        new ListBranches(pcf, createListBranchesProvider(grm), name).call();
    doVerify();
    assertNotNull(r);

    assertEquals(3, r.getBranches().size());
    assertEquals(HEAD, r.getBranches().get(0).getShortName());
    assertEquals("bar", r.getBranches().get(1).getShortName());
    assertEquals("foo", r.getBranches().get(2).getShortName());
  }

  @Test
  public void testHeadNotVisible() throws Exception {
    ObjectIdRef.Unpeeled bar =
        new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "bar", idA);
    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put(bar.getName(), bar);
    u.put(HEAD, new SymbolicRef(HEAD, bar));

    validate().andReturn(pc);
    expect(grm.openRepository(eq(name))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    assumeVisible(bar, false, targets(u));
    assumeVisible(bar, false, targets(u));
    expect(pc.canAddRefs()).andReturn(true);
    mockDb.close();
    expectLastCall();

    doReplay();
    final ListBranchesResult r =
        new ListBranches(pcf, createListBranchesProvider(grm), name).call();
    doVerify();
    assertNotNull(r);
    assertTrue(r.getBranches().isEmpty());
  }

  @Test
  public void testHeadVisibleButBranchHidden() throws Exception {
    ObjectIdRef.Unpeeled bar =
        new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "bar", idA);
    ObjectIdRef.Unpeeled foo =
        new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "foo", idA);

    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put(bar.getName(), bar);
    u.put(HEAD, new SymbolicRef(HEAD, bar));
    u.put(foo.getName(), foo);

    validate().andReturn(pc);
    expect(grm.openRepository(eq(name))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    assumeVisible(bar, true, targets(u));
    assumeVisible(bar, true, targets(u));
    assumeVisible(foo, false, targets(u));
    expect(pc.canAddRefs()).andReturn(true);
    mockDb.close();
    expectLastCall();

    doReplay();
    final ListBranchesResult r =
        new ListBranches(pcf, createListBranchesProvider(grm), name).call();
    doVerify();
    assertNotNull(r);

    assertEquals(2, r.getBranches().size());

    assertEquals(HEAD, r.getBranches().get(0).getShortName());
    assertFalse(r.getBranches().get(0).getCanDelete());

    assertEquals("bar", r.getBranches().get(1).getShortName());
    assertFalse(r.getBranches().get(1).getCanDelete());
  }

  private static Provider<com.google.gerrit.server.project.ListBranches> createListBranchesProvider(
      final GitRepositoryManager grm) {
    return new Provider<com.google.gerrit.server.project.ListBranches>() {
      @Override
      public com.google.gerrit.server.project.ListBranches get() {
        return new com.google.gerrit.server.project.ListBranches(grm);
      }
    };
  }
}
