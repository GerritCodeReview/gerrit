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
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.shared.SoyAstCache;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configures Soy Sauce object for rendering email templates. */
@Singleton
public class MailSoySauceProvider implements Provider<SoySauce> {

  // Note: will fail to construct the tofu object if this array is empty.
  private static final String[] TEMPLATES = {
    "Abandoned.soy",
    "AbandonedHtml.soy",
    "AddKey.soy",
    "AddKeyHtml.soy",
    "AddToAttentionSet.soy",
    "AddToAttentionSetHtml.soy",
    "ChangeFooter.soy",
    "ChangeFooterHtml.soy",
    "ChangeSubject.soy",
    "Comment.soy",
    "CommentHtml.soy",
    "CommentFooter.soy",
    "CommentFooterHtml.soy",
    "DeleteKey.soy",
    "DeleteKeyHtml.soy",
    "DeleteReviewer.soy",
    "DeleteReviewerHtml.soy",
    "DeleteVote.soy",
    "DeleteVoteHtml.soy",
    "InboundEmailRejection.soy",
    "InboundEmailRejectionHtml.soy",
    "Footer.soy",
    "FooterHtml.soy",
    "HeaderHtml.soy",
    "HttpPasswordUpdate.soy",
    "HttpPasswordUpdateHtml.soy",
    "Merged.soy",
    "MergedHtml.soy",
    "NewChange.soy",
    "NewChangeHtml.soy",
    "NoReplyFooter.soy",
    "NoReplyFooterHtml.soy",
    "Private.soy",
    "RegisterNewEmail.soy",
    "RegisterNewEmailHtml.soy",
    "RemoveFromAttentionSet.soy",
    "RemoveFromAttentionSetHtml.soy",
    "ReplacePatchSet.soy",
    "ReplacePatchSetHtml.soy",
    "Restored.soy",
    "RestoredHtml.soy",
    "Reverted.soy",
    "RevertedHtml.soy",
    "SetAssignee.soy",
    "SetAssigneeHtml.soy",
  };

  private final SitePaths site;
  private final SoyAstCache cache;
  private final PluginSetContext<MailSoyTemplateProvider> templateProviders;

  @Inject
  MailSoySauceProvider(
      SitePaths site,
      SoyAstCache cache,
      PluginSetContext<MailSoyTemplateProvider> templateProviders) {
    this.site = site;
    this.cache = cache;
    this.templateProviders = templateProviders;
  }

  @Override
  public SoySauce get() throws ProvisionException {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.setSoyAstCache(cache);
    for (String name : TEMPLATES) {
      addTemplate(builder, "com/google/gerrit/server/mail/", name);
    }
    templateProviders.runEach(
        e -> e.getFileNames().forEach(p -> addTemplate(builder, e.getPath(), p)));
    return builder.build().compileTemplates();
  }

  private void addTemplate(SoyFileSet.Builder builder, String resourcePath, String name)
      throws ProvisionException {
    // Load as a file in the mail templates directory if present.
    Path tmpl = site.mail_dir.resolve(name);
    if (Files.isRegularFile(tmpl)) {
      String content;
      // TODO(davido): Consider using JGit's FileSnapshot to cache based on
      // mtime.
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
    if (!resourcePath.endsWith("/")) {
      resourcePath += "/";
    }
    builder.add(Resources.getResource(resourcePath + name));
  }
}
