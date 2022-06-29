package com.google.gerrit.server.index;

import static com.google.common.truth.Truth.assertThat;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class UserTest {
  @DataPoint public static String GOOD_USERNAME = "optimus";
  @DataPoint public static String USERNAME_WITH_SLASH = "optimus/prime";

  @Theory
  public void filenameIncludesUsername(String username) {
    assertThat(username).hasLength(7);
  }
}
