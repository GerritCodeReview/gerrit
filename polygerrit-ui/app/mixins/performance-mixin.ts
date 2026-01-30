/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, PropertyValues} from 'lit';
import {Constructor} from '../utils/common-util';
export const PerformanceMixin = <T extends Constructor<LitElement>>(
  superClass: T
) => {
  class Mixin extends superClass {
    private _updateStart = 0;

    private readonly _thresholdMs = 20;

    override willUpdate(changedProperties: PropertyValues) {
      super.willUpdate(changedProperties);
      this._updateStart = performance.now();
    }

    override updated(changedProperties: PropertyValues) {
      super.updated(changedProperties);
      const duration = performance.now() - this._updateStart;
      if (duration > this._thresholdMs) {
        console.info(
          `[PerformanceMixin] ${this.tagName} took ${Math.round(duration)}ms`
        );
      }
    }
  }

  return Mixin as T;
};
