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

package com.google.gerrit.sshd.scproot.hooks;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

public class CommitMsgHookTest extends HookTestCase {
  private final String SOB1 = "Signed-off-by: J Author <ja@example.com>\n";
  private final String SOB2 = "Signed-off-by: J Committer <jc@example.com>\n";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Date when = author.getWhen();
    final TimeZone tz = author.getTimeZone();

    author = new PersonIdent("J. Author", "ja@example.com");
    author = new PersonIdent(author, when, tz);

    committer = new PersonIdent("J. Committer", "jc@example.com");
    committer = new PersonIdent(committer, when, tz);
  }

  public void testEmptyMessages() throws Exception {
    // Empty input must yield empty output so commit will abort.
    // Note we must consider different commit templates formats.
    //
    hookDoesNotModify("");
    hookDoesNotModify(" ");
    hookDoesNotModify("\n");
    hookDoesNotModify("\n\n");
    hookDoesNotModify("  \n  ");

    hookDoesNotModify("#");
    hookDoesNotModify("#\n");
    hookDoesNotModify("# on branch master\n# Untracked files:\n");
    hookDoesNotModify("\n# on branch master\n# Untracked files:\n");
    hookDoesNotModify("\n\n# on branch master\n# Untracked files:\n");

    hookDoesNotModify("\n# on branch master\ndiff --git a/src b/src\n"
        + "new file mode 100644\nindex 0000000..c78b7f0\n");
  }

  public void testChangeIdAlreadySet() throws Exception {
    // If a Change-Id is already present in the footer, the hook must
    // not modify the message but instead must leave the identity alone.
    //
    hookDoesNotModify("a\n" + //
        "\n" + //
        "Change-Id: Iaeac9b4149291060228ef0154db2985a31111335\n");
    hookDoesNotModify("fix: this thing\n" + //
        "\n" + //
        "Change-Id: I388bdaf52ed05b55e62a22d0a20d2c1ae0d33e7e\n");
    hookDoesNotModify("fix-a-widget: this thing\n" + //
        "\n" + //
        "Change-Id: Id3bc5359d768a6400450283e12bdfb6cd135ea4b\n");
    hookDoesNotModify("FIX: this thing\n" + //
        "\n" + //
        "Change-Id: I1b55098b5a2cce0b3f3da783dda50d5f79f873fa\n");
    hookDoesNotModify("Fix-A-Widget: this thing\n" + //
        "\n" + //
        "Change-Id: I4f4e2e1e8568ddc1509baecb8c1270a1fb4b6da7\n");
  }

  public void testTimeAltersId() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
        call("a\n"));

    tick();
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I3251906b99dda598a58a6346d8126237ee1ea800\n",//
        call("a\n"));

    tick();
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I69adf9208d828f41a3d7e41afbca63aff37c0c5c\n",//
        call("a\n"));
  }

  public void testFirstParentAltersId() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
        call("a\n"));

    setHEAD();
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I51e86482bde7f92028541aaf724d3a3f996e7ea2\n",//
        call("a\n"));
  }

  public void testDirCacheAltersId() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
        call("a\n"));

    final DirCacheBuilder builder = DirCache.lock(repository).builder();
    builder.add(file("A"));
    assertTrue(builder.commit());

    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: If56597ea9759f23b070677ea6f064c60c38da631\n",//
        call("a\n"));
  }

  public void testSingleLineMessages() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
        call("a\n"));

    assertEquals("fix: this thing\n" + //
        "\n" + //
        "Change-Id: I0f13d0e6c739ca3ae399a05a93792e80feb97f37\n",//
        call("fix: this thing\n"));
    assertEquals("fix-a-widget: this thing\n" + //
        "\n" + //
        "Change-Id: I1a1a0c751e4273d532e4046a501a612b9b8a775e\n",//
        call("fix-a-widget: this thing\n"));

    assertEquals("FIX: this thing\n" + //
        "\n" + //
        "Change-Id: If816d944c57d3893b60cf10c65931fead1290d97\n",//
        call("FIX: this thing\n"));
    assertEquals("Fix-A-Widget: this thing\n" + //
        "\n" + //
        "Change-Id: I3e18d00cbda2ba1f73aeb63ed8c7d57d7fd16c76\n",//
        call("Fix-A-Widget: this thing\n"));
  }

  public void testMultiLineMessagesWithoutFooter() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "b\n" + //
        "\n" + //
        "Change-Id: Id0b4f42d3d6fc1569595c9b97cb665e738486f5d\n",//
        call("a\n" + "\n" + "b\n"));

    assertEquals("a\n" + //
        "\n" + //
        "b\nc\nd\ne\n" + //
        "\n" + //
        "Change-Id: I7d237b20058a0f46cc3f5fabc4a0476877289d75\n",//
        call("a\n" + "\n" + "b\nc\nd\ne\n"));

    assertEquals("a\n" + //
        "\n" + //
        "b\nc\nd\ne\n" + //
        "\n" + //
        "f\ng\nh\n" + //
        "\n" + //
        "Change-Id: I382e662f47bf164d6878b7fe61637873ab7fa4e8\n",//
        call("a\n" + "\n" + "b\nc\nd\ne\n" + "\n" + "f\ng\nh\n"));
  }

  public void testSingleLineMessagesWithSignedOffBy() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n" + //
        SOB1,//
        call("a\n" + "\n" + SOB1));

    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n" + //
        SOB1 + //
        SOB2,//
        call("a\n" + "\n" + SOB1 + SOB2));
  }

  public void testMultiLineMessagesWithSignedOffBy() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "b\nc\nd\ne\n" + //
        "\n" + //
        "f\ng\nh\n" + //
        "\n" + //
        "Change-Id: I382e662f47bf164d6878b7fe61637873ab7fa4e8\n" + //
        SOB1,//
        call("a\n" + "\n" + "b\nc\nd\ne\n" + "\n" + "f\ng\nh\n" + "\n" + SOB1));

    assertEquals("a\n" + //
        "\n" + //
        "b\nc\nd\ne\n" + //
        "\n" + //
        "f\ng\nh\n" + //
        "\n" + //
        "Change-Id: I382e662f47bf164d6878b7fe61637873ab7fa4e8\n" + //
        SOB1 + //
        SOB2,//
        call("a\n" + //
            "\n" + //
            "b\nc\nd\ne\n" + //
            "\n" + //
            "f\ng\nh\n" + //
            "\n" + //
            SOB1 + //
            SOB2));

    assertEquals("a\n" + //
        "\n" + //
        "b: not a footer\nc\nd\ne\n" + //
        "\n" + //
        "f\ng\nh\n" + //
        "\n" + //
        "Change-Id: I8869aabd44b3017cd55d2d7e0d546a03e3931ee2\n" + //
        SOB1 + //
        SOB2,//
        call("a\n" + //
            "\n" + //
            "b: not a footer\nc\nd\ne\n" + //
            "\n" + //
            "f\ng\nh\n" + //
            "\n" + //
            SOB1 + //
            SOB2));
  }

  public void testNoteInMiddle() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "NOTE: This\n" + //
        "does not fix it.\n" + //
        "\n" + //
        "Change-Id: I988a127969a6ee5e58db546aab74fc46e66847f8\n", //
        call("a\n" + //
            "\n" + //
            "NOTE: This\n" + //
            "does not fix it.\n"));
  }

  public void testKernelStyleFooter() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I1bd787f9e7590a2ac82b02c404c955ffb21877c4\n" + //
        SOB1 + //
        "[ja: Fixed\n" + //
        "     the indentation]\n" + //
        SOB2, //
        call("a\n" + //
            "\n" + //
            SOB1 + //
            "[ja: Fixed\n" + //
            "     the indentation]\n" + //
            SOB2));
  }

  public void testChangeIdAfterBugOrIssue() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Bug: 42\n" + //
        "Change-Id: I8c0321227c4324e670b9ae8cf40eccc87af21b1b\n" + //
        SOB1,//
        call("a\n" + //
            "\n" + //
            "Bug: 42\n" + //
            SOB1));

    assertEquals("a\n" + //
        "\n" + //
        "Issue: 42\n" + //
        "Change-Id: Ie66e07d89ae5b114c0975b49cf326e90331dd822\n" + //
        SOB1,//
        call("a\n" + //
            "\n" + //
            "Issue: 42\n" + //
            SOB1));
  }

  public void testCommitDashV() throws Exception {
    assertEquals("a\n" + //
        "\n" + //
        "Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n" + //
        SOB1 + //
        SOB2, //
        call("a\n" + //
            "\n" + //
            SOB1 + //
            SOB2 + //
            "\n" + //
            "# on branch master\n" + //
            "diff --git a/src b/src\n" + //
            "new file mode 100644\n" + //
            "index 0000000..c78b7f0\n"));
  }

  private void hookDoesNotModify(final String in) throws Exception {
    assertEquals(in, call(in));
  }

  private String call(final String body) throws Exception {
    final File tmp = write(body);
    try {
      final File hook = getHook("commit-msg");
      assertEquals(0, runHook(repository, hook, tmp.getAbsolutePath()));
      return read(tmp);
    } finally {
      tmp.delete();
    }
  }

  private DirCacheEntry file(final String name) throws IOException {
    final DirCacheEntry e = new DirCacheEntry(name);
    e.setFileMode(FileMode.REGULAR_FILE);
    e.setObjectId(writer().writeBlob(Constants.encode(name)));
    return e;
  }

  private void setHEAD() throws Exception {
    final ObjectWriter ow = writer();
    final Commit commit = new Commit(repository);
    commit.setTreeId(DirCache.newInCore().writeTree(ow));
    commit.setAuthor(author);
    commit.setCommitter(committer);
    commit.setMessage("test\n");
    final ObjectId commitId = ow.writeCommit(commit);

    final RefUpdate ref = repository.updateRef(Constants.HEAD);
    ref.setNewObjectId(commitId);
    switch (ref.forceUpdate()) {
      case NEW:
      case FAST_FORWARD:
      case FORCED:
      case NO_CHANGE:
        break;
      default:
        fail(Constants.HEAD + " did not change: " + ref.getResult());
    }
  }

  private ObjectWriter writer() {
    return new ObjectWriter(repository);
  }
}
