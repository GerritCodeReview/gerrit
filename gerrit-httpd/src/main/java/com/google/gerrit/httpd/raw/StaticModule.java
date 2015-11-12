// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.cache.Cache;
import com.google.gerrit.httpd.GerritOptions;
import com.google.gerrit.httpd.raw.ResourceServlet.Resource;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import javax.servlet.http.HttpServlet;

public class StaticModule extends ServletModule {
  private static final String GWT_UI_SERVLET = "GwtUiServlet";
  private static final String BOWER_SERVLET = "BowerServlet";
  static final String CACHE = "static_content";

  private final GerritOptions options;
  private final FileSystem warFs;
  private final Path buckOut;
  private final Path unpackedWar;

  public StaticModule(GerritOptions options) {
    this.options = options;
    warFs = getDistributionArchive();
    if (warFs == null) {
      buckOut = getDeveloperBuckOut();
      unpackedWar = makeWarTempDir();
    } else {
      buckOut = null;
      unpackedWar = null;
    }
  }

  @Override
  protected void configureServlets() {
    serve("/static/*").with(SiteStaticDirectoryServlet.class);
    install(new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE, Path.class, Resource.class)
            .maximumWeight(1 << 20)
            .weigher(ResourceServlet.Weigher.class);
      }
    });
    if (options.enablePolyGerrit()) {
      servePolyGerritUi();
    } else if (options.enableDefaultUi()) {
      serveGwtUi();
    }
  }

  private void serveGwtUi() {
    serveRegex("^/gerrit_ui/(?!rpc/)(.*)$")
        .with(Key.get(HttpServlet.class, Names.named(GWT_UI_SERVLET)));
    if (warFs == null && buckOut != null) {
      filter("/").through(new RecompileGwtUiFilter(buckOut, unpackedWar));
    }
  }

  private void servePolyGerritUi() {
    serve("/").with(PolyGerritUiIndexServlet.class);
    serve("/c/*").with(PolyGerritUiIndexServlet.class);
    serve("/q/*").with(PolyGerritUiIndexServlet.class);
    serve("/x/*").with(PolyGerritUiIndexServlet.class);
    serve("/admin/*").with(PolyGerritUiIndexServlet.class);
    serve("/dashboard/*").with(PolyGerritUiIndexServlet.class);
    serve("/settings/*").with(PolyGerritUiIndexServlet.class);
    // TODO(dborowitz): These fragments conflict with the REST API namespace, so
    // they will need to use a different path.
    //serve("/groups/*").with(PolyGerritUiIndexServlet.class);
    //serve("/projects/*").with(PolyGerritUiIndexServlet.class);

    if (warFs == null) {
      serve("/bower_components/*")
          .with(Key.get(PolyGerritUiServlet.class, Names.named(BOWER_SERVLET)));
    }
    serve("/*").with(PolyGerritUiServlet.class);
  }

  @Provides
  @Singleton
  @Named(GWT_UI_SERVLET)
  HttpServlet getGwtUiServlet(@Named(CACHE) Cache<Path, Resource> cache)
      throws IOException {
    if (warFs != null) {
      return new WarGwtUiServlet(cache, warFs);
    } else {
      return new DeveloperGwtUiServlet(cache, unpackedWar);
    }
  }

  @Provides
  @Singleton
  PolyGerritUiIndexServlet getPolyGerritUiIndexServlet(
      @Named(CACHE) Cache<Path, Resource> cache) {
    return new PolyGerritUiIndexServlet(cache, polyGerritBasePath());
  }

  @Provides
  @Singleton
  PolyGerritUiServlet getPolyGerritUiServlet(
      @Named(CACHE) Cache<Path, Resource> cache) {
    return new PolyGerritUiServlet(cache, polyGerritBasePath());
  }

  @Provides
  @Singleton
  @Named(BOWER_SERVLET)
  PolyGerritUiServlet getPolyGerritUiBowerServlet(
      @Named(CACHE) Cache<Path, Resource> cache) {
    return new PolyGerritUiServlet(cache,
        polyGerritBasePath().resolveSibling("bower_components"));
  }

  private Path polyGerritBasePath() {
    return warFs != null
        ? warFs.getPath("/polygerrit_ui")
        : buckOut.getParent().resolve("polygerrit-ui").resolve("app");
  }

  private static FileSystem getDistributionArchive() {
    try {
      return GerritLauncher.getDistributionArchiveFileSystem();
    } catch (IOException e) {
      if ((e instanceof FileNotFoundException)
          && GerritLauncher.NOT_ARCHIVED.equals(e.getMessage())) {
        return null;
      } else {
        ProvisionException pe =
            new ProvisionException("Error reading gerrit.war");
        pe.initCause(e);
        throw pe;
      }
    }
  }

  private static Path getDeveloperBuckOut() {
    try {
      return GerritLauncher.getDeveloperBuckOut();
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  private static Path makeWarTempDir() {
    // Obtain our local temporary directory, but it comes back as a file
    // so we have to switch it to be a directory post creation.
    //
    try {
      File dstwar = GerritLauncher.createTempFile("gerrit_", "war");
      if (!dstwar.delete() || !dstwar.mkdir()) {
        throw new IOException("Cannot mkdir " + dstwar.getAbsolutePath());
      }

      // Jetty normally refuses to serve out of a symlinked directory, as
      // a security feature. Try to resolve out any symlinks in the path.
      //
      try {
        return dstwar.getCanonicalFile().toPath();
      } catch (IOException e) {
        return dstwar.getAbsoluteFile().toPath();
      }
    } catch (IOException e) {
      ProvisionException pe =
          new ProvisionException("Cannot create war tempdir");
      pe.initCause(e);
      throw pe;
    }
  }
}
