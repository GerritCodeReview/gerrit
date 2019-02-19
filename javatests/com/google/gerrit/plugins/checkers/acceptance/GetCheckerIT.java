package com.google.gerrit.plugins.checkers.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checkers.CheckerUuid;
import com.google.gerrit.plugins.checkers.api.CheckerInfo;
import com.google.gerrit.plugins.checkers.api.CheckerInput;
import com.google.inject.Inject;
import org.junit.Test;

public class GetCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getChecker() throws Exception {
    String name = "my-checker";
    String uuid = createChecker(name);

    CheckerInfo info = checkersApi.id(uuid).get();
    assertThat(info.uuid).isEqualTo(uuid);
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isNull();
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getCheckerWithDescription() throws Exception {
    String name = "my-checker";
    String description = "some description";
    String uuid = createChecker(name, description);

    CheckerInfo info = checkersApi.id(uuid).get();
    assertThat(info.uuid).isEqualTo(uuid);
    assertThat(info.name).isEqualTo(name);
    assertThat(info.description).isEqualTo(description);
    assertThat(info.createdOn).isNotNull();
  }

  @Test
  public void getNonExistingCheckerFails() throws Exception {
    String checkerUuid = CheckerUuid.make("non-existing");

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + checkerUuid);
    checkersApi.id(checkerUuid);
  }

  @Test
  public void getCheckerByNameFails() throws Exception {
    String name = "my-checker";
    createChecker(name);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    checkersApi.id(name);
  }

  @Test
  public void getCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String name = "my-checker";
    String uuid = createChecker(name);

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checkers not permitted");
    checkersApi.id(uuid);
  }

  private String createChecker(String name) throws RestApiException {
    return createChecker(name, null);
  }

  private String createChecker(String name, @Nullable String description) throws RestApiException {
    // TODO(ekempin): create test API for checkers and use it here
    CheckerInput input = new CheckerInput();
    input.name = name;
    input.description = description;
    CheckerInfo info = checkersApi.create(input).get();
    return info.uuid;
  }
}
