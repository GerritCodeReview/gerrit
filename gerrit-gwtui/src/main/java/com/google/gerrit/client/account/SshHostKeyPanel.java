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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.SshHostKey;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.clippy.client.CopyableLabel;

class SshHostKeyPanel extends Composite {
  SshHostKeyPanel(SshHostKey info) {
    final FlowPanel body = new FlowPanel();
    body.setStyleName(Gerrit.RESOURCES.css().sshHostKeyPanel());
    body.add(new SmallHeading(Util.C.sshHostKeyTitle()));
    {
      final Label fpLbl = new Label(Util.C.sshHostKeyFingerprint());
      fpLbl.setStyleName(Gerrit.RESOURCES.css().sshHostKeyPanelHeading());
      body.add(fpLbl);
      final Label fpVal = new Label(info.getFingerprint());
      fpVal.setStyleName(Gerrit.RESOURCES.css().sshHostKeyPanelFingerprintData());
      body.add(fpVal);
    }
    {
      final HTML hdr = new HTML(Util.C.sshHostKeyKnownHostEntry());
      hdr.setStyleName(Gerrit.RESOURCES.css().sshHostKeyPanelHeading());
      body.add(hdr);

      final CopyableLabel lbl;
      lbl = new CopyableLabel(info.getHostIdent() + " " + info.getHostKey());
      lbl.setPreviewText(SshPanel.elide(lbl.getText(), 80));
      lbl.addStyleName(Gerrit.RESOURCES.css().sshHostKeyPanelKnownHostEntry());
      body.add(lbl);
    }
    initWidget(body);
  }
}
