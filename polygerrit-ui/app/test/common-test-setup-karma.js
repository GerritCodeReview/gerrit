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

import './common-test-setup.js';
import '@polymer/test-fixture/test-fixture.js';
import 'chai/chai.js';
self.assert = window.chai.assert;
self.expect = window.chai.expect;

// Tests can use fake timers (sandbox.useFakeTimers)
// Keep the original one for use in test utils methods.
const nativeSetTimeout = window.setTimeout;

/**
 * Triggers a flush of any pending events, observations, etc and calls you back
 * after they have been processed if callback is passed; otherwise returns
 * promise.
 *
 * @param {function()} callback
 */
function flush(callback) {
  // Ideally, this function would be a call to Polymer.dom.flush, but that
  // doesn't support a callback yet
  // (https://github.com/Polymer/polymer-dev/issues/851)
  window.Polymer.dom.flush();
  if (callback) {
    nativeSetTimeout(callback, 0);
  } else {
    return new Promise(resolve => {
      nativeSetTimeout(resolve, 0);
    });
  }
}

self.flush = flush;

class TestFixtureIdProvider {
  static get instance() {
    if (!TestFixtureIdProvider._instance) {
      TestFixtureIdProvider._instance = new TestFixtureIdProvider();
    }
    return TestFixtureIdProvider._instance;
  }

  constructor() {
    this.fixturesCount = 1;
  }

  generateNewFixtureId() {
    this.fixturesCount++;
    return `fixture-${this.fixturesCount}`;
  }
}

class TestFixture {
  constructor(fixtureId) {
    this.fixtureId = fixtureId;
  }

  /**
   * Create an instance of a fixture's template.
   *
   * @param {Object} model - see Data-bound sections at
   *   https://www.webcomponents.org/element/@polymer/test-fixture
   * @return {HTMLElement | HTMLElement[]} - if the fixture's template contains
   *   a single element, returns the appropriated instantiated element.
   *   Otherwise, it return an array of all instantiated elements from the
   *   template.
   */
  instantiate(model) {
    // The window.fixture method is defined in common-test-setup.js
    return window.fixture(this.fixtureId, model);
  }
}

/**
 * Wraps provided template to a test-fixture tag and adds test-fixture to
 * the document. You can use the html function to create a template.
 *
 * Example:
 * import {html} from '@polymer/polymer/lib/utils/html-tag.js';
 *
 * // Create fixture at the root level of a test file
 * const basicTestFixture = fixtureFromTemplate(html`
 *   <gr-cursor-manager cursor-target-class="targeted"></gr-cursor-manager>
 *   <ul>
 *    <li>A</li>
 *    <li>B</li>
 *    <li>C</li>
 *    <li>D</li>
 *   </ul>
 * `);
 * ...
 * // Instantiate fixture when needed:
 *
 * suite('example') {
 *   let elements;
 *   setup(() => {
 *     elements = basicTestFixture.instantiate();
 *   });
 * }
 *
 * @param {HTMLTemplateElement} template - a template for a fixture
 * @return {TestFixture} - the instance of TestFixture class
 */
function fixtureFromTemplate(template) {
  const fixtureId = TestFixtureIdProvider.instance.generateNewFixtureId();
  const testFixture = document.createElement('test-fixture');
  testFixture.setAttribute('id', fixtureId);
  testFixture.appendChild(template);
  document.body.appendChild(testFixture);
  return new TestFixture(fixtureId);
}

/**
 * Wraps provided tag to a test-fixture/template tags and adds test-fixture
 * to the document.
 *
 * Example:
 *
 * // Create fixture at the root level of a test file
 * const basicTestFixture = fixtureFromElement('gr-diff-view');
 * ...
 * // Instantiate fixture when needed:
 *
 * suite('example') {
 *   let element;
 *   setup(() => {
 *     element = basicTestFixture.instantiate();
 *   });
 * }
 *
 * @param {HTMLTemplateElement} template - a template for a fixture
 * @return {TestFixture} - the instance of TestFixture class
 */
function fixtureFromElement(tagName) {
  const template = document.createElement('template');
  template.innerHTML = `<${tagName}></${tagName}>`;
  return fixtureFromTemplate(template);
}

window.fixtureFromTemplate = fixtureFromTemplate;
window.fixtureFromElement = fixtureFromElement;
