/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ErrorCallback} from './rest';
import {ChangeInfo, FlowInfo, FlowInput} from './rest-api';

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
   * Returns true if flows are enabled for the current change.
   */
  isEnabled(): boolean;

  /**
   * Returns true if the current user can create a flow on the current change
   * (flows are enabled, the change is open, and the user is both the change
   * owner and the latest patchset uploader).
   */
  canCreateFlow(): boolean;

  /**
   * Returns the currently loaded flows for the change.
   */
  getFlows(): FlowInfo[];

  /**
   * Returns true while flows are being loaded for the change.
   */
  isLoading(): boolean;

  /**
   * Returns the canonical change URL prefix (e.g. `https://host/c/project/+/123`)
   * used in flow condition expressions.
   */
  getChangePrefix(change?: Pick<ChangeInfo, 'project' | '_number'>): string;

  /**
   * Formats a Gerrit query into a full flow stage condition string
   * (`<changePrefix> is <query>`).
   */
  buildCondition(
    query: string,
    change?: Pick<ChangeInfo, 'project' | '_number'>
  ): string;

  /**
   * Subscribes to changes in flows state (including `isEnabled`, `flows`,
   * `loading`, and `canCreateFlow`). Invokes `listener` immediately and on
   * each state change. Returns an unsubscribe callback.
   */
  subscribe(listener: () => void): () => void;

  /**
   * Creates a flow on the current change and reloads the flows model.
   */
  createFlow(flowInput: FlowInput, errFn?: ErrorCallback): Promise<void>;

  /**
   * Deletes a flow by UUID on the current change and reloads the flows model.
   */
  deleteFlow(flowId: string): Promise<void>;

  /**
   * Replaces or deletes an existing flow on the current change and reloads the
   * flows model once. If `existingFlowId` is provided, deletes it before
   * creating `flowInput` (avoiding the per-change pending flow limit). If
   * `flowInput` is null or undefined, only deletes `existingFlowId`.
   */
  upsertFlow(
    existingFlowId: string | undefined,
    flowInput: FlowInput | null | undefined,
    errFn?: ErrorCallback
  ): Promise<void>;

  /**
   * Reloads the flows for the current change.
   */
  reload(): void;
}
