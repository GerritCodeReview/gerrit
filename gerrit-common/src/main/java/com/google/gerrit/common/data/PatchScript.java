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

import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;

import org.eclipse.jgit.diff.Edit;

import java.util.List;

public class PatchScript {
  public static enum DisplayMethod {
    NONE, DIFF, IMG
  }

  public static enum FileMode {
    FILE, SYMLINK, GITLINK
  }

  protected Change.Key changeId;
  protected ChangeType changeType;
  protected String oldName;
  protected String newName;
  protected FileMode oldMode;
  protected FileMode newMode;
  protected List<String> header;
  protected AccountDiffPreference diffPrefs;
  protected SparseFileContent a;
  protected SparseFileContent b;
  protected List<Edit> edits;
  protected DisplayMethod displayMethodA;
  protected DisplayMethod displayMethodB;
  protected CommentDetail comments;
  protected List<Patch> history;
  protected boolean hugeFile;
  protected boolean intralineDifference;
  protected boolean intralineFailure;
  protected boolean intralineTimeout;

  public PatchScript(final Change.Key ck, final ChangeType ct, final String on,
      final String nn, final FileMode om, final FileMode nm,
      final List<String> h, final AccountDiffPreference dp,
      final SparseFileContent ca, final SparseFileContent cb,
      final List<Edit> e, final DisplayMethod ma, final DisplayMethod mb,
      final CommentDetail cd, final List<Patch> hist, final boolean hf,
      final boolean id, final boolean idf, final boolean idt) {
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
    comments = cd;
    history = hist;
    hugeFile = hf;
    intralineDifference = id;
    intralineFailure = idf;
    intralineTimeout = idt;
  }

  protected PatchScript() {
  }

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

  public AccountDiffPreference getDiffPrefs() {
    return diffPrefs;
  }

  public void setDiffPrefs(AccountDiffPreference dp) {
    diffPrefs = dp;
  }

  public boolean isHugeFile() {
    return hugeFile;
  }

  public boolean isIgnoreWhitespace() {
    return diffPrefs.getIgnoreWhitespace() != Whitespace.IGNORE_NONE;
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
    return diffPrefs.isExpandAllComments();
  }

  public SparseFileContent getA() {
    return a;
  }

  public SparseFileContent getB() {
    return b;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public Iterable<EditList.Hunk> getHunks() {
    int ctx = diffPrefs.getContext();
    if (ctx == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
      ctx = Math.max(a.size(), b.size());
    }
    return new EditList(edits, ctx, a.size(), b.size()).getHunks();
  }
}
