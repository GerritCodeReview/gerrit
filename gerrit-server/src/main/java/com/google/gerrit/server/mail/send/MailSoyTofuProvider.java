// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.tofu.SoyTofu;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configures Soy Tofu object for rendering email templates. */
@Singleton
public class MailSoyTofuProvider implements Provider<SoyTofu> {

  // Note: will fail to construct the tofu object if this array is empty.
  private static final String[] TEMPLATES = {
    "Abandoned.soy",
    "AbandonedHtml.soy",
    "AddKey.soy",
    "AddKeyHtml.soy",
    "ChangeFooter.soy",
    "ChangeFooterHtml.soy",
    "ChangeSubject.soy",
    "Comment.soy",
    "CommentHtml.soy",
    "CommentFooter.soy",
    "CommentFooterHtml.soy",
    "DeleteReviewer.soy",
    "DeleteReviewerHtml.soy",
    "DeleteVote.soy",
    "DeleteVoteHtml.soy",
    "Footer.soy",
    "FooterHtml.soy",
    "HeaderHtml.soy",
    "Merged.soy",
    "MergedHtml.soy",
    "NewChange.soy",
    "NewChangeHtml.soy",
    "Private.soy",
    "RegisterNewEmail.soy",
    "ReplacePatchSet.soy",
    "ReplacePatchSetHtml.soy",
    "Restored.soy",
    "RestoredHtml.soy",
    "Reverted.soy",
    "RevertedHtml.soy",
  };

  private final SitePaths site;

  @Inject
  MailSoyTofuProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public SoyTofu get() throws ProvisionException {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    for (String name : TEMPLATES) {
      addTemplate(builder, name);
    }
    return builder.build().compileToTofu();
  }

  private void addTemplate(SoyFileSet.Builder builder, String name) throws ProvisionException {
    // Load as a file in the mail templates directory if present.
    Path tmpl = site.mail_dir.resolve(name);
    if (Files.isRegularFile(tmpl)) {
      String content;
      try (Reader r = Files.newBufferedReader(tmpl, StandardCharsets.UTF_8)) {
        content = CharStreams.toString(r);
      } catch (IOException err) {
        throw new ProvisionException(
            "Failed to read template file " + tmpl.toAbsolutePath().toString(), err);
      }
      builder.add(content, tmpl.toAbsolutePath().toString());
      return;
    }

    // Otherwise load the template as a resource.
    String resourcePath = "com/google/gerrit/server/mail/" + name;
    builder.add(Resources.getResource(resourcePath));
  }
}
