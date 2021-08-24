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

import '../../test/common-test-setup-karma';
import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {subscribable, subscribe} from './subscribable';
import {Subject} from 'rxjs';
import {queryAndAssert} from '../../utils/common-util';
import {SinonFakeTimers} from 'sinon';

const subject$ = new Subject<string>();

@customElement('test-subscribable')
@subscribable
export class TestSubscribable extends LitElement {

  @subscribe(subject$)
  @property()
  value: string = '';

  render() {
    return html`<span>${this.value}</span>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'test-subscribable': TestSubscribable;
  }
}

suite('subscribable test', () => {
  let clock: SinonFakeTimers;
  let el : TestSubscribable;
  setup(async () => {
    el = document.createElement('test-subscribable') as TestSubscribable;
    document.body.appendChild(el);
    clock = sinon.useFakeTimers();
    await flush();
  });
  teardown(() => {
    clock.restore();
    document.body.removeChild(el);
  });

  test('is defined', () => {
    assert.instanceOf(el, TestSubscribable);
  });

  test('subscribe rerenders', async () => {
    assert.instanceOf(el, TestSubscribable);
    let span = queryAndAssert(el, "span");
    assert.equal(span.textContent, '');
    subject$.next("a string")
    await flush();
    assert.equal(span.textContent, 'a string');
  });
});
