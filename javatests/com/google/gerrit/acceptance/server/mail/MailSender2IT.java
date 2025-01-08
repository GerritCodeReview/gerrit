// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.EmailHeader;
import java.util.Map;
import org.junit.Test;

@UseLocalDisk
public class MailSender2IT extends AbstractMailIT {

  @Test
  public void outgoingMailWithACommentInUnchangedFile() throws Exception {
    createChangeWithUnchangedFileReviewed(user);
    assertThat(sender.getMessages()).hasSize(1);
    assertEquals(sender.getMessages().size(), 1);
    String bodyAsString = sender.getMessages().iterator().next().body();
    assertTrue(bodyAsString.contains("PS1, Line 1: this is line 1"));
    assertTrue(bodyAsString.contains("> comment from admin in Line 1"));
    assertTrue(bodyAsString.contains("reply from user to comment made by admin in Line 1"));
  }
}
