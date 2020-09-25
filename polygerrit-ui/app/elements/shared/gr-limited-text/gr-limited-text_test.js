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
import './gr-limited-text.js';

const basicFixture = fixtureFromElement('gr-limited-text');

suite('gr-limited-text tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('tooltip without title input', () => {
    const updateSpy = sinon.spy(element, '_updateTitle');
    element.text = 'abc 123';
    flush();
    assert.isTrue(updateSpy.calledOnce);
    assert.isNotOk(element.getAttribute('title'));
    assert.isFalse(element.hasTooltip);

    element.limit = 10;
    flush();
    assert.isTrue(updateSpy.calledTwice);
    assert.isNotOk(element.getAttribute('title'));
    assert.isFalse(element.hasTooltip);

    element.limit = 3;
    flush();
    assert.equal(updateSpy.callCount, 3);
    assert.equal(element.getAttribute('title'), 'abc 123');
    assert.equal(element.title, 'abc 123');
    assert.isTrue(element.hasTooltip);

    element.limit = 100;
    flush();
    assert.equal(updateSpy.callCount, 4);
    assert.isFalse(element.hasTooltip);

    element.limit = null;
    flush();
    assert.equal(updateSpy.callCount, 5);
    assert.isNotOk(element.getAttribute('title'));
    assert.isFalse(element.hasTooltip);
  });

  test('with tooltip input', () => {
    const updateSpy = sinon.spy(element, '_updateTitle');
    element.tooltip = 'abc 123';
    flush();
    assert.isTrue(updateSpy.calledOnce);
    assert.isTrue(element.hasTooltip);
    assert.equal(element.getAttribute('title'), 'abc 123');
    assert.equal(element.title, 'abc 123');

    element.text = 'abc';
    flush();
    assert.equal(element.getAttribute('title'), 'abc 123');
    assert.isTrue(element.hasTooltip);

    element.text = 'abcdef';
    element.limit = 3;
    flush();
    assert.equal(element.getAttribute('title'), 'abcdef (abc 123)');
    assert.isTrue(element.hasTooltip);
  });

  test('_computeDisplayText', () => {
    assert.equal(element._computeDisplayText('foo bar', 100), 'foo bar');
    assert.equal(element._computeDisplayText('foo bar', 4), 'foo…');
    assert.equal(element._computeDisplayText('foo bar', null), 'foo bar');
  });

  test('when disable tooltip', () => {
    sinon.spy(element, '_updateTitle');
    element.text = 'abcdefghijklmn';
    element.disableTooltip = true;
    element.limit = 10;
    flush();
    assert.equal(element.getAttribute('title'), '');
  });
});

