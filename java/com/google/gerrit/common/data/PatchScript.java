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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.prettify.common.SparseFileContent;
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

    PatchScriptFileInfo(
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

  private final Change.Key changeId;
  private final ChangeType changeType;
  private final ImmutableList<String> header;
  private final DiffPreferencesInfo diffPrefs;
  private final ImmutableList<Edit> edits;
  private final ImmutableSet<Edit> editsDueToRebase;
  private final ImmutableList<Patch> history;
  private final boolean intralineFailure;
  private final boolean intralineTimeout;
  private final boolean binary;
  private final PatchScriptFileInfo fileInfoA;
  private final PatchScriptFileInfo fileInfoB;

  public PatchScript(
      Change.Key ck,
      ChangeType ct,
      String on,
      String nn,
      FileMode om,
      FileMode nm,
      ImmutableList<String> h,
      DiffPreferencesInfo dp,
      SparseFileContent ca,
      SparseFileContent cb,
      ImmutableList<Edit> e,
      ImmutableSet<Edit> editsDueToRebase,
      DisplayMethod ma,
      DisplayMethod mb,
      String mta,
      String mtb,
      ImmutableList<Patch> hist,
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
    history = hist;
    intralineFailure = idf;
    intralineTimeout = idt;
    binary = bin;

    fileInfoA = new PatchScriptFileInfo(on, om, ca, ma, mta, cma);
    fileInfoB = new PatchScriptFileInfo(nn, nm, cb, mb, mtb, cmb);
  }

  public Change.Key getChangeId() {
    return changeId;
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

  public List<Patch> getHistory() {
    return history;
  }

  public DiffPreferencesInfo getDiffPrefs() {
    return diffPrefs;
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

  public SparseFileContent getA() {
    return fileInfoA.content;
  }

  public SparseFileContent getB() {
    return fileInfoB.content;
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

  public PatchScriptFileInfo getFileInfoA() {
    return fileInfoA;
  }

  public PatchScriptFileInfo getFileInfoB() {
    return fileInfoB;
  }
}
