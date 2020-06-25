/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {DomUtilBehavior} from './dom-util-behavior.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const nestedStructureFixture = fixtureFromTemplate(html`
  <dom-util-behavior-test-element></dom-util-behavior-test-element>
  <div>
    <div class="a">
      <div class="b">
        <div class="c"></div>
      </div>
    </div>
  </div>
`);

suite('dom-util-behavior tests', () => {
  let element;
  let divs;

  suiteSetup(() => {
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'dom-util-behavior-test-element',
      behaviors: [DomUtilBehavior],
    });
  });

  setup(() => {
    const testDom = nestedStructureFixture.instantiate();
    element = testDom[0];
    divs = testDom[1];
  });

  test('descendedFromClass', () => {
    // .c is a child of .a and not vice versa.
    assert.isTrue(element.descendedFromClass(divs.querySelector('.c'), 'a'));
    assert.isFalse(element.descendedFromClass(divs.querySelector('.a'), 'c'));

    // Stops at stop element.
    assert.isFalse(element.descendedFromClass(divs.querySelector('.c'), 'a',
        divs.querySelector('.b')));
  });
});

