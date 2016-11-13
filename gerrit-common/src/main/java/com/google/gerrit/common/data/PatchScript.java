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

package com.google.gerrit.common.data;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import java.util.List;
import org.eclipse.jgit.diff.Edit;

public class PatchScript {
  public enum DisplayMethod {
    NONE,
    DIFF,
    IMG
  }

  public enum FileMode {
    FILE,
    SYMLINK,
    GITLINK
  }

  private Change.Key changeId;
  private ChangeType changeType;
  private String oldName;
  private String newName;
  private FileMode oldMode;
  private FileMode newMode;
  private List<String> header;
  private DiffPreferencesInfo diffPrefs;
  private SparseFileContent a;
  private SparseFileContent b;
  private List<Edit> edits;
  private DisplayMethod displayMethodA;
  private DisplayMethod displayMethodB;
  private transient String mimeTypeA;
  private transient String mimeTypeB;
  private CommentDetail comments;
  private List<Patch> history;
  private boolean hugeFile;
  private boolean intralineDifference;
  private boolean intralineFailure;
  private boolean intralineTimeout;
  private boolean binary;
  private transient String commitIdA;
  private transient String commitIdB;

  public PatchScript(
      final Change.Key ck,
      final ChangeType ct,
      final String on,
      final String nn,
      final FileMode om,
      final FileMode nm,
      final List<String> h,
      final DiffPreferencesInfo dp,
      final SparseFileContent ca,
      final SparseFileContent cb,
      final List<Edit> e,
      final DisplayMethod ma,
      final DisplayMethod mb,
      final String mta,
      final String mtb,
      final CommentDetail cd,
      final List<Patch> hist,
      final boolean hf,
      final boolean id,
      final boolean idf,
      final boolean idt,
      boolean bin,
      final String cma,
      final String cmb) {
    changeId = ck;
    changeType = ct;
    oldName = on;
    newName = nn;
    oldMode = om;
    newMode = nm;
    header = h;
    diffPrefs = dp;
    a = ca;
    b = cb;
    edits = e;
    displayMethodA = ma;
    displayMethodB = mb;
    mimeTypeA = mta;
    mimeTypeB = mtb;
    comments = cd;
    history = hist;
    hugeFile = hf;
    intralineDifference = id;
    intralineFailure = idf;
    intralineTimeout = idt;
    binary = bin;
    commitIdA = cma;
    commitIdB = cmb;
  }

  protected PatchScript() {}

  public Change.Key getChangeId() {
    return changeId;
  }

  public DisplayMethod getDisplayMethodA() {
    return displayMethodA;
  }

  public DisplayMethod getDisplayMethodB() {
    return displayMethodB;
  }

  public FileMode getFileModeA() {
    return oldMode;
  }

  public FileMode getFileModeB() {
    return newMode;
  }

  public List<String> getPatchHeader() {
    return header;
  }

  public ChangeType getChangeType() {
    return changeType;
  }

  public String getOldName() {
    return oldName;
  }

  public String getNewName() {
    return newName;
  }

  public CommentDetail getCommentDetail() {
    return comments;
  }

  public List<Patch> getHistory() {
    return history;
  }

  public DiffPreferencesInfo getDiffPrefs() {
    return diffPrefs;
  }

  public void setDiffPrefs(DiffPreferencesInfo dp) {
    diffPrefs = dp;
  }

  public boolean isHugeFile() {
    return hugeFile;
  }

  public boolean isIgnoreWhitespace() {
    return diffPrefs.ignoreWhitespace != Whitespace.IGNORE_NONE;
  }

  public boolean hasIntralineDifference() {
    return intralineDifference;
  }

  public boolean hasIntralineFailure() {
    return intralineFailure;
  }

  public boolean hasIntralineTimeout() {
    return intralineTimeout;
  }

  public boolean isExpandAllComments() {
    return diffPrefs.expandAllComments;
  }

  public SparseFileContent getA() {
    return a;
  }

  public SparseFileContent getB() {
    return b;
  }

  public String getMimeTypeA() {
    return mimeTypeA;
  }

  public String getMimeTypeB() {
    return mimeTypeB;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public Iterable<EditList.Hunk> getHunks() {
    int ctx = diffPrefs.context;
    if (ctx == DiffPreferencesInfo.WHOLE_FILE_CONTEXT) {
      ctx = Math.max(a.size(), b.size());
    }
    return new EditList(edits, ctx, a.size(), b.size()).getHunks();
  }

  public boolean isBinary() {
    return binary;
  }

  public String getCommitIdA() {
    return commitIdA;
  }

  public String getCommitIdB() {
    return commitIdB;
  }
}
