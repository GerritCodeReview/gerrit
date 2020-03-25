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
import {action, computed, decorate, observable} from 'mobx/lib/mobx.es6.js';

class LabelStore {
  // When using TypeScript you would just define a private field with an
  // @observable decorator.
  constructor() {
    this.labels = {};
  }

  // When using TypeScript you would just annotate this with @action.
  changeLoaded(change) {
    this.labels = change.labels;
  }

  // When using TypeScript you would just annotate this with @computed.
  get hasLabels() {
    return !!this.labels && Object.keys(this.labels).length > 0;
  }

  // When using TypeScript you would just annotate this with @computed.
  get labelNames() {
    return Object.keys(this.labels).sort();
  }
}

// Not needed when using TypeScript.
decorate(LabelStore, {
  labels: observable,
  changeLoaded: action,
  hasLabels: computed,
  labelNames: computed,
});

const labelStore = new LabelStore();
export default labelStore;

