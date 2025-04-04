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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.io.CharStreams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.util.FileUtils;

/**
 * Configures and loads Soy Sauce object for rendering email templates.
 *
 * <p>It reloads templates each time when {@link #load()} is called.
 */
@Singleton
class MailSoySauceLoader {

  // Note: will fail to construct the tofu object if this array is empty.
  private static final String[] TEMPLATES = {
    "Abandoned.soy",
    "AbandonedHtml.soy",
    "AddKey.soy",
    "AddKeyHtml.soy",
    "AddToAttentionSet.soy",
    "AddToAttentionSetHtml.soy",
    "AuthTokenExpired.soy",
    "AuthTokenExpiredHtml.soy",
    "AuthTokenWillExpire.soy",
    "AuthTokenWillExpireHtml.soy",
    "AuthTokenUpdate.soy",
    "AuthTokenUpdateHtml.soy",
    "ChangeFooter.soy",
    "ChangeFooterHtml.soy",
    "ChangeHeader.soy",
    "ChangeHeaderHtml.soy",
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
    "Email.soy",
    "EmailHtml.soy",
    "InboundEmailRejection.soy",
    "InboundEmailRejectionHtml.soy",
    "Footer.soy",
    "FooterHtml.soy",
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
  };

  private static final SoySauce DEFAULT = getDefault(null).build().compileTemplates();

  private final SitePaths site;
  private final PluginSetContext<MailSoyTemplateProvider> templateProviders;

  @Inject
  MailSoySauceLoader(SitePaths site, PluginSetContext<MailSoyTemplateProvider> templateProviders) {
    this.site = site;
    this.templateProviders = templateProviders;
  }

  public SoySauce load() {
    if (!hasCustomTemplates(site, templateProviders)) {
      return DEFAULT;
    }

    SoyFileSet.Builder builder = getDefault(site);
    templateProviders.runEach(
        e -> e.getFileNames().forEach(p -> addTemplate(builder, site, e.getPath(), p)));
    return builder.build().compileTemplates();
  }

  private static boolean hasCustomTemplates(
      SitePaths site, PluginSetContext<MailSoyTemplateProvider> templateProviders) {
    try {
      if (!templateProviders.isEmpty()) {
        return true;
      }
      return Files.exists(site.mail_dir) && FileUtils.hasFiles(site.mail_dir);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private static SoyFileSet.Builder getDefault(@Nullable SitePaths site) {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    for (String name : TEMPLATES) {
      addTemplate(builder, site, "com/google/gerrit/server/mail/", name);
    }
    return builder;
  }

  private static void addTemplate(
      SoyFileSet.Builder builder, @Nullable SitePaths site, String resourcePath, String name)
      throws ProvisionException {
    if (!resourcePath.endsWith("/")) {
      resourcePath += "/";
    }
    String logicalPath = resourcePath + name;

    if (site != null) {
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
              "Failed to read template file " + tmpl.toAbsolutePath(), err);
        }
        builder.add(content, logicalPath);
        return;
      }
    }

    // Otherwise load the template as a resource.
    URL resource = MailSoySauceLoader.class.getClassLoader().getResource(logicalPath);
    checkArgument(resource != null, "resource %s not found.", logicalPath);
    builder.add(resource, logicalPath);
  }
}
