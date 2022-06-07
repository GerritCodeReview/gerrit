/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export type UnsubscribeCallback = () => void;

export declare interface EventHelperPluginApi {
  /**
   * Alias for @see onClick
   */
  onTap(callback: (event: Event) => boolean): UnsubscribeCallback;

  /**
   * Add a callback to element click or touch.
   * The callback may return false to prevent event bubbling.
   */
  onClick(callback: (event: Event) => boolean): UnsubscribeCallback;
}
