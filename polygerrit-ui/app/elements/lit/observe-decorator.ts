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

export * from 'lit';

export function observe(...propertyNames: string[]) {
  return (proto: any, method: string) => {
    const clazz = proto.constructor;
    if (!clazz.hasOwnProperty('_observers')) {
      Object.defineProperty(clazz, '_observers', {value: new Map()});
    }

    for (const property of propertyNames) {
      let observers = clazz._observers.get(property);
      observers ?? clazz._observers.set(property, (observers = []));
      observers.push(clazz.prototype[method]);
    }
  };
}
