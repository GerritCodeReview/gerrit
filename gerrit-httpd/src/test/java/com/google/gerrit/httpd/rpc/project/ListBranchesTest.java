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

import com.google.gerrit.common.data.ListBranchesResult;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.easymock.IExpectationSetters;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListBranchesTest extends LocalDiskRepositoryTestCase {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private ObjectId idA;
  private Project.NameKey name;
  private Project.Id id;
  private Repository realDb;
  private Repository mockDb;
  private ProjectControl.Factory pcf;
  private ProjectControl pc;
  private GitRepositoryManager grm;
  private List<RefControl> refMocks;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    idA = ObjectId.fromString("df84c2f4f7ce7e0b25cdeac84b8870bcff319885");
    name = new Project.NameKey("test");
    id = new Project.Id(42);
    realDb = createBareRepository();

    mockDb = createStrictMock(Repository.class);
    pc = createStrictMock(ProjectControl.class);
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

  public void testProjectNotVisible() throws Exception {
    final NoSuchProjectException err = new NoSuchProjectException(name);
    validate().andThrow(err);
    doReplay();
    try {
      new ListBranches(pcf, grm, name).call();
      fail("did not throw when expected not authorized");
    } catch (NoSuchProjectException e2) {
      assertSame(err, e2);
    }
    doVerify();
  }


  private ListBranchesResult permitted(boolean getHead)
      throws NoSuchProjectException, IOException {
    Map<String, Ref> refs = realDb.getAllRefs();

    expect(pc.getProject()).andReturn(new Project(name, id));
    validate().andReturn(pc);

    expect(grm.openRepository(eq(name.get()))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andDelegateTo(realDb);
    if (getHead) {
      expect(mockDb.getRef(HEAD)).andDelegateTo(realDb);
      if (!refs.containsKey(HEAD) && realDb.getRef(HEAD) != null) {
        refs.put(HEAD, realDb.getRef(HEAD));
      }
    }

    for (Ref ref : refs.values()) {
      assumeVisible(ref, true);
    }

    grm.closeRepository(mockDb);

    expect(pc.canAddRefs()).andReturn(true);

    expectLastCall();

    doReplay();
    final ListBranchesResult r = new ListBranches(pcf, grm, name).call();
    doVerify();
    assertNotNull(r);
    assertNotNull(r.getBranches());
    return r;
  }

  private void assumeVisible(Ref ref, boolean visible) {
    RefControl rc = createStrictMock(RefControl.class);
    refMocks.add(rc);
    expect(rc.isVisible()).andReturn(visible);
    if (visible) {
      expect(rc.canDelete()).andReturn(true);
    }

    if (ref.isSymbolic()) {
      expect(pc.controlForRef(ref.getTarget().getName())).andReturn(rc);
      expect(pc.getProject()).andReturn(new Project(name, id));
    } else {
      expect(pc.controlForRef(ref.getName())).andReturn(rc);
      if (ref.getName().startsWith(Constants.R_HEADS) && visible) {
        expect(pc.getProject()).andReturn(new Project(name, id));
      }
    }
  }

  public void testEmptyProject() throws Exception {
    ListBranchesResult r = permitted(true);

    assertEquals(1, r.getBranches().size());

    Branch b = r.getBranches().get(0).getBranch();
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(id, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());
  }

  public void testMasterBranch() throws Exception {
    set("master", idA);

    ListBranchesResult r = permitted(false);
    assertEquals(2, r.getBranches().size());

    Branch b = r.getBranches().get(0).getBranch();
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(id, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());

    b = r.getBranches().get(1).getBranch();
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(id, b.getNameKey().getParentKey());
    assertEquals(R_HEADS + "master", b.getNameKey().get());

    assertEquals(R_HEADS + "master", b.getName());
    assertEquals("master", b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals(idA.name(), b.getRevision().get());
  }

  public void testBranchNotHead() throws Exception {
    set("foo", idA);

    ListBranchesResult r = permitted(true);
    assertEquals(2, r.getBranches().size());

    Branch b = r.getBranches().get(0).getBranch();
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(id, b.getNameKey().getParentKey());
    assertEquals(HEAD, b.getNameKey().get());

    assertEquals(HEAD, b.getName());
    assertEquals(HEAD, b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals("master", b.getRevision().get());

    b = r.getBranches().get(1).getBranch();
    assertNotNull(b);

    assertNotNull(b.getNameKey());
    assertSame(id, b.getNameKey().getParentKey());
    assertEquals(R_HEADS + "foo", b.getNameKey().get());

    assertEquals(R_HEADS + "foo", b.getName());
    assertEquals("foo", b.getShortName());

    assertNotNull(b.getRevision());
    assertEquals(idA.name(), b.getRevision().get());
  }

  public void testSortByName() throws Exception {
    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put("foo", new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "foo", idA));
    u.put("bar", new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "bar", idA));
    u.put(HEAD, new SymbolicRef(HEAD, new ObjectIdRef.Unpeeled(LOOSE, R_HEADS
        + "master", null)));

    expect(pc.getProject()).andReturn(new Project(name, id));
    validate().andReturn(pc);
    expect(grm.openRepository(eq(name.get()))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    for (Ref ref : u.values()) {
      assumeVisible(ref, true);
    }
    expect(pc.canAddRefs()).andReturn(true);
    grm.closeRepository(mockDb);
    expectLastCall();

    doReplay();
    final ListBranchesResult r = new ListBranches(pcf, grm, name).call();
    doVerify();
    assertNotNull(r);

    assertEquals(3, r.getBranches().size());
    assertEquals(HEAD, r.getBranches().get(0).getBranch().getShortName());
    assertEquals("bar", r.getBranches().get(1).getBranch().getShortName());
    assertEquals("foo", r.getBranches().get(2).getBranch().getShortName());
  }

  public void testHeadNotVisible() throws Exception {
    ObjectIdRef.Unpeeled bar =
        new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "bar", idA);
    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put(bar.getName(), bar);
    u.put(HEAD, new SymbolicRef(HEAD, bar));

    expect(pc.getProject()).andReturn(new Project(name, id));
    validate().andReturn(pc);
    expect(grm.openRepository(eq(name.get()))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    assumeVisible(bar, false);
    assumeVisible(bar, false);
    expect(pc.canAddRefs()).andReturn(true);
    grm.closeRepository(mockDb);
    expectLastCall();

    doReplay();
    final ListBranchesResult r = new ListBranches(pcf, grm, name).call();
    doVerify();
    assertNotNull(r);
    assertTrue(r.getBranches().isEmpty());
  }

  public void testHeadVisibleButBranchHidden() throws Exception {
    ObjectIdRef.Unpeeled bar =
        new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "bar", idA);
    ObjectIdRef.Unpeeled foo =
        new ObjectIdRef.Unpeeled(LOOSE, R_HEADS + "foo", idA);

    Map<String, Ref> u = new LinkedHashMap<String, Ref>();
    u.put(bar.getName(), bar);
    u.put(HEAD, new SymbolicRef(HEAD, bar));
    u.put(foo.getName(), foo);

    expect(pc.getProject()).andReturn(new Project(name, id));
    validate().andReturn(pc);
    expect(grm.openRepository(eq(name.get()))).andReturn(mockDb);
    expect(mockDb.getAllRefs()).andReturn(u);
    assumeVisible(bar, true);
    assumeVisible(bar, true);
    assumeVisible(foo, false);
    expect(pc.canAddRefs()).andReturn(true);
    grm.closeRepository(mockDb);
    expectLastCall();

    doReplay();
    final ListBranchesResult r = new ListBranches(pcf, grm, name).call();
    doVerify();
    assertNotNull(r);

    assertEquals(2, r.getBranches().size());
    assertEquals(HEAD, r.getBranches().get(0).getBranch().getShortName());
    assertEquals("bar", r.getBranches().get(1).getBranch().getShortName());
  }
}
