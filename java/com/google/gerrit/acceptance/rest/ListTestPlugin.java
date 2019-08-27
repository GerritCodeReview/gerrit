package com.google.gerrit.acceptance.rest;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;

public class ListTestPlugin implements RestReadView<ConfigResource> {

  @Override
  public Object apply(ConfigResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    return ImmutableList.of();
  }
}
