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

import static org.spearce.jgit.lib.Constants.encodeASCII;

import com.google.codereview.internal.UploadPatchsetFile.UploadPatchsetFileRequest.StatusType;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.Tree;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.util.RawParseUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Parses 'git diff-tree' output into {@link FileDiff} objects. */
class DiffReader {
  private static final byte[] DIFF_GIT = encodeASCII("diff --git a/");
  private static final byte[] DIFF_CC = encodeASCII("diff --cc ");
  private static final byte[] H_DELETED_FILE = encodeASCII("deleted file ");
  private static final byte[] H_NEW_FILE = encodeASCII("new file ");
  private static final byte[] H_INDEX = encodeASCII("index ");
  static final byte[] H_NEWPATH = encodeASCII("+++ b/");
  private static final byte[] H_BINARY = encodeASCII("Binary file");
  private static final int MAX_PATCH_SIZE = 1024 * 1024; // bytes

  static boolean match(final byte[] key, final byte[] line, int offset) {
    int remain = line.length - offset;
    if (remain < key.length) {
      return false;
    }
    for (int k = 0; k < key.length;) {
      if (key[k++] != line[offset++]) {
        return false;
      }
    }
    return true;
  }

  private static String str(final byte[] b, final int off) {
    return RawParseUtils.decode(Constants.CHARSET, b, off, b.length);
  }

  private Process proc;
  private RecordInputStream in;
  private FileDiff current;
  private boolean isMerge;

  DiffReader(final Repository db, final RevCommit c) throws IOException {
    final List<String> args = new ArrayList<String>();
    args.add("git");
    args.add("--git-dir=.");
    args.add("diff-tree");

    if (c.getParentCount() > 1) {
      args.add("--cc");
      args.add("-M");
      args.add("--full-index");
      args.add(c.getId().name());
      isMerge = true;
    } else if (c.getParentCount() == 1) {
      args.add("--unified=1");
      args.add("-M");
      args.add("--full-index");
      args.add(c.getParent(0).getTree().getId().name());
      args.add(c.getTree().getId().name());
    } else if (c.getParentCount() == 0) {
      args.add("--unified=1");
      args.add("-M");
      args.add("--full-index");
      args.add(new ObjectWriter(db).writeTree(new Tree(db)).name());
      args.add(c.getTree().getId().name());
    }

    proc =
        Runtime.getRuntime().exec(args.toArray(new String[args.size()]), null,
            db.getDirectory());
    proc.getOutputStream().close();
    proc.getErrorStream().close();
    in = new RecordInputStream(proc.getInputStream());
    if (isMerge) {
      // A diff --cc output from diff-tree starts with one line
      // holding the commit we passed in as an argument.
      //
      in.readRecord('\n');
    }
  }

  FileDiff next() throws IOException {
    return readOneDiff();
  }

  private FileDiff readOneDiff() throws IOException {
    boolean consume = false;
    for (;;) {
      final byte[] hdr = in.readRecord('\n');
      if (hdr == null) {
        final FileDiff prior = current;
        current = null;
        return prior;
      }

      if ((isMerge && match(DIFF_CC, hdr, 0))
          || (!isMerge && match(DIFF_GIT, hdr, 0))) {
        final FileDiff prior = current;
        current = new FileDiff();
        current.appendLine(hdr);

        // TODO(sop) This can split the old and new names wrong if the
        // old name was "f b/c". Until we can do diffs in-core we'll
        // just assume nobody uses spaces in filenames.
        //
        final String hdrStr = str(hdr, 0);
        if (isMerge) {
          current.setFilename(hdrStr.substring(DIFF_CC.length));
          current.setMerge(true);
        } else {
          final int newpos = hdrStr.indexOf(" b/");
          if (newpos > 0) {
            current.setFilename(hdrStr.substring(newpos + 3));
          }
        }

        if (prior != null) {
          return prior;
        } else {
          continue;
        }
      }

      if (!isMerge && match(H_INDEX, hdr, 0)) {
        current.setBaseId(ObjectId.fromString(hdr, H_INDEX.length));
        current.setFinalId(ObjectId.fromString(hdr, H_INDEX.length
            + Constants.OBJECT_ID_LENGTH * 2 + 2));
      } else if (match(H_NEW_FILE, hdr, 0)) {
        current.setStatus(StatusType.ADD);
      } else if (match(H_DELETED_FILE, hdr, 0)) {
        current.setStatus(StatusType.DELETE);
      } else if (match(H_BINARY, hdr, 0)) {
        current.setBinary(true);
      } else if (match(H_NEWPATH, hdr, 0)) {
        current.setFilename(str(hdr, H_NEWPATH.length));
      }

      if (consume) {
        continue;
      } else if (current.getPatchSize() + hdr.length >= MAX_PATCH_SIZE) {
        current.truncatePatch();
        consume = true;
      } else {
        current.appendLine(hdr);
      }
    }
  }

  void close() throws IOException {
    in.close();
    try {
      proc.waitFor();
    } catch (InterruptedException ie) {
      //
    }
  }
}
