/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export declare interface AttributeHelperPluginApi {
  /**
   * Binds callback to property updates.
   *
   * @param name Property name.
   * @return Unbind function.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  bind(name: string, callback: (value: any) => void): () => any;

  /**
   * Get value of the property from wrapped object. Waits for the property
   * to be initialized if it isn't defined.
   */
  get(name: string): Promise<unknown>;

  /**
   * Sets value and dispatches event to force notify.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  set(name: string, value: any): void;
}
