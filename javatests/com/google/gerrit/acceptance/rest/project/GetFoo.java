
package com.google.gerrit.acceptance.rest.project;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;

// @RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@RequiresCapability("printHello")
public class GetFoo implements RestReadView<ConfigResource> {
  @Override
  public Object apply(ConfigResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    return Response.ok("Foo");
  }
}
