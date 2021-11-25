/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {LitElement, PropertyValues} from 'lit';
export * from 'lit';

export type Observer = () => void;

export interface ObservableLitElementConstructor {
  new (): ObservableLitElement;

  _observers: Map<PropertyKey, Observer[]>;
}

export abstract class ObservableLitElement extends LitElement {
  update(changedProperties: PropertyValues) {
    const clazz = this.constructor as ObservableLitElementConstructor;
    if (clazz._observers !== undefined) {
      // Collect all observers triggered by this batch of property changes
      const observersToRun = new Set<Observer>();
      for (const propertyName of changedProperties.keys()) {
        const observers = clazz._observers.get(propertyName);
        if (observers !== undefined) {
          for (const observer of observers) {
            observersToRun.add(observer);
          }
        }
      }

      // Run the observers
      for (const observer of observersToRun) {
        observer.call(this);
      }
    }

    super.update(changedProperties);
  }
}
