/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export declare interface ChangeUpdatesPluginApi {
  /**
   * Must only be called once. You cannot register twice. You cannot unregister.
   */
  register(publisher: ChangeUpdatesPublisher): void;
}

/**
 * Publisher that notifies subscribers whenever the change updates.
 *
 * This update could be change being submitted, new patchset being uploaded etc.
 * Plugins are expected to use the ChangeUpdatesPluginApi to register a publisher.
 * Gerrit will use the "subscribe" method to register a callback to this publisher.
 * One use case is updating the ChangeModel state whenever the change is updated.
 */
export declare interface ChangeUpdatesPublisher {
  /**
   * Subcribers can use this method to add a callback that will be triggered when updates to the change happen.
   *
   * @param repo Repository containing the change.
   * @param change The change number of the change for which update events are published.
   * @param callback The callback to be called when the change is updated.
   */
  subscribe(repo: string, change: number, callback: () => void): void;
  /**
   * Remove existing callbacks. Does nothing if no subscriber was added.
   */
  unsubscribe(): void;
}
