
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









<dom-element id="some-element">

import '../../../test/common-test-setup.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
Polymer({
  is: 'some-element',

  properties: {
    fooBar: {
      type: Object,
      notify: true,
    },
  },
});


</dom-element>

<test-fixture id="basic">
  <template>
    <some-element></some-element>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import {addListener} from '@polymer/polymer/lib/utils/gestures.js';
import {GrEventHelper} from './gr-event-helper.js';

suite('gr-event-helper tests', () => {
  let element;
  let instance;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = fixture('basic');
    instance = new GrEventHelper(element);
  });

  teardown(() => {
    sandbox.restore();
  });

  test('onTap()', done => {
    instance.onTap(() => {
      done();
    });
    MockInteractions.tap(element);
  });

  test('onTap() cancel', () => {
    const tapStub = sandbox.stub();
    addListener(element.parentElement, 'tap', tapStub);
    instance.onTap(() => false);
    MockInteractions.tap(element);
    flushAsynchronousOperations();
    assert.isFalse(tapStub.called);
  });

  test('onClick() cancel', () => {
    const tapStub = sandbox.stub();
    element.parentElement.addEventListener('click', tapStub);
    instance.onTap(() => false);
    MockInteractions.tap(element);
    flushAsynchronousOperations();
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
    const tapStub = sandbox.stub();
    addListener(element.parentElement, 'tap', tapStub);
    instance.captureTap(() => false);
    MockInteractions.tap(element);
    flushAsynchronousOperations();
    assert.isFalse(tapStub.called);
  });

  test('captureClick() cancels click()', () => {
    const tapStub = sandbox.stub();
    element.addEventListener('click', tapStub);
    instance.captureTap(() => false);
    MockInteractions.tap(element);
    flushAsynchronousOperations();
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

