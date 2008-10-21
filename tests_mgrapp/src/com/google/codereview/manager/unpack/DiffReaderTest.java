// Copyright 2008 Google Inc.
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

package com.google.codereview.manager.unpack;

import com.google.codereview.TrashTestCase;
import com.google.codereview.internal.UploadPatchsetFile.UploadPatchsetFileRequest.StatusType;

import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.Tree;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;

public class DiffReaderTest extends TrashTestCase {
  private Repository db;
  private ObjectWriter writer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    db = new Repository(new File(tempRoot, ".git"));
    db.create();
    writer = new ObjectWriter(db);
  }

  public void testAddOneTextFile_RootCommit() throws Exception {
    final Tree top = new Tree(db);
    final ObjectId blobId = blob("a\nb\n");
    top.addFile("foo").setId(blobId);
    final RevCommit c = commit(top);
    final DiffReader dr = new DiffReader(db, c);

    final FileDiff foo = dr.next();
    assertNotNull(foo);
    assertEquals("foo", foo.getFilename());
    assertSame(StatusType.ADD, foo.getStatus());
    assertEquals(ObjectId.zeroId(), foo.getBaseId());
    assertEquals("diff --git a/foo b/foo\n" + "new file mode 100644\n"
        + "index " + ObjectId.zeroId().name() + ".." + blobId.name() + "\n"
        + "--- /dev/null\n" + "+++ b/foo\n" + "@@ -0,0 +1,2 @@\n" + "+a\n"
        + "+b\n", foo.getPatch());

    assertNull(dr.next());
    dr.close();
  }

  public void testAddOneTextFile_ExistingTree() throws Exception {
    final Tree top = new Tree(db);
    final RevCommit parent = commit(top);
    final ObjectId blobId = blob("a\nb\n");
    top.addFile("foo").setId(blobId);
    final RevCommit c = commit(top, parent);
    final DiffReader dr = new DiffReader(db, c);

    final FileDiff foo = dr.next();
    assertNotNull(foo);
    assertEquals("foo", foo.getFilename());
    assertSame(StatusType.ADD, foo.getStatus());
    assertEquals(ObjectId.zeroId(), foo.getBaseId());
    assertEquals("diff --git a/foo b/foo\n" + "new file mode 100644\n"
        + "index " + ObjectId.zeroId().name() + ".." + blobId.name() + "\n"
        + "--- /dev/null\n" + "+++ b/foo\n" + "@@ -0,0 +1,2 @@\n" + "+a\n"
        + "+b\n", foo.getPatch());

    assertNull(dr.next());
    dr.close();
  }

  public void testDeleteOneTextFile() throws Exception {
    final Tree top = new Tree(db);
    final ObjectId blobId = blob("a\nb\n");
    top.addFile("foo").setId(blobId);
    final RevCommit parent = commit(top);
    top.findBlobMember("foo").delete();
    final RevCommit c = commit(top, parent);
    final DiffReader dr = new DiffReader(db, c);

    final FileDiff foo = dr.next();
    assertNotNull(foo);
    assertEquals("foo", foo.getFilename());
    assertSame(StatusType.DELETE, foo.getStatus());
    assertEquals(blobId, foo.getBaseId());
    assertEquals("diff --git a/foo b/foo\n" + "deleted file mode 100644\n"
        + "index " + blobId.name() + ".." + ObjectId.zeroId().name() + "\n"
        + "--- a/foo\n" + "+++ /dev/null\n" + "@@ -1,2 +0,0 @@\n" + "-a\n"
        + "-b\n", foo.getPatch());

    assertNull(dr.next());
    dr.close();
  }

  public void testModifyTwoTextFiles() throws Exception {
    final Tree top = new Tree(db);
    final ObjectId barBaseId = blob("a\nc\n");
    top.addFile("bar").setId(barBaseId);
    final ObjectId fooBaseId = blob("a\nb\n");
    top.addFile("foo").setId(fooBaseId);

    final RevCommit parent = commit(top);
    final ObjectId barNewId = blob("a\nd\nc\n");
    top.findBlobMember("bar").setId(barNewId);
    final ObjectId fooNewId = blob("a\nc\nb\n");
    top.findBlobMember("foo").setId(fooNewId);

    final RevCommit c = commit(top, parent);
    final DiffReader dr = new DiffReader(db, c);

    final FileDiff bar = dr.next();
    assertNotNull(bar);
    assertEquals("bar", bar.getFilename());
    assertSame(StatusType.MODIFY, bar.getStatus());
    assertEquals(barBaseId, bar.getBaseId());
    assertEquals("diff --git a/bar b/bar\n" + "index " + barBaseId.name()
        + ".." + barNewId.name() + " 100644\n" + "--- a/bar\n" + "+++ b/bar\n"
        + "@@ -1,2 +1,3 @@\n" + " a\n" + "+d\n" + " c\n", bar.getPatch());

    final FileDiff foo = dr.next();
    assertNotNull(foo);
    assertEquals("foo", foo.getFilename());
    assertSame(StatusType.MODIFY, foo.getStatus());
    assertEquals(fooBaseId, foo.getBaseId());
    assertEquals("diff --git a/foo b/foo\n" + "index " + fooBaseId.name()
        + ".." + fooNewId.name() + " 100644\n" + "--- a/foo\n" + "+++ b/foo\n"
        + "@@ -1,2 +1,3 @@\n" + " a\n" + "+c\n" + " b\n", foo.getPatch());

    assertNull(dr.next());
    dr.close();
  }

  public void testAddBinaryFile() throws Exception {
    final Tree top = new Tree(db);
    final ObjectId blobId = blob("\0\1\2\0\1\2\0\1\2");
    top.addFile("foo").setId(blobId);
    final RevCommit c = commit(top);
    final DiffReader dr = new DiffReader(db, c);

    final FileDiff foo = dr.next();
    assertNotNull(foo);
    assertEquals("foo", foo.getFilename());
    assertSame(StatusType.ADD, foo.getStatus());
    assertEquals(ObjectId.zeroId(), foo.getBaseId());
    assertEquals("diff --git a/foo b/foo\n" + "new file mode 100644\n"
        + "index " + ObjectId.zeroId().name() + ".." + blobId.name() + "\n"
        + "Binary files /dev/null and b/foo differ\n", foo.getPatch());

    assertNull(dr.next());
    dr.close();
  }

  private ObjectId blob(final String content) throws IOException {
    return writer.writeBlob(content.getBytes("UTF-8"));
  }

  private RevCommit commit(final Tree top, final RevCommit... parents)
      throws IOException {
    final Commit c = new Commit(db);
    c.setTreeId(writer.writeTree(top));
    final ObjectId parentIds[] = new ObjectId[parents.length];
    for (int i = 0; i < parents.length; i++) {
      parentIds[i] = parents[i].getId();
    }
    c.setParentIds(parentIds);
    c.setAuthor(new PersonIdent("A U Thor <a@example.com> 1 +0000"));
    c.setCommitter(c.getAuthor());
    c.setMessage("");
    c.setCommitId(writer.writeCommit(c));
    final RevWalk rw = new RevWalk(db);
    final RevCommit r = rw.parseCommit(c.getCommitId());
    for (final RevCommit p : r.getParents()) {
      rw.parse(p);
    }
    return r;
  }
}
