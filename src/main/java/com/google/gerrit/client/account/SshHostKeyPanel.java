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

import com.google.gerrit.client.data.SshHostKey;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.clippy.client.CopyableLabel;

class SshHostKeyPanel extends Composite {
  SshHostKeyPanel(final SshHostKey info) {
    final FlowPanel body = new FlowPanel();
    body.setStyleName("gerrit-SshHostKeyPanel");
    body.add(new SmallHeading(Util.C.sshHostKeyTitle()));
    {
      final Label fpLbl = new Label(Util.C.sshHostKeyFingerprint());
      fpLbl.setStyleName("gerrit-SshHostKeyPanel-Heading");
      body.add(fpLbl);
      final Label fpVal = new Label(info.getFingerprint());
      fpVal.setStyleName("gerrit-SshHostKeyPanel-FingerprintData");
      body.add(fpVal);
    }
    {
      final HTML hdr = new HTML(Util.C.sshHostKeyKnownHostEntry());
      hdr.setStyleName("gerrit-SshHostKeyPanel-Heading");
      body.add(hdr);
      final CopyableLabel lbl =
          new CopyableLabel(info.getHostIdent() + " " + info.getHostKey());
      lbl.addStyleName("gerrit-SshHostKeyPanel-KnownHostEntry");
      body.add(lbl);
    }
    initWidget(body);
  }
}
