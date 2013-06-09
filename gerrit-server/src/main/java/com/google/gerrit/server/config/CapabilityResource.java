package com.google.gerrit.server.config;

import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class CapabilityResource extends ConfigResource {
  public static final TypeLiteral<RestView<CapabilityResource>> CAPABILITY_KIND =
      new TypeLiteral<RestView<CapabilityResource>>() {};

}
