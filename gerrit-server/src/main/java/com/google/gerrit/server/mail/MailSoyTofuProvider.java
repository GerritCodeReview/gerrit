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

package com.google.gerrit.server.mail;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import com.google.template.soy.SoyFileSet;
import com.google.template.soy.tofu.SoyTofu;

import java.io.File;
import java.net.URL;

/** Configures Soy Tofu object for rendering email templates. */
@Singleton
public class MailSoyTofuProvider implements Provider<SoyTofu> {

  // Note: will fail to construct the tofu object if this array is empty.
  private static final String[] TEMPLATES = {
    "footer.soy",
  };

  private final SitePaths site;

  @Inject
  MailSoyTofuProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public SoyTofu get() throws ProvisionException {
    SoyFileSet.Builder builder = new SoyFileSet.Builder();
    for (String name : TEMPLATES) {
      addTemplate(builder, name);
    }
    return builder.build().compileToTofu();
  }

  private void addTemplate(SoyFileSet.Builder builder, String name)
      throws ProvisionException {
    // Load as a file in the mail templates directory if present.
    File file = new File(site.mail_dir.toAbsolutePath().toString(), name);
    if (file.exists()) {
      builder.add(file);
      return;
    }

    // Otherwise load the template as a resource.
    String resourcePath = "/com/google/gerrit/server/mail/" + name;
    URL resourceURL = this.getClass().getResource(resourcePath);
    if (resourceURL == null) {
      throw new ProvisionException("Template " + name + " not found.");
    }
    builder.add(resourceURL);
  }
}
