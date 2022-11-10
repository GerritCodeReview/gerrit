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

package com.google.gerrit.httpd.raw;

import static com.google.gerrit.server.experiments.ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION;

import com.google.common.cache.Cache;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

abstract class DocServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private final ExperimentFeatures experimentFeatures;

  DocServlet(Cache<Path, Resource> cache, boolean refresh, ExperimentFeatures experimentFeatures) {
    super(cache, refresh);
    this.experimentFeatures = experimentFeatures;
  }

  @Override
  protected boolean shouldProcessResourceBeforeServe(
      HttpServletRequest req, HttpServletResponse rsp, Path p) {
    String nonce = (String) req.getAttribute("nonce");
    if (!experimentFeatures.isFeatureEnabled(GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION)
        || nonce == null) {
      return false;
    }
    return ResourceServlet.contentType(p.toString()).equals("text/html");
  }

  @Override
  protected Resource processResourceBeforeServe(
      HttpServletRequest req, HttpServletResponse rsp, Resource resource) {
    // ResourceServlet doesn't set character encoding for a resource. Gerrit will
    // default to setting charset to utf-8, if none provided. So we guess UTF_8 here.
    Optional<String> updatedHtml =
        HtmlDomUtil.attachNonce(
            new String(resource.raw, StandardCharsets.UTF_8), (String) req.getAttribute("nonce"));
    if (updatedHtml.isEmpty()) {
      return resource;
    }
    return new Resource(
        resource.lastModified,
        resource.contentType,
        updatedHtml.get().getBytes(StandardCharsets.UTF_8));
  }
}
