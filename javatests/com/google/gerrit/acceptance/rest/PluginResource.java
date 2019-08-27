package com.google.gerrit.acceptance.rest;

import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.TypeLiteral;

public class PluginResource extends ConfigResource {

  static final TypeLiteral<RestView<PluginResource>> PLUGIN_KIND =
      new TypeLiteral<RestView<PluginResource>>() {};
}
