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

package com.google.gerrit.server.mail;

import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.spearce.jgit.util.Base64;

import java.io.UnsupportedEncodingException;

import javax.servlet.http.HttpServletRequest;

public class RegisterNewEmailSender extends OutgoingEmail {

  public interface Factory {
    public RegisterNewEmailSender create(String address);
  }

  private final HttpServletRequest req;
  private final String addr;
  private final AuthConfig authConfig;

  @Inject
  public RegisterNewEmailSender(final GerritServer gs, final EmailSender sf,
      final PatchSetInfoFactory psif, final HttpServletRequest request,
      final AuthConfig ac, @Assisted final String address) {
    super(gs, sf, psif, null, "registernewemail");
    addr = address;
    req = request;
    authConfig = ac;
  }

  @Override
  protected void init() {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] Email Verification");
    add(RecipientType.TO, new Address(addr));
  }

  @Override
  protected boolean shouldSendMessage() {
    return true;
  }

  @Override
  protected void format() {
    final StringBuffer url = req.getRequestURL();
    url.setLength(url.lastIndexOf("/")); // cut "AccountSecurity"
    url.setLength(url.lastIndexOf("/")); // cut "rpc"
    url.setLength(url.lastIndexOf("/")); // cut "gerrit"
    url.append("/Gerrit#VE,");
    try {
      url.append(authConfig.getEmailRegistrationToken().newToken(
          Base64.encodeBytes(addr.getBytes("UTF-8"))));
    } catch (XsrfException e) {
      throw new IllegalArgumentException(e);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }

    appendText("Welcome to Gerrit Code Review at ");
    appendText(req.getServerName());
    appendText(".\n");

    appendText("\n");
    appendText("To add a verified email address to your user account, please\n");
    appendText("click on the following link:\n");
    appendText("\n");
    appendText(url.toString());
    appendText("\n");

    appendText("\n");
    appendText("If you have received this mail in error,"
        + " you do not need to take any\n");
    appendText("action to cancel the account."
        + " The account will not be activated, and\n");
    appendText("you will not receive any further emails.\n");

    appendText("\n");
    appendText("If clicking the link above does not work,"
        + " copy and paste the URL in a\n");
    appendText("new browser window instead.\n");

    appendText("\n");
    appendText("This is a send-only email address."
        + "  Replies to this message will not\n");
    appendText("be read or answered.\n");
  }
}
