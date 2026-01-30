/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, PropertyValues} from 'lit';
import {Constructor} from '../utils/common-util';
import {getAppContext} from '../services/app-context';
import {ReportingService} from '../services/gr-reporting/gr-reporting';

/**
 * Mixin that reports the render time of the component.
 *
 * It measures the time between `willUpdate` and `updated`.
 * If the time exceeds `thresholdMs` (default 20ms), it reports a 'ComponentRender' timing event.
 *
 * @example
 *
 * class YourComponent extends PerformanceMixin(LitElement) {
 *   // ...
 * }
 */
export const PerformanceMixin = <T extends Constructor<LitElement>>(
  superClass: T
) => {
  class Mixin extends superClass {
    private readonly reporting: ReportingService = getAppContext().reportingService;

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
        this.reporting.reporter(
          'timing-report',
          'UI Latency',
          'ComponentRender',
          Math.round(duration),
          {
            tagName: this.tagName,
          }
        );
      }
    }
  }

  return Mixin as T;
};
