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
import {testResolver as testResolverImpl} from './common-test-setup';
import '@polymer/test-fixture/test-fixture';
import 'chai/chai';

declare global {
  interface Window {
    flush: typeof flushImpl;
    fixtureFromTemplate: typeof fixtureFromTemplateImpl;
    fixtureFromElement: typeof fixtureFromElementImpl;
    testResolver: typeof testResolverImpl;
  }
  let flush: typeof flushImpl;
  let fixtureFromTemplate: typeof fixtureFromTemplateImpl;
  let fixtureFromElement: typeof fixtureFromElementImpl;
  let testResolver: typeof testResolverImpl;
}

// Workaround for https://github.com/karma-runner/karma-mocha/issues/227
let unhandledError: ErrorEvent;

window.addEventListener('error', e => {
  // For uncaught error mochajs doesn't print the full stack trace.
  // We should print it ourselves.
  console.error('Uncaught error:');
  console.error(e);
  console.error(e.error.stack.toString());
  unhandledError = e;
});

let originalOnBeforeUnload: typeof window.onbeforeunload;

suiteSetup(() => {
  // This suiteSetup() method is called only once before all tests

  // Can't use window.addEventListener("beforeunload",...) here,
  // the handler is raised too late.
  originalOnBeforeUnload = window.onbeforeunload;
  window.onbeforeunload = function (e: BeforeUnloadEvent) {
    // If a test reloads a page, we can't prevent it.
    // However we can print an error and the stack trace with assert.fail
    try {
      throw new Error();
    } catch (e) {
      console.error('Page reloading attempt detected.');
      if (e instanceof Error) {
        console.error(e.stack?.toString());
      }
    }
    if (originalOnBeforeUnload) {
      originalOnBeforeUnload.call(window, e);
    }
  };
});

suiteTeardown(() => {
  // This suiteTeardown() method is called only once after all tests
  window.onbeforeunload = originalOnBeforeUnload;
  if (unhandledError) {
    throw unhandledError;
  }
});

// Tests can use fake timers (sandbox.useFakeTimers)
// Keep the original one for use in test utils methods.
const nativeSetTimeout = window.setTimeout;

function flushImpl(): Promise<void>;
function flushImpl(callback: () => void): void;
/**
 * Triggers a flush of any pending events, observations, etc and calls you back
 * after they have been processed if callback is passed; otherwise returns
 * promise.
 */
function flushImpl(callback?: () => void): Promise<void> | void {
  // Ideally, this function would be a call to Polymer.dom.flush, but that
  // doesn't support a callback yet
  // (https://github.com/Polymer/polymer-dev/issues/851)
  // The type is used only in one place, disable eslint warning instead of
  // creating an interface
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (window as any).Polymer.dom.flush();
  if (callback) {
    nativeSetTimeout(callback, 0);
  } else {
    return new Promise(resolve => {
      nativeSetTimeout(resolve, 0);
    });
  }
}

self.flush = flushImpl;

class TestFixtureIdProvider {
  public static readonly instance: TestFixtureIdProvider =
    new TestFixtureIdProvider();

  private fixturesCount = 1;

  generateNewFixtureId() {
    this.fixturesCount++;
    return `fixture-${this.fixturesCount}`;
  }
}

interface TagTestFixture<T extends Element> {
  instantiate(model?: unknown): T;
}

class TestFixture {
  constructor(readonly fixtureId: string) {}

  /**
   * Create an instance of a fixture's template.
   *
   * @param model - see Data-bound sections at
   *   https://www.webcomponents.org/element/@polymer/test-fixture
   * @return - if the fixture's template contains
   *   a single element, returns the appropriated instantiated element.
   *   Otherwise, it return an array of all instantiated elements from the
   *   template.
   */
  instantiate(model?: unknown): HTMLElement | HTMLElement[] {
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
 * @param template - a template for a fixture
 */
function fixtureFromTemplateImpl(template: HTMLTemplateElement): TestFixture {
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
 * @param tagName - a template for a fixture is <tagName></tagName>
 */
function fixtureFromElementImpl<T extends keyof HTMLElementTagNameMap>(
  tagName: T
): TagTestFixture<HTMLElementTagNameMap[T]> {
  const template = document.createElement('template');
  template.innerHTML = `<${tagName}></${tagName}>`;
  return fixtureFromTemplate(template) as unknown as TagTestFixture<
    HTMLElementTagNameMap[T]
  >;
}

window.fixtureFromTemplate = fixtureFromTemplateImpl;
window.fixtureFromElement = fixtureFromElementImpl;
window.testResolver = testResolverImpl;
