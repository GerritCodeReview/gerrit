/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ChangeInfo, FlowInfo, FlowInput} from './rest-api';
import {ErrorCallback} from './rest';

/**
 * Snapshot of Gerrit's Flows state for the active change.
 */
export declare interface FlowsStateInfo {
  isEnabled: boolean;
  flows: FlowInfo[];
  loading: boolean;
  errorMessage?: string;
}

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
   * Returns a list of disabled custom conditions.
   */
  getDisabledConditions?(): string[];

  /**
   * Returns a list of disabled actions.
   */
  getDisabledActions?(): string[];

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

  /**
   * Returns the submit condition for autosubmit.
   */
  getSubmitCondition(): string | undefined;

  /**
   * Returns the submit action for autosubmit.
   */
  getSubmitAction(): {name: string} | undefined;
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

  /**
   * Returns the current flows state for the active change, if available.
   */
  getState(): FlowsStateInfo | undefined;

  /**
   * Subscribes to changes in the active change's flows state.
   * Returns an unsubscribe function.
   */
  onStateChanged(callback: (state: FlowsStateInfo) => void): () => void;

  /**
   * Creates a flow on the active change and triggers a reload of `FlowsModel`
   * when `reload` is true (default). Stage conditions that omit the change URL
   * prefix are automatically prefixed with `<getChangePrefix()> is `.
   * Rejects by default when flows are disabled/unloaded or on HTTP error.
   */
  createFlow(
    flowInput: FlowInput,
    errFn?: ErrorCallback,
    reload?: boolean
  ): Promise<FlowInfo | undefined>;

  /**
   * Deletes a flow on the active change and triggers a reload of `FlowsModel`
   * when `reload` is true (default).
   * Rejects by default when flows are disabled/unloaded or on HTTP error.
   */
  deleteFlow(
    flowId: string,
    errFn?: ErrorCallback,
    reload?: boolean
  ): Promise<void>;

  /**
   * Reloads flows for the active change in `FlowsModel`.
   */
  reload(): void;

  /**
   * Returns the canonical change URL prefix (e.g. `https://example.com/c/project/+/123`)
   * used in Gerrit Flow stage conditions.
   */
  getChangePrefix(change?: Pick<ChangeInfo, 'project' | '_number'>): string;
}
