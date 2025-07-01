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
  /**
   * Use this method to add a callback that will be triggered when updates to the change happen.
   *
   * @param repo The repository name.
   * @param change The change number.
   * @param callback The callback to be called when the change is updated.
   */
  subscribe(repo: string, change: number, callback: () => void): void;
<<<<<<< PATCH SET (5b0c7b Refetch change if update signal is received)
  unsubscribe(): void;
||||||| BASE
  unsubcribe(repo: string, change: number): void;
=======
  /**
   * Remove existing callbacks.
   */

  unsubcribe(repo: string, change: number): void;
>>>>>>> BASE      (be22ff Add provider for change updates)
}
