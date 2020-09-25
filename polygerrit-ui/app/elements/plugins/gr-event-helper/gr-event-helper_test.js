/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import {addListener} from '@polymer/polymer/lib/utils/gestures.js';
import {GrEventHelper} from './gr-event-helper.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';

Polymer({
  is: 'gr-event-helper-some-element',

  properties: {
    fooBar: {
      type: Object,
      notify: true,
    },
  },
});

const basicFixture = fixtureFromElement('gr-event-helper-some-element');

suite('gr-event-helper tests', () => {
  let element;
  let instance;

  setup(() => {
    element = basicFixture.instantiate();
    instance = new GrEventHelper(element);
  });

  test('onTap()', done => {
    instance.onTap(() => {
      done();
    });
    MockInteractions.tap(element);
  });

  test('onTap() cancel', () => {
    const tapStub = sinon.stub();
    addListener(element.parentElement, 'tap', tapStub);
    instance.onTap(() => false);
    MockInteractions.tap(element);
    flush();
    assert.isFalse(tapStub.called);
  });

  test('onClick() cancel', () => {
    const tapStub = sinon.stub();
    element.parentElement.addEventListener('click', tapStub);
    instance.onTap(() => false);
    MockInteractions.tap(element);
    flush();
    assert.isFalse(tapStub.called);
  });

  test('captureTap()', done => {
    instance.captureTap(() => {
      done();
    });
    MockInteractions.tap(element);
  });

  test('captureClick()', done => {
    instance.captureClick(() => {
      done();
    });
    MockInteractions.tap(element);
  });

  test('captureTap() cancels tap()', () => {
    const tapStub = sinon.stub();
    addListener(element.parentElement, 'tap', tapStub);
    instance.captureTap(() => false);
    MockInteractions.tap(element);
    flush();
    assert.isFalse(tapStub.called);
  });

  test('captureClick() cancels click()', () => {
    const tapStub = sinon.stub();
    element.addEventListener('click', tapStub);
    instance.captureTap(() => false);
    MockInteractions.tap(element);
    flush();
    assert.isFalse(tapStub.called);
  });

  test('on()', done => {
    instance.on('foo', () => {
      done();
    });
    element.dispatchEvent(
        new CustomEvent('foo', {
          composed: true, bubbles: true,
        }));
  });
});

