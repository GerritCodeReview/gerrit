// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.prettify.client;

import com.google.gwtexpui.safehtml.client.SafeHtml;

public interface SparseHtmlFile {
  /** @return the line of formatted HTML. */
  public SafeHtml getSafeHtmlLine(int lineNo);

  /** @return the number of lines in this sparse list. */
  public int size();

  /** @return true if the line is valid in this sparse list. */
  public boolean contains(int idx);

  /** @return true if this line ends in the middle of a character edit span. */
  public boolean hasTrailingEdit(int idx);
}
