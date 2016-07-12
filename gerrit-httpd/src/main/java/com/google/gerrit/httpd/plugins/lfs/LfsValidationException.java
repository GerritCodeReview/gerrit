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

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gwtexpui.server.CacheHeaders;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LfsValidationException extends ValidationException {
  public static ErrorSender NOT_FOUND = new ErrorSender() {
    @Override
    void sendError(LfsValidationException e, HttpServletRequest req,
        HttpServletResponse res) throws IOException {
      Resource.NOT_FOUND.send(req, res);
    }
  };

  public static ErrorSender UPLOAD_FORBIDDEN = new ErrorSender() {
    @Override
    void sendError(LfsValidationException e, HttpServletRequest req,
        HttpServletResponse res) throws IOException {
      CacheHeaders.setNotCacheable(res);
      res.setStatus(SC_FORBIDDEN);
      res.setContentType(CONTENT_TYPE_VND_GIT_LFS_JSON);
      try (Writer w = new BufferedWriter(
          new OutputStreamWriter(res.getOutputStream(), UTF_8))) {
        JsonObject error = new JsonObject();
        error.addProperty("message", e.getMessage());
        gson.toJson(error, w);
        w.flush();
      }
    }
  };

  private static final long serialVersionUID = 1L;
  private static final String CONTENT_TYPE_VND_GIT_LFS_JSON =
      "application/vnd.git-lfs+json";
  private static final Gson gson = createGson();

  private final ErrorSender sender;

  LfsValidationException(ErrorSender sender, String reason) {
    super(reason);
    this.sender = sender;
  }

  public void sendError(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    sender.sendError(this, req, res);
  }

  static abstract class ErrorSender {
    abstract void sendError(LfsValidationException e,
        HttpServletRequest req, HttpServletResponse res) throws IOException;
  }

  private static Gson createGson() {
    GsonBuilder gb = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting().disableHtmlEscaping();
    return gb.create();
  }
}