/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo} from './rest-api';

/**
 * Information about a custom condition that can be used in a flow.
 *
 * <p>Which custom conditions are supported depends on the flow service implementation.
 */
export declare interface FlowCustomConditionInfo {
  /**
   * The name of the custom condition.
   *
   * <p>Which custom conditions are supported depends on the flow service implementation.
   */
  name: string;

  /**
   * Optional prefix that should be appended to all created conditions.
   *
   * <p>Which prefix values are supported depends on the flow service implementation.
   */
  prefix?: string;

  /**
   * Optional documentation string that describes the custom condition.
   *
   * <p>Which documentation values are supported depends on the flow service implementation.
   */
  documentation?: string;
}

export declare interface FlowsProvider {
  /**
   * List all custom conditions for the current user and the given change.
   */
  getCustomConditions(change: ChangeInfo): Promise<FlowCustomConditionInfo[]>;

  /**
   * Returns a string containing a link to the feature documentation.
   */
  getDocumentation(): string;
}

export declare interface FlowsAutosubmitProvider {
  /**
   * Returns true if autosubmit is enabled .
   */
  isAutosubmitEnabled(): boolean;
}

export declare interface FlowsPluginApi {
  /**
   * Must only be called once. You cannot register twice (throws an error).
   * You cannot unregister.
   */
  register(provider: FlowsProvider): void;

  /**
   * Must only be called once. You cannot register twice (throws an error).
   * You cannot unregister.
   */
  registerAutosubmitProvider(provider: FlowsAutosubmitProvider): void;
}
