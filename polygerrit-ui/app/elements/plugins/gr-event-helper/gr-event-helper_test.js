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
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {mockPromise} from '../../../test/test-utils.js';

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
    let plugin;
    window.Gerrit.install(p => { plugin = p; }, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    element = basicFixture.instantiate();
    instance = plugin.eventHelper(element);
  });

  test('onTap()', async () => {
    const promise = mockPromise();
    instance.onTap(() => {
      promise.resolve();
    });
    MockInteractions.tap(element);
    await promise;
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
});

