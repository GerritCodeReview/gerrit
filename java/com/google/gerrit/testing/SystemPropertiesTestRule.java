package com.google.gerrit.testing;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import java.util.Map;
import java.util.Optional;
import org.junit.rules.ExternalResource;

/** Setup system properties before tests and return previous value after tests are finished */
public class SystemPropertiesTestRule extends ExternalResource {
  ImmutableMap<String, Optional<String>> properties;
  @Nullable ImmutableMap<String, Optional<String>> previousValues;

  public SystemPropertiesTestRule(String key, String value) {
    this(ImmutableMap.of(key, Optional.of(value)));
  }

  public SystemPropertiesTestRule(Map<String, Optional<String>> properties) {
    this.properties = ImmutableMap.copyOf(properties);
  }

  @Override
  protected void before() throws Throwable {
    super.before();
    checkState(
        previousValues == null,
        "after() wasn't called after the previous call to the before() method");
    ImmutableMap.Builder<String, Optional<String>> previousValuesBuilder = ImmutableMap.builder();
    for (String key : properties.keySet()) {
      previousValuesBuilder.put(key, Optional.ofNullable(System.getProperty(key)));
    }
    previousValues = previousValuesBuilder.build();
    properties.entrySet().stream().forEach(this::setSystemProperty);
  }

  @Override
  protected void after() {
    checkState(previousValues != null, "before() wasn't called");
    previousValues.entrySet().stream().forEach(this::setSystemProperty);
    previousValues = null;
    super.after();
  }

  private void setSystemProperty(Map.Entry<String, Optional<String>> keyValue) {
    if (keyValue.getValue().isPresent()) {
      System.setProperty(keyValue.getKey(), keyValue.getValue().get());
    } else {
      System.clearProperty(keyValue.getKey());
    }
  }
}
