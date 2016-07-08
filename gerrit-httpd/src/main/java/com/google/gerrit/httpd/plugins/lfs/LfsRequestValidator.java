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

import static com.google.gerrit.httpd.plugins.LfsPluginServlet.URL_REGEX;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.ProjectUtil;
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

  private final Gson gson;

  @Inject
  public LfsRequestValidator() {
    this.gson = createGson();
  }

  public void validate(HttpServletRequest req) throws IOException {
    LfsRequestSpec spec = getSpecFromRequest(req);
    // TODO perform actual validation here
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
    public final String project;

    LfsRequestSpec(Operation operation,
        String project) {
      this.operation = operation;
      this.project = project;
    }
  }

  private static class LfsRequest {
    LfsRequestSpec.Operation operation;
  }
}
