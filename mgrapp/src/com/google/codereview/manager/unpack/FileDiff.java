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

import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.ObjectId;

import java.util.ArrayList;
import java.util.List;

/** Computed difference for a single file. */
class FileDiff {
  private ObjectId baseId;
  private ObjectId finalId;
  private String filename;
  private boolean binary;
  private boolean truncated;
  private boolean merge;
  private List<byte[]> lines = new ArrayList<byte[]>();
  private StatusType status = StatusType.MODIFY;
  private int linesSize;

  ObjectId getBaseId() {
    return baseId;
  }

  void setBaseId(final AnyObjectId id) {
    baseId = id.toObjectId();
  }

  ObjectId getFinalId() {
    return finalId;
  }

  void setFinalId(final AnyObjectId id) {
    finalId = id.toObjectId();
  }

  String getFilename() {
    return filename;
  }

  void setFilename(final String name) {
    filename = name;
  }

  StatusType getStatus() {
    return status;
  }

  void setStatus(final StatusType t) {
    status = t;
  }

  boolean isBinary() {
    return binary;
  }

  void setBinary(final boolean b) {
    binary = b;
  }

  boolean isTruncated() {
    return truncated;
  }

  void setTruncated(final boolean b) {
    truncated = b;
  }

  boolean isMerge() {
    return merge;
  }

  void setMerge(final boolean b) {
    merge = b;
  }

  byte[] getPatch() {
    final byte[] r = new byte[linesSize + lines.size()];
    int pos = 0;
    for (final byte[] line : lines) {
      System.arraycopy(line, 0, r, pos, line.length);
      pos += line.length;
      r[pos++] = '\n';
    }
    return r;
  }

  int getPatchSize() {
    return linesSize;
  }

  void appendLine(final byte[] line) {
    lines.add(line);
    linesSize += line.length;
  }

  void truncatePatch() {
    final List<byte[]> oldLines = lines;

    linesSize = 0;
    lines = new ArrayList<byte[]>();
    for (final byte[] b : oldLines) {
      appendLine(b);
      if (DiffReader.match(DiffReader.H_NEWPATH, b, 0)) {
        appendLine(encodeASCII("File content is too large to display"));
        break;
      }
    }
  }
}
