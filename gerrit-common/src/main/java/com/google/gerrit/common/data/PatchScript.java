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

import com.google.gerrit.common.data.PatchScriptSettings.Whitespace;
import com.google.gerrit.prettify.client.ClientSideFormatter;
import com.google.gerrit.prettify.common.EditList;
import com.google.gerrit.prettify.common.PrettyFormatter;
import com.google.gerrit.prettify.common.PrettySettings;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.prettify.common.SparseHtmlFile;
import com.google.gerrit.reviewdb.Change;

import org.eclipse.jgit.diff.Edit;

import java.util.List;

public class PatchScript {
  public static enum DisplayMethod {
    NONE, DIFF, IMG
  }

  protected Change.Key changeId;
  protected List<String> header;
  protected PatchScriptSettings settings;
  protected SparseFileContent a;
  protected SparseFileContent b;
  protected List<Edit> edits;
  protected DisplayMethod displayMethodA;
  protected DisplayMethod displayMethodB;

  public PatchScript(final Change.Key ck, final List<String> h,
      final PatchScriptSettings s, final SparseFileContent ca,
      final SparseFileContent cb, final List<Edit> e, final DisplayMethod ma,
      final DisplayMethod mb) {
    changeId = ck;
    header = h;
    settings = s;
    a = ca;
    b = cb;
    edits = e;
    displayMethodA = ma;
    displayMethodB = mb;
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

  public List<String> getPatchHeader() {
    return header;
  }

  public int getContext() {
    return settings.getContext();
  }

  public boolean isIgnoreWhitespace() {
    return settings.getWhitespace() != Whitespace.IGNORE_NONE;
  }

  public SparseFileContent getA() {
    return a;
  }

  public SparseFileContent getB() {
    return b;
  }

  public SparseHtmlFile getSparseHtmlFileA() {
    PrettySettings s = new PrettySettings(settings.getPrettySettings());
    s.setFileName(a.getPath());
    s.setShowWhiteSpaceErrors(false);

    PrettyFormatter f = ClientSideFormatter.FACTORY.get();
    f.setPrettySettings(s);
    f.setEditFilter(PrettyFormatter.A);
    f.setEditList(getEditList());
    f.format(a);
    return f;
  }

  public SparseHtmlFile getSparseHtmlFileB() {
    PrettySettings s = new PrettySettings(settings.getPrettySettings());
    s.setFileName(b.getPath());

    PrettyFormatter f = ClientSideFormatter.FACTORY.get();
    f.setPrettySettings(s);
    f.setEditFilter(PrettyFormatter.B);
    f.setEditList(getEditList());

    if (s.isSyntaxHighlighting() && a.isWholeFile() && !b.isWholeFile()) {
      f.format(b.completeWithContext(a, getEditList()));
    } else {
      f.format(b);
    }
    return f;
  }

  public List<Edit> getEdits() {
    return edits;
  }

  public Iterable<EditList.Hunk> getHunks() {
    return getEditList().getHunks();
  }

  private EditList getEditList() {
    return new EditList(edits, getContext(), a.size(), b.size());
  }
}
