
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


import {GrAttributeHelper} from './gr-attribute-helper.js';

suite('gr-attribute-helper tests', () => {
  let element;
  let instance;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = fixture('basic');
    instance = new GrAttributeHelper(element);
  });

  teardown(() => {
    sandbox.restore();
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
    const stub = sandbox.stub();
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

