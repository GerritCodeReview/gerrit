/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FlagsService} from './flags';
import {Finalizable} from '../registry';

declare global {
  interface Window {
    ENABLED_EXPERIMENTS?: string[];
  }
}

/**
 * Flags service.
 *
 * Provides all related methods / properties regarding on feature flags.
 */
export class FlagsServiceImplementation implements FlagsService, Finalizable {
  private readonly _experiments: Set<string>;

  constructor() {
    // stores all enabled experiments
    this._experiments = this._loadExperiments();
  }

  finalize() {}

  isEnabled(experimentId: string): boolean {
    return this._experiments.has(experimentId);
  }

  _loadExperiments(): Set<string> {
    return new Set(window.ENABLED_EXPERIMENTS ?? []);
  }

  get enabledExperiments() {
    return [...this._experiments];
  }
}
