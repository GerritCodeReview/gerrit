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

import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.util.Base64;

import java.io.UnsupportedEncodingException;

public class RegisterNewEmailSender extends OutgoingEmail {
  public interface Factory {
    public RegisterNewEmailSender create(String address);
  }

  private final AuthConfig authConfig;
  private final String addr;

  @Inject
  public RegisterNewEmailSender(EmailArguments ea, AuthConfig ac,
      @Assisted final String address) {
    super(ea, "registernewemail");
    authConfig = ac;
    addr = address;
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
    final StringBuilder url = new StringBuilder();
    url.append(getGerritUrl());
    url.append("#VE,");
    url.append(getEmailRegistrationToken());

    appendText("Welcome to Gerrit Code Review at ");
    appendText(getGerritHost());
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

  public String getEmailRegistrationToken() {
    try {
      return authConfig.getEmailRegistrationToken().newToken(
          Base64.encodeBytes(addr.getBytes("UTF-8")));
    } catch (XsrfException e) {
      throw new IllegalArgumentException(e);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
