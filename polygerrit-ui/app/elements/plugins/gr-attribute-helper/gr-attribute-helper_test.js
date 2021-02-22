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
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {GrAttributeHelper} from './gr-attribute-helper.js';

Polymer({
  is: 'gr-attribute-helper-some-element',
  properties: {
    fooBar: {
      type: Object,
      notify: true,
    },
  },
});

const basicFixture = fixtureFromElement('gr-attribute-helper-some-element');

suite('gr-attribute-helper tests', () => {
  let element;
  let instance;

  setup(() => {
    element = basicFixture.instantiate();
    instance = new GrAttributeHelper(element);
  });

  test('resolved on value change from undefined', () => {
    const promise = instance.get('fooBar').then(value => {
      assert.equal(value, 'foo! bar!');
    });
    element.fooBar = 'foo! bar!';
    return promise;
  });

  test('resolves to current attribute value', () => {
    element.fooBar = 'foo-foo-bar';
    const promise = instance.get('fooBar').then(value => {
      assert.equal(value, 'foo-foo-bar');
    });
    element.fooBar = 'no bar';
    return promise;
  });

  test('bind', () => {
    const stub = sinon.stub();
    element.fooBar = 'bar foo';
    const unbind = instance.bind('fooBar', stub);
    element.fooBar = 'partridge in a foo tree';
    element.fooBar = 'five gold bars';
    assert.equal(stub.callCount, 3);
    assert.deepEqual(stub.args[0], ['bar foo']);
    assert.deepEqual(stub.args[1], ['partridge in a foo tree']);
    assert.deepEqual(stub.args[2], ['five gold bars']);
    stub.reset();
    unbind();
    instance.fooBar = 'ladies dancing';
    assert.isFalse(stub.called);
  });
});

