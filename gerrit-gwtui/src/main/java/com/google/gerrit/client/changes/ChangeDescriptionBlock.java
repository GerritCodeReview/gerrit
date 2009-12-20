// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class ChangeDescriptionBlock extends Composite {
  private final ChangeInfoBlock infoBlock;
  private final HTML description;

  public ChangeDescriptionBlock() {
    infoBlock = new ChangeInfoBlock();
    description = new HTML();
    description.setStyleName(Gerrit.RESOURCES.css().changeScreenDescription());

    final HorizontalPanel hp = new HorizontalPanel();
    hp.add(infoBlock);
    hp.add(description);
    initWidget(hp);
  }

  public void display(final Change chg, final PatchSetInfo info,
      final AccountInfoCache acc) {
    infoBlock.display(chg, acc);

    SafeHtml msg = new SafeHtmlBuilder().append(info.getMessage());
    msg = msg.linkify();
    msg = msg.replaceAll(Gerrit.getConfig().getCommentLinks());
    msg = new SafeHtmlBuilder().openElement("p").append(msg).closeElement("p");
    msg = msg.replaceAll("\n\n", "</p><p>");
    msg = msg.replaceAll("\n", "<br />");
    SafeHtml.set(description, msg);
  }
}
