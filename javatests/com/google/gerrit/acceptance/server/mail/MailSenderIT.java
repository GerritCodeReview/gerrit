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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.EmailHeader;
import com.google.gerrit.entities.EmailHeader.StringEmailHeader;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@UseLocalDisk
public class MailSenderIT extends AbstractMailIT {

  @Test
  @GerritConfig(name = "sendemail.replyToAddress", value = "custom@example.com")
  @GerritConfig(name = "receiveemail.protocol", value = "POP3")
  public void outgoingMailHasCustomReplyToHeader() throws Exception {
    createChangeWithReview(user);
    // Check that the custom address was added as Reply-To
    assertThat(sender.getMessages()).hasSize(1);
    ImmutableMap<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    assertThat(headerString(headers, "Reply-To")).isEqualTo("custom@example.com");
  }

  @Test
  public void outgoingMailHasUserEmailInReplyToHeader() throws Exception {
    createChangeWithReview(user);
    // Check that the user's email was added as Reply-To
    assertThat(sender.getMessages()).hasSize(1);
    ImmutableMap<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    assertThat(headerString(headers, "Reply-To")).contains(user.email());
  }

  @Test
  public void outgoingMailHasListHeaders() throws Exception {
    String changeId = createChangeWithReview(user);
    // Check that the mail has the expected headers
    assertThat(sender.getMessages()).hasSize(1);
    ImmutableMap<String, EmailHeader> headers = sender.getMessages().iterator().next().headers();
    String hostname = URI.create(canonicalWebUrl.get()).getHost();
    String listId = String.format("<gerrit-%s.%s>", project.get(), hostname);
    String unsubscribeLink = String.format("<%ssettings?usp=email>", canonicalWebUrl.get());
    String threadId =
        String.format(
            "<gerrit.%s.%s@%s>",
            gApi.changes().id(changeId).get().created.getTime(), changeId, hostname);
    assertThat(headerString(headers, "List-Id")).isEqualTo(listId);
    assertThat(headerString(headers, "List-Unsubscribe")).isEqualTo(unsubscribeLink);
    assertThat(headerString(headers, "In-Reply-To")).isEqualTo(threadId);
  }

  @Test
  @Sandboxed
  public void useCustomTemplates() throws Exception {
    String customTemplate =
        "{namespace com.google.gerrit.server.mail.template.ChangeSubject}\n"
            + "\n"
            + "{template ChangeSubject kind=\"text\"}CUSTOM-TEMPLATE{/template}\n";
    Files.write(sitePaths.mail_dir.resolve("ChangeSubject.soy"), customTemplate.getBytes(UTF_8));

    createChangeWithReview(user);
    String subject = headerString(sender.getMessages().iterator().next().headers(), "Subject");
    assertThat(subject).isEqualTo("CUSTOM-TEMPLATE");
  }

  @Test
  public void outgoingMailWithACommentsInUnchangedFile() throws Exception {
    String adminInlineComment = "comment from admin in Line 1";
    String userInlineComment =
        String.format("reply from user %s to comment made by admin in Line 1", user.fullName());
    String changeId =
        createChangeWithUnchangedFileReviewed(user, adminInlineComment, userInlineComment);
    List<CommentInfo> comments = gApi.changes().id(changeId).commentsRequest().getAsList();
    assertThat(comments).hasSize(2);
    String lastCommentId = Iterables.getLast(comments).id;
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    String expectedBodyAsString =
        "PS1, Line 1: this is line 1 \n" + "> " + adminInlineComment + "\n" + userInlineComment;
    assertThat(sender.getMessages()).hasSize(1);
    String bodyAsString = sender.getMessages().iterator().next().body();
    assertThat(bodyAsString).contains(expectedBodyAsString);
    assertThat(bodyAsString).contains(getChangeUrl(changeInfo) + "/comment/" + lastCommentId);
    assertThat(bodyAsString).contains("File gerrit-server/test.txt:\n");
  }

  private String headerString(Map<String, EmailHeader> headers, String name) {
    EmailHeader header = headers.get(name);
    assertThat(header).isInstanceOf(StringEmailHeader.class);
    return ((StringEmailHeader) header).getString();
  }

  private String getChangeUrl(ChangeInfo changeInfo) {
    return canonicalWebUrl.get() + "c/" + changeInfo.project + "/+/" + changeInfo._number;
  }
}
