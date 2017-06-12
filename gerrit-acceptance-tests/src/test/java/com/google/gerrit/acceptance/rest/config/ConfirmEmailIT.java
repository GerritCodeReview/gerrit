// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.server.config.ConfirmEmail;
import com.google.gerrit.server.mail.EmailTokenVerifier;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ConfirmEmailIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setString("auth", null, "registerEmailPrivateKey", SignedToken.generateRandomKey());
    return cfg;
  }

  @Inject private EmailTokenVerifier emailTokenVerifier;

  @Test
  public void confirm() throws Exception {
    ConfirmEmail.Input in = new ConfirmEmail.Input();
    in.token = emailTokenVerifier.encode(admin.getId(), "new.mail@example.com");
    adminRestSession.put("/config/server/email.confirm", in).assertNoContent();
  }

  @Test
  public void confirmForOtherUser_UnprocessableEntity() throws Exception {
    ConfirmEmail.Input in = new ConfirmEmail.Input();
    in.token = emailTokenVerifier.encode(user.getId(), "new.mail@example.com");
    adminRestSession.put("/config/server/email.confirm", in).assertUnprocessableEntity();
  }

  @Test
  public void confirmInvalidToken_UnprocessableEntity() throws Exception {
    ConfirmEmail.Input in = new ConfirmEmail.Input();
    in.token = "invalidToken";
    adminRestSession.put("/config/server/email.confirm", in).assertUnprocessableEntity();
  }

  @Test
  public void confirmAlreadyInUse_UnprocessableEntity() throws Exception {
    ConfirmEmail.Input in = new ConfirmEmail.Input();
    in.token = emailTokenVerifier.encode(admin.getId(), user.email);
    adminRestSession.put("/config/server/email.confirm", in).assertUnprocessableEntity();
  }
}
