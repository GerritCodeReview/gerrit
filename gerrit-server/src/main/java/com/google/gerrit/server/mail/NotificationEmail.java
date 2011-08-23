// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.server.ssh.SshInfo;

import com.google.inject.Inject;
import com.jcraft.jsch.HostKey;
import java.util.List;

/**
 * Common class for sending out notifications related to alterations in
 * repositories and changes
 */
public abstract class NotificationEmail extends OutgoingEmail {
  protected SshInfo sshInfo = null;

  @Inject
  protected NotificationEmail(EmailArguments ea,
      final String anonymousCowardName, String mc) {
    super(ea, anonymousCowardName, mc);
  }

  public String getSshHost() {
    if (sshInfo == null) {
      return null;
    }

    final List<HostKey> hostKeys = sshInfo.getHostKeys();
    if (hostKeys.isEmpty()) {
      return null;
    }

    final String host = hostKeys.get(0).getHost();
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }

  protected void setListIdHeader() throws EmailException {
    // Set a reasonable list id so that filters can be used to sort messages
    setVHeader("Mailing-List", "list $email.listId");
    setVHeader("List-Id", "<$email.listId.replace('@', '.')>");
    if (getSettingsUrl() != null) {
      setVHeader("List-Unsubscribe", "<$email.settingsUrl>");
    }
  }
}
