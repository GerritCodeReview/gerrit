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

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class ChangeDescriptionBlock extends Composite {
  private final DisclosurePanel descriptionPanel;
  private final ChangeInfoBlock infoBlock;
  private final HTML description;

  public ChangeDescriptionBlock() {
    infoBlock = new ChangeInfoBlock();
    description = new HTML();
    description.setStyleName("gerrit-ChangeScreen-Description");
    descriptionPanel = new DisclosurePanel(Util.C.changeScreenDescription());
    {
      final Label glue = new Label();
      final HorizontalPanel hp = new HorizontalPanel();
      hp.add(description);
      hp.add(glue);
      hp.add(infoBlock);
      hp.setCellWidth(glue, "15px;");
      descriptionPanel.setContent(hp);
      descriptionPanel.setWidth("100%");
    }
    initWidget(descriptionPanel);
  }

  public void display(final Change chg, final PatchSetInfo info,
      final AccountInfoCache acc) {
    infoBlock.display(chg, acc);
    SafeHtml.set(description, new SafeHtmlBuilder().append(info.getMessage())
        .linkify());
    descriptionPanel.setOpen(true);
  }
}
