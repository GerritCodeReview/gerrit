/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ValueChangedEvent} from '../../../types/events';

export class GrAttributeHelper {
  constructor(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    public element: any
  ) {}

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  bind(callback: (value: any) => void) {
    const changedHandler = (e: ValueChangedEvent) => callback(e.detail.value);
    this.element.addEventListener('value-changed', changedHandler);
    if (this.element['value'] !== undefined) {
      callback(this.element['value']);
    }
  }
}
