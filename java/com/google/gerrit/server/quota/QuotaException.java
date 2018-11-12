package com.google.gerrit.server.quota;

import com.google.gerrit.extensions.restapi.RestApiException;

/**
 * Exception that was encountered while checking if there is sufficient quota to fulfill the
 * request. Can be propagated directly to the REST API.
 */
public class QuotaException extends RestApiException {
  public QuotaException(String reason) {
    super(reason);
  }
}
