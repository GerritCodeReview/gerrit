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

package com.google.gerrit.client.data;

import com.google.gerrit.client.patches.PatchSideBySideScreen;
import com.google.gerrit.client.reviewdb.Patch;

import java.util.List;

/** Detail necessary to display {@link PatchSideBySideScreen}. */
public class SideBySidePatchDetail extends BasePatchDetail {
  protected int fileCount;
  protected int lineCount;
  protected List<List<SideBySideLine>> lines;

  protected SideBySidePatchDetail() {
  }

  public SideBySidePatchDetail(final Patch p, final AccountInfoCache aic) {
    super(p, aic);
  }

  public int getFileCount() {
    return fileCount;
  }

  public int getLineCount() {
    return lineCount;
  }

  public List<List<SideBySideLine>> getLines() {
    return lines;
  }

  public void setLines(final int fc, final int lc,
      final List<List<SideBySideLine>> in) {
    fileCount = fc;
    lineCount = lc;
    lines = in;
  }
}
