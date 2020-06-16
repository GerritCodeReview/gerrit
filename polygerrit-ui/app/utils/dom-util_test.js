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
import '../test/common-test-setup-karma.js';
import {getComputedStyleValue, querySelector, querySelectorAll, descendedFromClass, getEventPath} from './dom-util.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

class TestEle extends PolymerElement {
  static get is() {
    return 'dom-util-test-element';
  }

  static get template() {
    return html`
    <div>
      <div class="a">
        <div class="b">
          <div class="c"></div>
        </div>
        <span class="ss"></span>
      </div>
      <span class="ss"></span>
    </div>
    `;
  }
}

customElements.define(TestEle.is, TestEle);

const basicFixture = fixtureFromTemplate(html`
  <div id="test" class="a b c">
    <a class="testBtn" style="color:red;"></a>
    <dom-util-test-element></dom-util-test-element>
    <span class="ss"></span>
  </div>
`);

suite('dom-util tests', () => {
  suite('getEventPath', () => {
    test('empty event', () => {
      assert.equal(getEventPath(), '');
      assert.equal(getEventPath(null), '');
      assert.equal(getEventPath(undefined), '');
      assert.equal(getEventPath({}), '');
    });

    test('event with fake path', () => {
      assert.equal(getEventPath({path: []}), '');
      assert.equal(getEventPath({path: [
        {tagName: 'dd'},
      ]}), 'dd');
    });

    test('event with fake complicated path', () => {
      assert.equal(getEventPath({path: [
        {tagName: 'dd', id: 'test', className: 'a b'},
        {tagName: 'DIV', id: 'test2', className: 'a b c'},
      ]}), 'div#test2.a.b.c>dd#test.a.b');
    });

    test('event with fake target', () => {
      const fakeTargetParent2 = {
        tagName: 'DIV', id: 'test2', className: 'a b c',
      };
      const fakeTargetParent1 = {
        parentNode: fakeTargetParent2,
        tagName: 'dd',
        id: 'test',
        className: 'a b',
      };
      const fakeTarget = {tagName: 'SPAN', parentNode: fakeTargetParent1};
      assert.equal(
          getEventPath({target: fakeTarget}),
          'div#test2.a.b.c>dd#test.a.b>span'
      );
    });

    test('event with real click', () => {
      const element = basicFixture.instantiate();
      const aLink = element.querySelector('a');
      let path;
      aLink.onclick = e => path = getEventPath(e);
      MockInteractions.click(aLink);
      assert.equal(
          path,
          `html>body>test-fixture#${basicFixture.fixtureId}>` +
          'div#test.a.b.c>a.testBtn'
      );
    });
  });

  suite('querySelector and querySelectorAll', () => {
    test('query cross shadow dom', () => {
      const element = basicFixture.instantiate();
      const theFirstEl = querySelector(element, '.ss');
      const allEls = querySelectorAll(element, '.ss');
      assert.equal(allEls.length, 3);
      assert.equal(theFirstEl, allEls[0]);
    });
  });

  suite('getComputedStyleValue', () => {
    test('color style', () => {
      const element = basicFixture.instantiate();
      const testBtn = querySelector(element, '.testBtn');
      assert.equal(
          getComputedStyleValue('color', testBtn), 'rgb(255, 0, 0)'
      );
    });
  });

  suite('descendedFromClass', () => {
    test('basic tests', () => {
      const element = basicFixture.instantiate();
      const testEl = querySelector(element, 'dom-util-test-element');
      // .c is a child of .a and not vice versa.
      assert.isTrue(descendedFromClass(querySelector(testEl, '.c'), 'a'));
      assert.isFalse(descendedFromClass(querySelector(testEl, '.a'), 'c'));

      // Stops at stop element.
      assert.isFalse(descendedFromClass(querySelector(testEl, '.c'), 'a',
          querySelector(testEl, '.b')));
    });
  });
});