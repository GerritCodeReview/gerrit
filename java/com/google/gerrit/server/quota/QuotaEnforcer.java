package com.google.gerrit.server.quota;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Allows plugins to enforce different types of quota.
 *
 * <p>Enforcing quotas can be helpful in many scenarios. For example:
 *
 * <ul>
 *   <li>Reducing the number of QPS a user can send to Gerrit on the REST API
 *   <li>Limiting the size of a repository (project)
 *   <li>Limiting the number of changes in a repository
 *   <li>Limiting the number of actions that have the potential for spam, abuse or flooding if not
 *       limited
 * </ul>
 *
 * This endpoint gives plugins the capability to enforce any of these limits. The server will ask
 * all plugins that registered this endpoint and collect all results. In case {@link
 * #request(String, QuotaRequestContext, long)} was called and one or more plugins returned a result
 * other than {@code OK}, the server will call {@link #refill(String, QuotaRequestContext, long)} on
 * all plugins with the same parameters. Plugins that deducted tokens in the {@link #request(String,
 * QuotaRequestContext, long)} call can refill them so that users don't get charged any quota for
 * failed requests.
 *
 * <p>Not all implementations will need to deduct quota on {@link #request(String,
 * QuotaRequestContext, long)}}. Implementations that work on top of instance-attributes, such as
 * the number of projects per instance can choose not to keep any state and always check how many
 * existing projects there are and if adding the inquired number would exceed the limit. In this
 * case, {@link #request(String, QuotaRequestContext, long)} and {@link #requestNoDeduction(String,
 * QuotaRequestContext, long)}} share the same implementation and {@link #refill(String,
 * QuotaRequestContext, long)} is a no-op.
 */
@ExtensionPoint
public interface QuotaEnforcer {
  /**
   * Checks if there is at least {@code numTokens} quota to fulfil the request. Bucket-based
   * implementation can deduct the inquired number of tokens from the bucket.
   */
  QuotaResponse request(String quotaGroup, QuotaRequestContext ctx, long numTokens);

  /**
   * Checks if there is at least {@code numTokens} quota to fulfil the request. This is a pre-flight
   * request, implementations should not deduct tokens from a bucket, yet.
   */
  QuotaResponse requestNoDeduction(String quotaGroup, QuotaRequestContext ctx, long numTokens);

  /**
   * A previously requested and deducted quota has to be refilled (if possible) because the request
   * failed other quota checks. Implementations can choose to leave this a no-op in case they are
   * the first line of defence (e.g. always deduct HTTP quota even if the request failed for other
   * quota issues so that the user gets throttled).
   */
  void refill(String quotaGroup, QuotaRequestContext ctx, long numTokens);
}
