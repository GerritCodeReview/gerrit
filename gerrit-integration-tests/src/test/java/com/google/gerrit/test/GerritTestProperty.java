// Copyright (C) 2011 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.test;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class GerritTestProperty<T extends Object> {

  public final static String NAMESPACE_PREFIX = "gerrit.it.";

  /**
   * Path to the Gerrit war file that should be used to initialize and start the
   * Gerrit test server.
   *
   * Must be set if no existing Gerrit server is configured against which the
   * tests should run (see {@link #GERRIT_URL}).
   */
  public final static GerritTestProperty<File> GERRIT_WAR = new FileProperty(
      "gerrit-war", true, true);

  /**
   * Web-URL of an existing Gerrit server. If set the integrations are executed
   * against this server and no test server is started. The configured Gerrit
   * server must already be running before the tests are executed.
   *
   * If set also the system properties {@link #USER} and {@link #SSH_KEY} have
   * to be set. If needed a passphrase for the SSH key can be set by
   * {@link #PASSPHRASE}. Optionally the SSH port can be configured by
   * {@link #SSH_PORT}.
   */
  public final static GerritTestProperty<URL> GERRIT_URL = new UrlProperty(
      "gerrit-url");

  /**
   * HTTP port the non-pre-configured Gerrit server should use.
   */
  public final static GerritTestProperty<Integer> HTTP_PORT =
      new IntegerProperty("http-port");

  /**
   * SSH port of the configured Gerrit server (see {@link #GERRIT_URL}).
   *
   * This system property is only relevant if an existing Gerrit server is
   * configured against which the tests are executed (see {@link #GERRIT_URL}).
   */
  public final static GerritTestProperty<Integer> SSH_PORT =
      new IntegerProperty("ssh-port", 29418);

  /**
   * Name of the user that should be used to execute the integration tests.
   *
   * This system property is only relevant if an existing Gerrit server is
   * configured against which the tests are executed (see {@link #GERRIT_URL}).
   */
  public final static GerritTestProperty<String> USER = new StringProperty(
      "user");

  /**
   * Password of the user that should be used to execute the integration tests.
   *
   * This system property is only relevant if an existing Gerrit server is
   * configured against which the tests are executed (see {@link #GERRIT_URL}).
   *
   * If this system property is set it is assumed that the configured server
   * uses LDAP for authentication.
   */
  public final static GerritTestProperty<String> PASSWORD = new StringProperty(
      "password");

  /**
   * The private SSH key file of the user (see {@link #USER}) that should be
   * used to execute the integration tests.
   *
   * This system property is only relevant if an existing Gerrit server is
   * configured against which the tests are executed (see {@link #GERRIT_URL}).
   */
  public final static GerritTestProperty<File> SSH_KEY = new FileProperty(
      "ssh-key", true, true);

  /**
   * Passphrase for the SSH key (see {@link #SSH_KEY}) of the configured user
   * (see {@link #USER}).
   *
   * This system property is only relevant if an existing Gerrit server is
   * configured against which the tests are executed (see {@link #GERRIT_URL}).
   */
  public final static GerritTestProperty<String> PASSPHRASE =
      new StringProperty("passphrase");

  /**
   * HTTP timeout for accessing the Gerrit WebUI in milliseconds.
   */
  public final static GerritTestProperty<Integer> HTTP_TIMEOUT =
      new IntegerProperty("http-timeout", 30 * 1000);

  /**
   * HTTP polling interval for accessing the Gerrit WebUI in milliseconds.
   */
  public final static GerritTestProperty<Integer> HTTP_POLLING_INTERVAL =
      new IntegerProperty("http-polling-interval", 500);

  /**
   * Number of times a HTTP page is tried to be loaded before giving up.
   */
  public final static GerritTestProperty<Integer> HTTP_RELOAD_RETRIES =
      new IntegerProperty("http-reload-retries", 3);

  /**
   * Timeout for SSH communication to Gerrit in milliseconds.
   */
  public final static GerritTestProperty<Integer> SSH_TIMEOUT =
      new IntegerProperty("ssh-timeout", 60 * 1000);

  /**
   * Polling interval for SSH communication to Gerrit in milliseconds.
   */
  public final static GerritTestProperty<Integer> SSH_POLLING_INTERVAL =
      new IntegerProperty("ssh-polling-interval", 500);

  /**
   * Selenium web driver that should be used to execute the tests.
   */
  public final static GerritTestProperty<SeleniumWebDriver> WEBDRIVER =
      new EnumProperty<SeleniumWebDriver>("webdriver",
          SeleniumWebDriver.HTMLUNIT, SeleniumWebDriver.class);

  /**
   * Trace level for the SSH communication to the Gerrit server.
   */
  public final static GerritTestProperty<SshTraceLevel> SSH_TRACE_LEVEL =
      new EnumProperty<SshTraceLevel>("ssh-trace-level", SshTraceLevel.class);

  /**
   * Time in milliseconds that is waited for a Selenium event to be processed
   * before continuing with the test.
   */
  public final static GerritTestProperty<Integer> EVENT_WAIT =
      new IntegerProperty("event-wait", 200);

  /**
   * Polling interval for Selenium events to be processed in milliseconds.
   */
  public final static GerritTestProperty<Integer> EVENT_POLLING_INTERVAL =
      new IntegerProperty("event-polling-interval", 10);

  /**
   * Trace level for the integration tests.
   */
  public final static GerritTestProperty<TraceLevel> TRACE_LEVEL =
      new EnumProperty<TraceLevel>("trace-level", TraceLevel.INFO,
          TraceLevel.class);

  private final String name;
  private final T defaultValue;
  private T value;

  protected GerritTestProperty(final String name, final T defaultValue) {
    this.name = NAMESPACE_PREFIX + name;
    this.defaultValue = defaultValue;
  }

  protected GerritTestProperty(final String name) {
    this(name, null);
  }

  public final String getName() {
    return name;
  }

  public final T get() throws InvalidPropertyValueException {
    return getValue();
  }

  public final T getOrFail() throws InvalidPropertyValueException,
      PropertyNotSetException {
    final T value = getValue();
    if (value == null) {
      throw new PropertyNotSetException(name);
    }
    return value;
  }

  private T getValue() {
    if (value == null) {
      final String sysPropValue = System.getProperty(name);
      if (sysPropValue != null) {
        value = valueOf(name, sysPropValue);
      } else {
        value = defaultValue;
      }
    }
    return value;
  }

  protected abstract T valueOf(final String name, final String value);

  static class StringProperty extends GerritTestProperty<String> {

    StringProperty(final String name) {
      super(name);
    }

    @Override
    protected String valueOf(final String name, final String value) {
      return value;
    }
  }

  static class FileProperty extends GerritTestProperty<File> {

    private final boolean checkIfExists;
    private final boolean checkIfFile;

    FileProperty(final String name) {
      this(name, false, false);
    }

    FileProperty(final String name, final boolean checkIfExists,
        final boolean checkIfFile) {
      super(name);
      this.checkIfExists = checkIfExists;
      this.checkIfFile = checkIfFile;
    }

    @Override
    protected File valueOf(final String name, final String value) {
      if (value == null) {
        return null;
      }
      final File file = new File(value);
      if ((checkIfExists && !file.exists()) || (checkIfFile && !file.isFile())) {
        throw new InvalidPropertyValueException(name);
      }
      return file;
    }
  }

  static class IntegerProperty extends GerritTestProperty<Integer> {

    IntegerProperty(final String name, final Integer defaultValue) {
      super(name, defaultValue);
    }

    public IntegerProperty(String name) {
      super(name);
    }

    @Override
    protected Integer valueOf(final String name, final String value) {
      if (value == null) {
        return null;
      }
      try {
        return Integer.valueOf(value);
      } catch (NumberFormatException e) {
        throw new InvalidPropertyValueException(name, e);
      }
    }
  }

  static class UrlProperty extends GerritTestProperty<URL> {

    UrlProperty(final String name) {
      super(name);
    }

    @Override
    protected URL valueOf(final String name, final String value) {
      if (value == null) {
        return null;
      }
      try {
        return new URL(value);
      } catch (MalformedURLException e) {
        throw new InvalidPropertyValueException(name, e);
      }
    }
  }

  static class EnumProperty<E extends Enum<E>> extends GerritTestProperty<E> {

    private final Class<E> enumClass;

    EnumProperty(final String name, final Class<E> enumClass) {
      this(name, null, enumClass);
    }

    EnumProperty(final String name, final E defaultValue,
        final Class<E> enumClass) {
      super(name, defaultValue);
      this.enumClass = enumClass;
    }

    @Override
    protected E valueOf(final String name, final String value) {
      try {
        return Enum.valueOf(enumClass, value.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new InvalidPropertyValueException(name, "Allowed values are: "
            + StringUtils.join(enumClass.getEnumConstants(), ", "), e);
      }
    }
  }
}
