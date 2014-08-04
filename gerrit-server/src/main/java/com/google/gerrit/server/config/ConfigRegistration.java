package com.google.gerrit.server.config;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

@ExtensionPoint
public class ConfigRegistration {

  //TODO: use another means of registering plugins that doesn't require
  // a redundant input.
  public ConfigRegistration () {}

}
