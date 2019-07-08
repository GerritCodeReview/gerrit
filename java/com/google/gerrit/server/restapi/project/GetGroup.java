package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.GroupResolver;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetGroup {
  private final GroupResolver groupResolver;

  @Inject
  public GetGroup(GroupResolver groupResolver) {
    this.groupResolver = groupResolver;
  }

  public AccountGroup.UUID getGroupUuidById(String id) {
    try {
      return groupResolver.parse(id).getGroupUUID();
    } catch (UnprocessableEntityException ex) {
      return null;
    }
  }
}
