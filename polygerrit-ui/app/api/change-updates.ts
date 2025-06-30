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
 * Provider for listening whenever the change updates.
 * This update could be change being submitted, new patchset being uploaded etc.
 * Plugins are expected to use the ChangeUpdatesPluginApi to register a provider.
 * Gerrit will use the "subscribe" method to register a callback to this provider.
 * One use case is updating the ChangeModel state whenever the change is updated.
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
  /**
   * Remove existing callbacks.
   */

  unsubcribe(repo: string, change: number): void;
}
