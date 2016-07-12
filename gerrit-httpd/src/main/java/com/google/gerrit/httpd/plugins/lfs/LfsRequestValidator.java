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

package com.google.gerrit.httpd.plugins.lfs;

import static com.google.gerrit.extensions.client.ProjectState.HIDDEN;
import static com.google.gerrit.extensions.client.ProjectState.READ_ONLY;
import static com.google.gerrit.httpd.plugins.LfsPluginServlet.URL_REGEX;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.ProjectUtil;
import com.google.gerrit.extensions.client.LfsState;
import com.google.gerrit.httpd.plugins.lfs.LfsRequestValidator.LfsRequestSpec.Operation;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

@Singleton
public class LfsRequestValidator {
  private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

  private final ProjectCache projectCache;
  private final Gson gson;

  @Inject
  public LfsRequestValidator(ProjectCache projectCache) {
    this.projectCache = projectCache;
    this.gson = createGson();
  }

  public void validate(HttpServletRequest req)
      throws IOException, LfsValidationException {
    LfsRequestSpec spec = getSpecFromRequest(req);
    ProjectState state = projectCache.get(spec.project);
    if ((state == null)
        || (state.getProject().getState() == HIDDEN)
        || (state.getProject().getLfsState() == LfsState.DISABLED)) {
      throw new LfsValidationException(LfsValidationException.NOT_FOUND,
          String.format("Project %s was not found", spec.project));
    }

    // authorize by default read and verify operations
    if (spec.operation != Operation.UPLOAD) {
      return;
    }

    Project project = state.getProject();
    if ((project.getLfsState() == LfsState.READ_ONLY)
        || (project.getState() == READ_ONLY)) {
      throw new LfsValidationException(LfsValidationException.UPLOAD_FORBIDDEN,
          String.format("Project %s is read-only", spec.project));
    }
  }

  private LfsRequestSpec getSpecFromRequest(HttpServletRequest req)
      throws IOException {
    String pathInfo = req.getPathInfo();
    pathInfo = pathInfo.startsWith("/") ? pathInfo : "/" + pathInfo;
    Matcher matcher = URL_PATTERN.matcher(pathInfo);
    if (!matcher.matches()) {
      return null;
    }

    try (Reader r = new BufferedReader(
        new InputStreamReader(req.getInputStream(), UTF_8))) {
      LfsRequest request = gson.fromJson(r, LfsRequest.class);

      String project = matcher.group(1);
      return new LfsRequestSpec(request.operation,
          ProjectUtil.stripGitSuffix(project));
    }
  }

  private static Gson createGson() {
    GsonBuilder gb = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting().disableHtmlEscaping();
    return gb.create();
  }

  public static class LfsRequestSpec {
    enum Operation {
      @SerializedName("upload")
      UPLOAD,

      @SerializedName("verify")
      VERIFY,

      @SerializedName("download")
      DOWNLOAD
      }

    public final Operation operation;
    public final Project.NameKey project;

    LfsRequestSpec(Operation operation,
        String project) {
      this.operation = operation;
      this.project = Project.NameKey.parse(project);
    }
  }

  private static class LfsRequest {
    LfsRequestSpec.Operation operation;
  }
}
