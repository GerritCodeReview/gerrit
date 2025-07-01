/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export declare interface ChangeUpdatesPluginApi {
  /**
   * Must only be called once. You cannot register twice. You cannot unregister.
   */
  register(provider: ChangeUpdatesProvider): void;
}

/**
 * Provider for listening to change update signals.
 * Clients can use these signals to refresh the change state.
 */
export declare interface ChangeUpdatesProvider {
  subscribe(repo: string, change: number, callback: () => void): void;
  unsubscribe(): void;
}
