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
import {Observable, Subject} from 'rxjs';
import {takeUntil} from 'rxjs/operators';

/**
 * Base class for Gerrit's lit-elements.
 *
 * Adds basic functionality that we want to have available in all Gerrit's
 * components.
 */
export abstract class GrLitElement extends LitElement {
  disconnected$ = new Subject();

  /**
   * Hooks up an element property with an observable. Apart from subscribing it
   * makes sure that you are unsubscribed when the component is disconnected.
   * And it requests a template check when a new value comes in.
   *
   * Should be called from connectedCallback() such that you will be
   * re-subscribed when the component is re-connected.
   *
   * TODO: Maybe distinctUntilChanged should be applied to obs$?
   */
  subscribe<Key extends keyof this>(prop: Key, obs$: Observable<this[Key]>) {
    obs$.pipe(takeUntil(this.disconnected$)).subscribe(value => {
      const oldValue = this[prop];
      this[prop] = value;
      this.requestUpdate(prop, oldValue);
    });
  }

  disconnectedCallback() {
    this.disconnected$.next();
    super.disconnectedCallback();
  }
}
