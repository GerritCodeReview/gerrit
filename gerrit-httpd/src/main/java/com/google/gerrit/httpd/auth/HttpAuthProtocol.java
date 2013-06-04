// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.auth;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.auth.Credentials;
import com.google.gerrit.server.auth.CredentialsVerifier;
import com.google.gerrit.server.auth.VerifiableCredentials;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.ServletModule;
import com.google.inject.util.Types;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class HttpAuthProtocol {

  @ExtensionPoint
  public interface CredentialsExtractor<T extends Credentials> {
    T extractCredentials(HttpServletRequest req) throws AuthProtocolException;
  }

  @ExtensionPoint
  public interface BrowserLoginHandler {
    void loginBrowser(HttpServletRequest req, HttpServletResponse resp,
        @Nullable String dest) throws IOException;
    void logoutBrowser(HttpServletRequest req, HttpServletResponse resp,
        @Nullable String dest, @Nullable Credentials creds) throws IOException;
  }

  @ExtensionPoint
  public interface ProgrammaticLoginHandler {
    void loginProgrammatic(HttpServletRequest req, HttpServletResponse resp,
        @Nullable Credentials creds) throws IOException;
  }

  final class VerifiableCredentialsExtractor<C extends Credentials, P extends CredentialsExtractor<? extends C>> {
    private final P extractor;
    private final Provider<CredentialsVerifier<C>> verifier;

    @Inject
    VerifiableCredentialsExtractor(P extractor, Provider<CredentialsVerifier<C>> verifier) {
      this.extractor = extractor;
      this.verifier = verifier;
    }

    VerifiableCredentials<C> extractCredentials(HttpServletRequest req) throws AuthProtocolException {
      return VerifiableCredentials.of(extractor.extractCredentials(req), verifier);
    }
  }

  private static final TypeLiteral<VerifiableCredentialsExtractor<?, ?>> EXTRACTOR_TYPE = new TypeLiteral<VerifiableCredentialsExtractor<?, ?>>() {
  };

  @SuppressWarnings("unchecked")
  public static <C extends Credentials> void extractorOf(Binder binder,
      Class<? extends CredentialsExtractor<? extends C>> extractor,
      Class<C> creds) {
    ParameterizedType type = Types.newParameterizedTypeWithOwner(
        HttpAuthProtocol.class, VerifiableCredentialsExtractor.class, creds, extractor);
    DynamicSet.bind(binder, EXTRACTOR_TYPE)
        .to((TypeLiteral<? extends VerifiableCredentialsExtractor<?, ?>>) TypeLiteral.get(type));
  }

  public static Module module() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        DynamicItem.itemOf(binder(), HttpAuthProtocol.BrowserLoginHandler.class);
        DynamicItem.itemOf(binder(), HttpAuthProtocol.ProgrammaticLoginHandler.class);
        DynamicSet.setOf(binder(), EXTRACTOR_TYPE);

        filter("/*").through(HttpAuthorizer.class);
      }
    };
  }

  private HttpAuthProtocol() {
  }
}
