package com.google.gerrit.extensions.config;

import com.google.inject.TypeLiteral;

public class CapabilityDefinitionResource {
  public static final TypeLiteral<CapabilityDefinition> EXTERNAL_CAPABILITY_KIND =
      new TypeLiteral<CapabilityDefinition>() {};

  public CapabilityDefinitionResource() {
  }
}
