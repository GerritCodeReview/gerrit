/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export declare interface PopupPluginApi {
  /**
   * Opens the popup, inserts it into the DOM over current UI.
   * Creates the popup if not previously created. Creates and inserts the popup
   * content element, if a `moduleName` was provided in the constructor.
   * Otherwise you have to call `appendContent()` when the promise resolves.
   */
  open(): Promise<PopupPluginApi>;

  /**
   * Hides the popup.
   */
  close(): void;

  /**
   * Appends the given element as a child to the popup. Only call this method
   * when you have called `popup()` without a `moduleName`.
   */
  appendContent(el: HTMLElement): void;
}
