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
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import java.util.List;
import java.util.Set;
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

  public static class PatchScriptFileInfo {
    public final String name;
    public final FileMode mode;
    public final SparseFileContent content;
    public final DisplayMethod displayMethod;
    public final String mimeType;
    public final String commitId;

    public PatchScriptFileInfo(
        String name,
        FileMode mode,
        SparseFileContent content,
        DisplayMethod displayMethod,
        String mimeType,
        String commitId) {
      this.name = name;
      this.mode = mode;
      this.content = content;
      this.displayMethod = displayMethod;
      this.mimeType = mimeType;
      this.commitId = commitId;
    }
  }

  private Change.Key changeId;
  private ChangeType changeType;
  private List<String> header;
  private DiffPreferencesInfo diffPrefs;
  private List<Edit> edits;
  private Set<Edit> editsDueToRebase;
  private CommentDetail comments;
  private List<Patch> history;
  private boolean hugeFile;
  private boolean intralineFailure;
  private boolean intralineTimeout;
  private boolean binary;
  private PatchScriptFileInfo fileInfoA;
  private PatchScriptFileInfo fileInfoB;

  public PatchScript(
      Change.Key ck,
      ChangeType ct,
      String on,
      String nn,
      FileMode om,
      FileMode nm,
      List<String> h,
      DiffPreferencesInfo dp,
      SparseFileContent ca,
      SparseFileContent cb,
      List<Edit> e,
      Set<Edit> editsDueToRebase,
      DisplayMethod ma,
      DisplayMethod mb,
      String mta,
      String mtb,
      CommentDetail cd,
      List<Patch> hist,
      boolean hf,
      boolean idf,
      boolean idt,
      boolean bin,
      String cma,
      String cmb) {
    changeId = ck;
    changeType = ct;
    header = h;
    diffPrefs = dp;
    edits = e;
    this.editsDueToRebase = editsDueToRebase;
    comments = cd;
    history = hist;
    hugeFile = hf;
    intralineFailure = idf;
    intralineTimeout = idt;
    binary = bin;

    fileInfoA = new PatchScriptFileInfo(on, om, ca, ma, mta, cma);
    fileInfoB = new PatchScriptFileInfo(nn, nm, cb, mb, mtb, cmb);
  }

  public Change.Key getChangeId() {
    return changeId;
  }

  public DisplayMethod getDisplayMethodA() {
    return fileInfoA.displayMethod;
  }

  public DisplayMethod getDisplayMethodB() {
    return fileInfoB.displayMethod;
  }

  public FileMode getFileModeA() {
    return fileInfoA.mode;
  }

  public FileMode getFileModeB() {
    return fileInfoB.mode;
  }

  public List<String> getPatchHeader() {
    return header;
  }

  public ChangeType getChangeType() {
    return changeType;
  }

  public String getOldName() {
    return fileInfoA.name;
  }

  public String getNewName() {
    return fileInfoB.name;
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
    return fileInfoA.content;
  }

  public SparseFileContent getB() {
    return fileInfoB.content;
  }

  public String getMimeTypeA() {
    return fileInfoA.mimeType;
  }

  public String getMimeTypeB() {
    return fileInfoB.mimeType;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public Set<Edit> getEditsDueToRebase() {
    return editsDueToRebase;
  }

  public boolean isBinary() {
    return binary;
  }

  public String getCommitIdA() {
    return fileInfoA.commitId;
  }

  public String getCommitIdB() {
    return fileInfoB.commitId;
  }

  public PatchScriptFileInfo getFileInfoA() {
    return fileInfoA;
  }

  public PatchScriptFileInfo getFileInfoB() {
    return fileInfoB;
  }
}
