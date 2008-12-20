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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.reviewdb.PatchLineComment;

import java.util.Iterator;
import java.util.List;

public class UnifiedDiffTable extends AbstractPatchContentTable {
  public UnifiedDiffTable() {
    table.setStyleName("gerrit-UnifiedDiffTable");
  }

  public void display(final List<PatchLine> list) {
    final StringBuilder nc = new StringBuilder();
    for (final PatchLine pLine : list) {
      appendLine(nc, pLine);
    }
    resetHtml(nc.toString());

    int row = 0;
    for (final PatchLine pLine : list) {
      setRowItem(row, pLine);
      row++;

      final List<PatchLineComment> comments = pLine.getComments();
      if (comments != null) {
        for (final Iterator<PatchLineComment> ci = comments.iterator(); ci
            .hasNext();) {
          final PatchLineComment c = ci.next();
          table.insertRow(row);
          bindComment(row, 1, c, !ci.hasNext());
          row++;
        }
      }
    }
  }

  private void appendLine(final StringBuilder nc, final PatchLine line) {
    nc.append("<tr>");
    nc.append("<td class=\"" + S_ICON_CELL + "\">&nbsp;</td>");

    nc.append("<td class=\"DiffText DiffText-");
    nc.append(line.getType().name());
    nc.append("\">");
    if (!"".equals(line.getText()))
      nc.append(PatchUtil.lineToHTML(line.getText()));
    else
      nc.append("&nbsp;");
    nc.append("</td>");

    nc.append("</tr>");
  }
}
