/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
