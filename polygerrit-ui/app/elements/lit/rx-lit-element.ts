/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {LitElement} from 'lit-element';
import {Observable, Subject} from 'rxjs/index';
import {takeUntil} from 'rxjs/operators/index';

/**
 * This is a decorator for lit-element fields to be hooked up with a stream of
 * Observable values.
 */
export const subscribe = (stream: Observable<any>) => <K extends RxLitElement>(
  targetPrototype: K,
  propertyKey: keyof K
) => {
  if (!stream) throw Error('invalid stream input for @subscribe decorator');

  const formerConnectedCallback = targetPrototype.connectedCallback;
  targetPrototype.connectedCallback = function () {
    formerConnectedCallback.call(this);
    this.subscribe(propertyKey, stream);
  };
};

/**
 * Base class for lit-elements that subscribe to rxjs Observables. Manages
 * calling requestUpdate() for invalidating the template and unsubcribing from
 * the stream when disconnected.
 */
export abstract class RxLitElement extends LitElement {
  disconnected$ = new Subject();

  subscribe<Key extends keyof this>(
    propertyName: Key,
    stream$: Observable<this[Key]>
  ) {
    stream$.pipe(takeUntil(this.disconnected$)).subscribe(value => {
      const oldValue = this[propertyName];
      this[propertyName] = value;
      this.requestUpdate(propertyName, oldValue);
    });
  }

  disconnectedCallback() {
    this.disconnected$.next();
    super.disconnectedCallback();
  }
}
