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

import com.google.codereview.internal.UploadPatchsetFile.UploadPatchsetFileRequest.StatusType;

import junit.framework.TestCase;

import org.spearce.jgit.lib.ObjectId;

import java.io.UnsupportedEncodingException;

public class FileDiffTest extends TestCase {
  public void testConstructor() {
    final FileDiff fd = new FileDiff();
    assertNull(fd.getBaseId());
    assertNull(fd.getFilename());
    assertSame(StatusType.MODIFY, fd.getStatus());
    assertEquals("", fd.getPatch());
  }

  public void testBaseId() {
    final ObjectId id1 =
        ObjectId.fromString("fc5ac44497e0548c32506b9c584248fc49bb9f97");
    final ObjectId id2 =
        ObjectId.fromString("8abf2492d8c5228192a3cba5528e47b3a4bb87e0");
    final FileDiff fd = new FileDiff();
    fd.setBaseId(id1);
    assertSame(id1, fd.getBaseId());
    fd.setBaseId(id2);
    assertSame(id2, fd.getBaseId());
  }

  public void testFilename() {
    final FileDiff fd = new FileDiff();
    final String name1 = "foo";
    final String name2 = "foo/bar/baz";
    fd.setFilename(name1);
    assertSame(name1, fd.getFilename());
    fd.setFilename(name2);
    assertSame(name2, fd.getFilename());
  }

  public void testStatus() {
    final FileDiff fd = new FileDiff();
    fd.setStatus(StatusType.ADD);
    assertSame(StatusType.ADD, fd.getStatus());
    fd.setStatus(StatusType.MODIFY);
    assertSame(StatusType.MODIFY, fd.getStatus());
    fd.setStatus(StatusType.DELETE);
    assertSame(StatusType.DELETE, fd.getStatus());
  }

  public void testPatchBody() {
    final FileDiff fd = new FileDiff();
    final String n1 = "diff --git a/foo b/foo";
    final String n2 = "--- a/foo";
    final String n3 = "+++ b/foo";
    final String n4 = "@@ -20,7 + 20,7 @@";

    fd.appendLine(toBytes(n1));
    fd.appendLine(toBytes(n2));
    fd.appendLine(toBytes(n3));
    fd.appendLine(toBytes(n4));
    assertEquals(n1 + "\n" + n2 + "\n" + n3 + "\n" + n4 + "\n", fd.getPatch());
  }

  private static byte[] toBytes(final String s) {
    final String enc = "UTF-8";
    try {
      return s.getBytes(enc);
    } catch (UnsupportedEncodingException uee) {
      throw new RuntimeException("No " + enc + " support?", uee);
    }
  }
}
