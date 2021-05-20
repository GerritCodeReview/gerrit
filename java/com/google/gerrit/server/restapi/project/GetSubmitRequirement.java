package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.inject.Singleton;

@Singleton
public class GetSubmitRequirement implements RestReadView<SubmitRequirementResource> {
  @Override
  public Response<SubmitRequirementInfo> apply(SubmitRequirementResource rsrc)
      throws AuthException, BadRequestException {
    return Response.ok(SubmitRequirementJson.format(rsrc.getSubmitRequirement()));
  }
}
