/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {createDiff} from '../../../test/test-data-generators';
import './gr-diff';
import {GrDiffBuilderImage} from '../gr-diff-builder/gr-diff-builder-image';
import {getComputedStyleValue} from '../../../utils/dom-util';
import '@polymer/paper-button/paper-button';
import {
  DiffContent,
  DiffInfo,
  DiffPreferencesInfo,
  DiffViewMode,
  IgnoreWhitespaceType,
  Side,
} from '../../../api/diff';
import {
  mockPromise,
  mouseDown,
  query,
  queryAll,
  queryAndAssert,
  waitEventLoop,
  waitQueryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {AbortStop} from '../../../api/core';
import {waitForEventOnce} from '../../../utils/event-util';
import {GrDiff} from './gr-diff';
import {ImageInfo} from '../../../types/common';
import {GrRangedCommentHint} from '../gr-ranged-comment-hint/gr-ranged-comment-hint';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    assert.isAccessible(await fixture(html`<gr-diff></gr-diff>`));
  });
});

suite('gr-diff tests', () => {
  let element: GrDiff;

  const MINIMAL_PREFS: DiffPreferencesInfo = {
    tab_size: 2,
    line_length: 80,
    font_size: 12,
    context: 3,
    ignore_whitespace: 'IGNORE_NONE',
  };

  setup(async () => {
    element = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
  });

  suite('rendering', () => {
    test('empty diff', async () => {
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer sideBySide">
            <table id="diffTable"></table>
          </div>
        `
      );
    });

    test('a normal diff legacy', async () => {
      await testNormal();
    });

    test('a normal diff lit', async () => {
      // TODO(brohlfs): Make sure that test passes. Then uncomment next line.
      element.renderPrefs = {...element.renderPrefs, use_lit_components: true};
      await testNormal();
    });

    const testNormal = async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.diff = createDiff();
      await element.updateComplete;
      await waitForEventOnce(element, 'render');
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer sideBySide">
            <table class="selected-right" id="diffTable">
              <colgroup>
                <col class="blame gr-diff" />
                <col class="gr-diff left" width="48" />
                <col class="gr-diff left sign" />
                <col class="gr-diff left" />
                <col class="gr-diff right" width="48" />
                <col class="gr-diff right sign" />
                <col class="gr-diff right" />
              </colgroup>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-LOST left-content-LOST right-button-LOST right-content-LOST"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="LOST"></td>
                  <td class="gr-diff left lineNum" data-value="LOST"></td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left lost no-intraline-info">
                    <div class="thread-group" data-side="left">
                      <slot name="left-LOST"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="LOST"></td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff lost no-intraline-info right">
                    <div class="thread-group" data-side="right">
                      <slot name="right-LOST"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-FILE left-content-FILE right-button-FILE right-content-FILE"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="FILE"></td>
                  <td class="gr-diff left lineNum" data-value="FILE">
                    <button
                      aria-label="Add file comment"
                      class="gr-diff left lineNumButton"
                      data-value="FILE"
                      id="left-button-FILE"
                      tabindex="-1"
                    >
                      File
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content file gr-diff left no-intraline-info">
                    <div class="thread-group" data-side="left">
                      <slot name="left-FILE"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="FILE">
                    <button
                      aria-label="Add file comment"
                      class="gr-diff lineNumButton right"
                      data-value="FILE"
                      id="right-button-FILE"
                      tabindex="-1"
                    >
                      File
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content file gr-diff no-intraline-info right">
                    <div class="thread-group" data-side="right">
                      <slot name="right-FILE"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="1"></td>
                  <td class="gr-diff left lineNum" data-value="1">
                    <button
                      aria-label="1 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="1"
                      id="left-button-1"
                      tabindex="-1"
                    >
                      1
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-1"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-1"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="1">
                    <button
                      aria-label="1 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="1"
                      id="right-button-1"
                      tabindex="-1"
                    >
                      1
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-1"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-1"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-2 left-content-2 right-button-2 right-content-2"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="2"></td>
                  <td class="gr-diff left lineNum" data-value="2">
                    <button
                      aria-label="2 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="2"
                      id="left-button-2"
                      tabindex="-1"
                    >
                      2
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-2"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-2"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="2">
                    <button
                      aria-label="2 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="2"
                      id="right-button-2"
                      tabindex="-1"
                    >
                      2
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-2"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-2"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-3 left-content-3 right-button-3 right-content-3"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="3"></td>
                  <td class="gr-diff left lineNum" data-value="3">
                    <button
                      aria-label="3 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="3"
                      id="left-button-3"
                      tabindex="-1"
                    >
                      3
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-3"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-3"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="3">
                    <button
                      aria-label="3 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="3"
                      id="right-button-3"
                      tabindex="-1"
                    >
                      3
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-3"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-3"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-4 left-content-4 right-button-4 right-content-4"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="4"></td>
                  <td class="gr-diff left lineNum" data-value="4">
                    <button
                      aria-label="4 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="4"
                      id="left-button-4"
                      tabindex="-1"
                    >
                      4
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-4"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-4"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="4">
                    <button
                      aria-label="4 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="4"
                      id="right-button-4"
                      tabindex="-1"
                    >
                      4
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-4"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-4"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="right-button-5 right-content-5"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="5">
                    <button
                      aria-label="5 added"
                      class="gr-diff lineNumButton right"
                      data-value="5"
                      id="right-button-5"
                      tabindex="-1"
                    >
                      5
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-5"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-5"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-6 right-content-6"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="6">
                    <button
                      aria-label="6 added"
                      class="gr-diff lineNumButton right"
                      data-value="6"
                      id="right-button-6"
                      tabindex="-1"
                    >
                      6
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-6"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-6"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-7 right-content-7"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="7">
                    <button
                      aria-label="7 added"
                      class="gr-diff lineNumButton right"
                      data-value="7"
                      id="right-button-7"
                      tabindex="-1"
                    >
                      7
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-7"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-7"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-5 left-content-5 right-button-8 right-content-8"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="5"></td>
                  <td class="gr-diff left lineNum" data-value="5">
                    <button
                      aria-label="5 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="5"
                      id="left-button-5"
                      tabindex="-1"
                    >
                      5
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-5"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-5"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="8">
                    <button
                      aria-label="8 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="8"
                      id="right-button-8"
                      tabindex="-1"
                    >
                      8
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-8"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-8"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-6 left-content-6 right-button-9 right-content-9"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="6"></td>
                  <td class="gr-diff left lineNum" data-value="6">
                    <button
                      aria-label="6 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="6"
                      id="left-button-6"
                      tabindex="-1"
                    >
                      6
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-6"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-6"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="9">
                    <button
                      aria-label="9 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="9"
                      id="right-button-9"
                      tabindex="-1"
                    >
                      9
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-9"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-9"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-7 left-content-7 right-button-10 right-content-10"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="7"></td>
                  <td class="gr-diff left lineNum" data-value="7">
                    <button
                      aria-label="7 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="7"
                      id="left-button-7"
                      tabindex="-1"
                    >
                      7
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-7"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-7"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="10">
                    <button
                      aria-label="10 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="10"
                      id="right-button-10"
                      tabindex="-1"
                    >
                      10
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-10"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-10"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-8 left-content-8 right-button-11 right-content-11"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="8"></td>
                  <td class="gr-diff left lineNum" data-value="8">
                    <button
                      aria-label="8 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="8"
                      id="left-button-8"
                      tabindex="-1"
                    >
                      8
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-8"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-8"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="11">
                    <button
                      aria-label="11 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="11"
                      id="right-button-11"
                      tabindex="-1"
                    >
                      11
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-11"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-11"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-9 left-content-9 right-button-12 right-content-12"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="9"></td>
                  <td class="gr-diff left lineNum" data-value="9">
                    <button
                      aria-label="9 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="9"
                      id="left-button-9"
                      tabindex="-1"
                    >
                      9
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-9"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-9"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="12">
                    <button
                      aria-label="12 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="12"
                      id="right-button-12"
                      tabindex="-1"
                    >
                      12
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-12"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-12"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="left-button-10 left-content-10"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="10"></td>
                  <td class="gr-diff left lineNum" data-value="10">
                    <button
                      aria-label="10 removed"
                      class="gr-diff left lineNumButton"
                      data-value="10"
                      id="left-button-10"
                      tabindex="-1"
                    >
                      10
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-10"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-10"> </slot>
                    </div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-11 left-content-11"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="11"></td>
                  <td class="gr-diff left lineNum" data-value="11">
                    <button
                      aria-label="11 removed"
                      class="gr-diff left lineNumButton"
                      data-value="11"
                      id="left-button-11"
                      tabindex="-1"
                    >
                      11
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-11"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-11"> </slot>
                    </div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-12 left-content-12"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="12"></td>
                  <td class="gr-diff left lineNum" data-value="12">
                    <button
                      aria-label="12 removed"
                      class="gr-diff left lineNumButton"
                      data-value="12"
                      id="left-button-12"
                      tabindex="-1"
                    >
                      12
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-12"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-12"> </slot>
                    </div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-13 left-content-13"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="13"></td>
                  <td class="gr-diff left lineNum" data-value="13">
                    <button
                      aria-label="13 removed"
                      class="gr-diff left lineNumButton"
                      data-value="13"
                      id="left-button-13"
                      tabindex="-1"
                    >
                      13
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-13"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-13"> </slot>
                    </div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff ignoredWhitespaceOnly section">
                <tr
                  aria-labelledby="left-button-14 left-content-14 right-button-13 right-content-13"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="14"></td>
                  <td class="gr-diff left lineNum" data-value="14">
                    <button
                      aria-label="14 removed"
                      class="gr-diff left lineNumButton"
                      data-value="14"
                      id="left-button-14"
                      tabindex="-1"
                    >
                      14
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-14"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-14"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="13">
                    <button
                      aria-label="13 added"
                      class="gr-diff lineNumButton right"
                      data-value="13"
                      id="right-button-13"
                      tabindex="-1"
                    >
                      13
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-13"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-13"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-15 left-content-15 right-button-14 right-content-14"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="15"></td>
                  <td class="gr-diff left lineNum" data-value="15">
                    <button
                      aria-label="15 removed"
                      class="gr-diff left lineNumButton"
                      data-value="15"
                      id="left-button-15"
                      tabindex="-1"
                    >
                      15
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-15"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-15"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="14">
                    <button
                      aria-label="14 added"
                      class="gr-diff lineNumButton right"
                      data-value="14"
                      id="right-button-14"
                      tabindex="-1"
                    >
                      14
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-14"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-14"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section">
                <tr
                  aria-labelledby="left-button-16 left-content-16 right-button-15 right-content-15"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="16"></td>
                  <td class="gr-diff left lineNum" data-value="16">
                    <button
                      aria-label="16 removed"
                      class="gr-diff left lineNumButton"
                      data-value="16"
                      id="left-button-16"
                      tabindex="-1"
                    >
                      16
                    </button>
                  </td>
                  <td class="gr-diff left remove sign">-</td>
                  <td class="content gr-diff left remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-16"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-16"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="15">
                    <button
                      aria-label="15 added"
                      class="gr-diff lineNumButton right"
                      data-value="15"
                      id="right-button-15"
                      tabindex="-1"
                    >
                      15
                    </button>
                  </td>
                  <td class="add gr-diff right sign">+</td>
                  <td class="add content gr-diff right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-15"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-15"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-17 left-content-17 right-button-16 right-content-16"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="17"></td>
                  <td class="gr-diff left lineNum" data-value="17">
                    <button
                      aria-label="17 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="17"
                      id="left-button-17"
                      tabindex="-1"
                    >
                      17
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-17"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-17"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="16">
                    <button
                      aria-label="16 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="16"
                      id="right-button-16"
                      tabindex="-1"
                    >
                      16
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-16"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-16"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-18 left-content-18 right-button-17 right-content-17"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="18"></td>
                  <td class="gr-diff left lineNum" data-value="18">
                    <button
                      aria-label="18 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="18"
                      id="left-button-18"
                      tabindex="-1"
                    >
                      18
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-18"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-18"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="17">
                    <button
                      aria-label="17 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="17"
                      id="right-button-17"
                      tabindex="-1"
                    >
                      17
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-17"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-17"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-19 left-content-19 right-button-18 right-content-18"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="19"></td>
                  <td class="gr-diff left lineNum" data-value="19">
                    <button
                      aria-label="19 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="19"
                      id="left-button-19"
                      tabindex="-1"
                    >
                      19
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-19"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-19"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="18">
                    <button
                      aria-label="18 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="18"
                      id="right-button-18"
                      tabindex="-1"
                    >
                      18
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-18"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-18"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="contextControl gr-diff section">
                <tr
                  class="above contextBackground gr-diff side-by-side"
                  left-type="contextControl"
                  right-type="contextControl"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                </tr>
                <tr class="dividerRow gr-diff show-both">
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff"></td>
                  <td class="dividerCell gr-diff" colspan="5">
                    <gr-context-controls class="gr-diff" showconfig="both">
                    </gr-context-controls>
                  </td>
                </tr>
                <tr
                  class="below contextBackground gr-diff side-by-side"
                  left-type="contextControl"
                  right-type="contextControl"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-38 left-content-38 right-button-37 right-content-37"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="38"></td>
                  <td class="gr-diff left lineNum" data-value="38">
                    <button
                      aria-label="38 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="38"
                      id="left-button-38"
                      tabindex="-1"
                    >
                      38
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-38"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-38"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="37">
                    <button
                      aria-label="37 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="37"
                      id="right-button-37"
                      tabindex="-1"
                    >
                      37
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-37"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-37"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-39 left-content-39 right-button-38 right-content-38"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="39"></td>
                  <td class="gr-diff left lineNum" data-value="39">
                    <button
                      aria-label="39 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="39"
                      id="left-button-39"
                      tabindex="-1"
                    >
                      39
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-39"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-39"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="38">
                    <button
                      aria-label="38 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="38"
                      id="right-button-38"
                      tabindex="-1"
                    >
                      38
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-38"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-38"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-40 left-content-40 right-button-39 right-content-39"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="40"></td>
                  <td class="gr-diff left lineNum" data-value="40">
                    <button
                      aria-label="40 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="40"
                      id="left-button-40"
                      tabindex="-1"
                    >
                      40
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-40"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-40"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="39">
                    <button
                      aria-label="39 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="39"
                      id="right-button-39"
                      tabindex="-1"
                    >
                      39
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-39"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-39"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="right-button-40 right-content-40"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="40">
                    <button
                      aria-label="40 added"
                      class="gr-diff lineNumButton right"
                      data-value="40"
                      id="right-button-40"
                      tabindex="-1"
                    >
                      40
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-40"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-40"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-41 right-content-41"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="41">
                    <button
                      aria-label="41 added"
                      class="gr-diff lineNumButton right"
                      data-value="41"
                      id="right-button-41"
                      tabindex="-1"
                    >
                      41
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-41"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-41"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-42 right-content-42"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="42">
                    <button
                      aria-label="42 added"
                      class="gr-diff lineNumButton right"
                      data-value="42"
                      id="right-button-42"
                      tabindex="-1"
                    >
                      42
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-42"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-42"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-43 right-content-43"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="43">
                    <button
                      aria-label="43 added"
                      class="gr-diff lineNumButton right"
                      data-value="43"
                      id="right-button-43"
                      tabindex="-1"
                    >
                      43
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-43"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-43"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-41 left-content-41 right-button-44 right-content-44"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="41"></td>
                  <td class="gr-diff left lineNum" data-value="41">
                    <button
                      aria-label="41 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="41"
                      id="left-button-41"
                      tabindex="-1"
                    >
                      41
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-41"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-41"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="44">
                    <button
                      aria-label="44 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="44"
                      id="right-button-44"
                      tabindex="-1"
                    >
                      44
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-44"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-44"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-42 left-content-42 right-button-45 right-content-45"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="42"></td>
                  <td class="gr-diff left lineNum" data-value="42">
                    <button
                      aria-label="42 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="42"
                      id="left-button-42"
                      tabindex="-1"
                    >
                      42
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-42"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-42"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="45">
                    <button
                      aria-label="45 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="45"
                      id="right-button-45"
                      tabindex="-1"
                    >
                      45
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-45"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-45"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-43 left-content-43 right-button-46 right-content-46"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="43"></td>
                  <td class="gr-diff left lineNum" data-value="43">
                    <button
                      aria-label="43 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="43"
                      id="left-button-43"
                      tabindex="-1"
                    >
                      43
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-43"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-43"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="46">
                    <button
                      aria-label="46 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="46"
                      id="right-button-46"
                      tabindex="-1"
                    >
                      46
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-46"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-46"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-44 left-content-44 right-button-47 right-content-47"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="44"></td>
                  <td class="gr-diff left lineNum" data-value="44">
                    <button
                      aria-label="44 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="44"
                      id="left-button-44"
                      tabindex="-1"
                    >
                      44
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-44"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-44"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="47">
                    <button
                      aria-label="47 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="47"
                      id="right-button-47"
                      tabindex="-1"
                    >
                      47
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-47"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-47"> </slot>
                    </div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-45 left-content-45 right-button-48 right-content-48"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="45"></td>
                  <td class="gr-diff left lineNum" data-value="45">
                    <button
                      aria-label="45 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="45"
                      id="left-button-45"
                      tabindex="-1"
                    >
                      45
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-45"
                    ></div>
                    <div class="thread-group" data-side="left">
                      <slot name="left-45"> </slot>
                    </div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="48">
                    <button
                      aria-label="48 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="48"
                      id="right-button-48"
                      tabindex="-1"
                    >
                      48
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-48"
                    ></div>
                    <div class="thread-group" data-side="right">
                      <slot name="right-48"> </slot>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        `,
        {
          ignoreTags: [
            'gr-context-controls-section',
            'gr-diff-section',
            'gr-diff-row',
            'gr-diff-text',
            'gr-legacy-text',
          ],
        }
      );
    };
  });

  suite('selectionchange event handling', () => {
    let handleSelectionChangeStub: sinon.SinonSpy;

    const emulateSelection = function () {
      document.dispatchEvent(new CustomEvent('selectionchange'));
    };

    setup(async () => {
      handleSelectionChangeStub = sinon.spy(
        element.highlights,
        'handleSelectionChange'
      );
    });

    test('enabled if logged in', async () => {
      element.loggedIn = true;
      await element.updateComplete;
      emulateSelection();
      assert.isTrue(handleSelectionChangeStub.called);
    });

    test('ignored if logged out', async () => {
      element.loggedIn = false;
      await element.updateComplete;
      emulateSelection();
      assert.isFalse(handleSelectionChangeStub.called);
    });
  });

  test('cancel', () => {
    const cleanupStub = sinon.stub(element.diffBuilder, 'cleanup');
    element.cancel();
    assert.isTrue(cleanupStub.calledOnce);
  });

  test('line limit with line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: true};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '80ch');
  });

  test('line limit without line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: false};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '-1px');
  });

  suite('FULL_RESPONSIVE mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'FULL_RESPONSIVE'};
      await element.updateComplete;
    });

    test('line limit is based on line_length', async () => {
      element.prefs = {...element.prefs!, line_length: 100};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--line-limit-marker', element),
        '100ch'
      );
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });
  });

  suite('SHRINK_ONLY mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'SHRINK_ONLY'};
      await element.updateComplete;
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });

    test('max-width considers two content columns in side-by-side', async () => {
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers one content column in unified', async () => {
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(1 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers font-size', async () => {
      element.prefs = {...element.prefs!, font_size: 13};
      await element.updateComplete;
      // Each line number column: 4 * 13 = 52px
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 52px + 0ch + 1px + 2px)'
      );
    });

    test('sign cols are considered if show_sign_col is true', async () => {
      element.renderPrefs = {...element.renderPrefs, show_sign_col: true};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 2ch + 1px + 2px)'
      );
    });
  });

  suite('not logged in', () => {
    setup(async () => {
      element.loggedIn = false;
      await element.updateComplete;
    });

    test('toggleLeftDiff', () => {
      element.toggleLeftDiff();
      assert.isTrue(element.classList.contains('no-left'));
      element.toggleLeftDiff();
      assert.isFalse(element.classList.contains('no-left'));
    });

    test('view does not start with displayLine classList', () => {
      const container = queryAndAssert(element, '.diffContainer');
      assert.isFalse(container.classList.contains('displayLine'));
    });

    test('displayLine class added when displayLine is true', async () => {
      element.displayLine = true;
      await element.updateComplete;
      const container = queryAndAssert(element, '.diffContainer');
      assert.isTrue(container.classList.contains('displayLine'));
    });

    suite('image diffs', () => {
      let mockFile1: ImageInfo;
      let mockFile2: ImageInfo;
      setup(() => {
        mockFile1 = {
          body:
            'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
            'wsAAAAAAAAAAAAAAAAA/w==',
          type: 'image/bmp',
        };
        mockFile2 = {
          body:
            'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
            'wsAAAAAAAAAAAAA/////w==',
          type: 'image/bmp',
        };

        element.isImageDiff = true;
        element.prefs = {
          context: 10,
          cursor_blink_rate: 0,
          font_size: 12,
          ignore_whitespace: 'IGNORE_NONE',
          line_length: 100,
          line_wrapping: false,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
        };
      });

      test('renders image diffs with same file name', async () => {
        element.baseImage = mockFile1;
        element.revisionImage = mockFile2;
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        await waitForEventOnce(element, 'render');

        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        // Left image rendered with the parent commit's version of the file.
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = queryAndAssert(diffTable, 'td.left img');
        const leftLabel = queryAndAssert(diffTable, 'td.left label');
        const leftLabelContent = queryAndAssert(leftLabel, '.label');
        const leftLabelName = query(leftLabel, '.name');

        const rightImage = queryAndAssert(diffTable, 'td.right img');
        const rightLabel = queryAndAssert(diffTable, 'td.right label');
        const rightLabelContent = queryAndAssert(rightLabel, '.label');
        const rightLabelName = query(rightLabel, '.name');

        assert.isNotOk(rightLabelName);
        assert.isNotOk(leftLabelName);

        assert.equal(
          leftImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile1.body
        );
        assert.isTrue(leftLabelContent.textContent?.includes('image/bmp'));

        assert.equal(
          rightImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile2.body
        );
        assert.isTrue(rightLabelContent.textContent?.includes('image/bmp'));
      });

      test('renders image diffs with a different file name', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot2.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot2.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        element.baseImage = mockFile1;
        element.baseImage._name = mockDiff.meta_a!.name;
        element.revisionImage = mockFile2;
        element.revisionImage._name = mockDiff.meta_b!.name;
        element.diff = mockDiff;
        await waitForEventOnce(element, 'render');

        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        // Left image rendered with the parent commit's version of the file.
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = queryAndAssert(diffTable, 'td.left img');
        const leftLabel = queryAndAssert(diffTable, 'td.left label');
        const leftLabelContent = queryAndAssert(leftLabel, '.label');
        const leftLabelName = queryAndAssert(leftLabel, '.name');

        const rightImage = queryAndAssert(diffTable, 'td.right img');
        const rightLabel = queryAndAssert(diffTable, 'td.right label');
        const rightLabelContent = queryAndAssert(rightLabel, '.label');
        const rightLabelName = queryAndAssert(rightLabel, '.name');

        assert.isOk(rightLabelName);
        assert.isOk(leftLabelName);
        assert.equal(leftLabelName.textContent, mockDiff.meta_a?.name);
        assert.equal(rightLabelName.textContent, mockDiff.meta_b?.name);

        assert.isOk(leftImage);
        assert.equal(
          leftImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile1.body
        );
        assert.isTrue(leftLabelContent.textContent?.includes('image/bmp'));

        assert.isOk(rightImage);
        assert.equal(
          rightImage.getAttribute('src'),
          'data:image/bmp;base64,' + mockFile2.body
        );
        assert.isTrue(rightLabelContent.textContent?.includes('image/bmp'));
      });

      test('renders added image', async () => {
        const mockDiff: DiffInfo = {
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'ADDED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 0000000..f9c2f2c 100644',
            '--- /dev/null',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        const promise = mockPromise();
        function rendered() {
          promise.resolve();
        }
        element.addEventListener('render', rendered);

        element.revisionImage = mockFile2;
        element.diff = mockDiff;
        await promise;
        element.removeEventListener('render', rendered);
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = query(diffTable, 'td.left img');
        assert.isNotOk(leftImage);
        queryAndAssert(diffTable, 'td.right img');
      });

      test('renders removed image', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        const promise = mockPromise();
        function rendered() {
          promise.resolve();
        }
        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.diff = mockDiff;
        await promise;
        element.removeEventListener('render', rendered);
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);

        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        queryAndAssert(diffTable, 'td.left img');
        const rightImage = query(diffTable, 'td.right img');
        assert.isNotOk(rightImage);
      });

      test('does not render disallowed image type', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {
            name: 'carrot.jpg',
            content_type: 'image/jpeg-evil',
            lines: 560,
          },
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        mockFile1.type = 'image/jpeg-evil';

        const promise = mockPromise();
        function rendered() {
          promise.resolve();
        }
        element.addEventListener('render', rendered);

        element.baseImage = mockFile1;
        element.diff = mockDiff;
        await promise;
        element.removeEventListener('render', rendered);
        // Recognizes that it should be an image diff.
        assert.isTrue(element.isImageDiff);
        assert.instanceOf(element.diffBuilder.builder, GrDiffBuilderImage);
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        const leftImage = query(diffTable, 'td.left img');
        assert.isNotOk(leftImage);
      });
    });

    test('handleTap lineNum', async () => {
      const addDraftStub = sinon.stub(element, 'addDraftAtLine');
      const el = document.createElement('div');
      el.className = 'lineNum';
      const promise = mockPromise();
      el.addEventListener('click', e => {
        element.handleTap(e);
        assert.isTrue(addDraftStub.called);
        assert.equal(addDraftStub.lastCall.args[0], el);
        promise.resolve();
      });
      el.click();
      await promise;
    });

    test('handleTap content', async () => {
      const content = document.createElement('div');
      const lineEl = document.createElement('div');
      lineEl.className = 'lineNum';
      const row = document.createElement('div');
      row.appendChild(lineEl);
      row.appendChild(content);

      const selectStub = sinon.stub(element, 'selectLine');

      content.className = 'content';
      const promise = mockPromise();
      content.addEventListener('click', e => {
        element.handleTap(e);
        assert.isTrue(selectStub.called);
        assert.equal(selectStub.lastCall.args[0], lineEl);
        promise.resolve();
      });
      content.click();
      await promise;
    });

    suite('getCursorStops', () => {
      async function setupDiff() {
        element.diff = createDiff();
        element.prefs = {
          context: 10,
          tab_size: 8,
          font_size: 12,
          line_length: 100,
          cursor_blink_rate: 0,
          line_wrapping: false,

          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          ignore_whitespace: 'IGNORE_NONE',
        };
        await element.updateComplete;
        element.renderDiffTable();
      }

      test('returns [] when hidden and noAutoRender', async () => {
        element.noAutoRender = true;
        await setupDiff();
        element.loading = false;
        await element.updateComplete;
        element.hidden = true;
        await element.updateComplete;
        assert.equal(element.getCursorStops().length, 0);
      });

      test('returns one stop per line and one for the file row', async () => {
        await setupDiff();
        element.loading = false;
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        assert.equal(element.getCursorStops().length, ROWS + FILE_ROW);
      });

      test('returns an additional AbortStop when still loading', async () => {
        await setupDiff();
        element.loading = true;
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        const actual = element.getCursorStops();
        assert.equal(actual.length, ROWS + FILE_ROW + 1);
        assert.isTrue(actual[actual.length - 1] instanceof AbortStop);
      });
    });
  });

  suite('logged in', async () => {
    let fakeLineEl: HTMLElement;
    setup(async () => {
      element.loggedIn = true;

      fakeLineEl = {
        getAttribute: sinon.stub().returns(42),
        classList: {
          contains: sinon.stub().returns(true),
        },
      } as unknown as HTMLElement;
      await element.updateComplete;
    });

    test('addDraftAtLine', () => {
      sinon.stub(element, 'selectLine');
      const createCommentStub = sinon.stub(element, 'createComment');
      element.addDraftAtLine(fakeLineEl);
      assert.isTrue(createCommentStub.calledWithExactly(fakeLineEl, 42));
    });

    test('adds long range comment hint', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(threadEl);

      const hint = await waitQueryAndAssert<GrRangedCommentHint>(
        element,
        'gr-ranged-comment-hint'
      );
      assert.deepEqual(hint.range, range);
    });

    test('no duplicate range hint for same thread', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const firstHint = document.createElement('gr-ranged-comment-hint');
      firstHint.range = range;
      firstHint.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(firstHint);
      element.appendChild(threadEl);

      assert.equal(
        element.querySelectorAll('gr-ranged-comment-hint').length,
        1
      );
    });

    test('removes long range comment hint when comment is discarded', async () => {
      const range = {
        start_line: 1,
        end_line: 7,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          ab: Array(8).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(threadEl);
      await waitUntil(() => element.commentRanges.length === 1);

      threadEl.remove();
      await waitUntil(() => element.commentRanges.length === 0);

      assert.isEmpty(element.querySelectorAll('gr-ranged-comment-hint'));
    });

    suite('change in preferences', () => {
      setup(async () => {
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          diff_header: [],
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          content: [{skip: 66}],
        };
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
      });

      test('change in preferences re-renders diff', async () => {
        const stub = sinon.stub(element, 'renderDiffTable');
        element.prefs = {
          ...MINIMAL_PREFS,
        };
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
        assert.isTrue(stub.called);
      });

      test('adding/removing property in preferences re-renders diff', async () => {
        const stub = sinon.stub(element, 'renderDiffTable');
        const newPrefs1: DiffPreferencesInfo = {
          ...MINIMAL_PREFS,
          line_wrapping: true,
        };
        element.prefs = newPrefs1;
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
        assert.isTrue(stub.called);
        stub.reset();

        const newPrefs2 = {...newPrefs1};
        delete newPrefs2.line_wrapping;
        element.prefs = newPrefs2;
        await element.updateComplete;
        await element.renderDiffTableTask?.flush();
        assert.isTrue(stub.called);
      });

      test(
        'change in preferences does not re-renders diff with ' +
          'noRenderOnPrefsChange',
        async () => {
          const stub = sinon.stub(element, 'renderDiffTable');
          element.noRenderOnPrefsChange = true;
          element.prefs = {
            ...MINIMAL_PREFS,
            context: 12,
          };
          await element.updateComplete;
          await element.renderDiffTableTask?.flush();
          assert.isFalse(stub.called);
        }
      );
    });
  });

  suite('diff header', () => {
    setup(async () => {
      element.diff = {
        meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
        meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
        diff_header: [],
        intraline_status: 'OK',
        change_type: 'MODIFIED',
        content: [{skip: 66}],
      };
      await element.updateComplete;
    });

    test('hidden', async () => {
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('diff --git a/test.jpg b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('index 2adc47d..f9c2f2c 100644');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('--- a/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('+++ b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('test');
      assert.equal(element.computeDiffHeaderItems().length, 1);
      element.requestUpdate('diff');
      await element.updateComplete;

      const header = queryAndAssert(element, '#diffHeader');
      assert.equal(header.textContent?.trim(), 'test');
    });

    test('binary files', () => {
      element.diff!.binary = true;
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('diff --git a/test.jpg b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('test');
      assert.equal(element.computeDiffHeaderItems().length, 1);
      element.diff?.diff_header?.push('Binary files differ');
      assert.equal(element.computeDiffHeaderItems().length, 1);
    });
  });

  suite('safety and bypass', () => {
    let renderStub: sinon.SinonStub;

    setup(async () => {
      renderStub = sinon.stub(element.diffBuilder, 'render').callsFake(() => {
        assertIsDefined(element.diffTable);
        const diffTable = element.diffTable;
        diffTable.dispatchEvent(
          new CustomEvent('render', {bubbles: true, composed: true})
        );
        return Promise.resolve();
      });
      sinon.stub(element, 'getDiffLength').returns(10000);
      element.diff = createDiff();
      element.noRenderOnPrefsChange = true;
      await element.updateComplete;
    });

    test('large render w/ context = 10', async () => {
      element.prefs = {...MINIMAL_PREFS, context: 10};
      const promise = mockPromise();
      function rendered() {
        assert.isTrue(renderStub.called);
        assert.isFalse(element.showWarning);
        promise.resolve();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element.renderDiffTable();
      await promise;
    });

    test('large render w/ whole file and bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: -1};
      element.safetyBypass = 10;
      const promise = mockPromise();
      function rendered() {
        assert.isTrue(renderStub.called);
        assert.isFalse(element.showWarning);
        promise.resolve();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element.renderDiffTable();
      await promise;
    });

    test('large render w/ whole file and no bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: -1};
      const promise = mockPromise();
      function rendered() {
        assert.isFalse(renderStub.called);
        assert.isTrue(element.showWarning);
        promise.resolve();
        element.removeEventListener('render', rendered);
      }
      element.addEventListener('render', rendered);
      element.renderDiffTable();
      await promise;
    });

    test('toggles expand context using bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: 3};

      element.toggleAllContext();
      element.renderDiffTable();
      await element.updateComplete;

      assert.equal(element.prefs.context, 3);
      assert.equal(element.safetyBypass, -1);
      assert.equal(element.diffBuilder.prefs.context, -1);
    });

    test('toggles collapse context from bypass', async () => {
      element.prefs = {...MINIMAL_PREFS, context: 3};
      element.safetyBypass = -1;

      element.toggleAllContext();
      element.renderDiffTable();
      await element.updateComplete;

      assert.equal(element.prefs.context, 3);
      assert.isNull(element.safetyBypass);
      assert.equal(element.diffBuilder.prefs.context, 3);
    });

    test('toggles collapse context from pref using default', async () => {
      element.prefs = {...MINIMAL_PREFS, context: -1};

      element.toggleAllContext();
      element.renderDiffTable();
      await element.updateComplete;

      assert.equal(element.prefs.context, -1);
      assert.equal(element.safetyBypass, 10);
      assert.equal(element.diffBuilder.prefs.context, 10);
    });
  });

  suite('blame', () => {
    test('unsetting', async () => {
      element.blame = [];
      const setBlameSpy = sinon.spy(element.diffBuilder, 'setBlame');
      element.classList.add('showBlame');
      element.blame = null;
      await element.updateComplete;
      assert.isTrue(setBlameSpy.calledWithExactly(null));
      assert.isFalse(element.classList.contains('showBlame'));
    });

    test('setting', async () => {
      element.blame = [
        {
          author: 'test-author',
          time: 12345,
          commit_msg: '',
          id: 'commit id',
          ranges: [{start: 1, end: 2}],
        },
      ];
      await element.updateComplete;
      assert.isTrue(element.classList.contains('showBlame'));
    });
  });

  suite('trailing newline warnings', () => {
    const NO_NEWLINE_LEFT = 'No newline at end of left file.';
    const NO_NEWLINE_RIGHT = 'No newline at end of right file.';

    const getWarning = (element: GrDiff) => {
      const warningElement = query(element, '.newlineWarning');
      return warningElement?.textContent ?? '';
    };

    setup(async () => {
      element.showNewlineWarningLeft = false;
      element.showNewlineWarningRight = false;
      await element.updateComplete;
    });

    test('shows combined warning if both sides set to warn', async () => {
      element.showNewlineWarningLeft = true;
      element.showNewlineWarningRight = true;
      await element.updateComplete;
      assert.include(
        getWarning(element),
        NO_NEWLINE_LEFT + ' \u2014 ' + NO_NEWLINE_RIGHT
      ); // \u2014 - '—'
    });

    suite('showNewlineWarningLeft', () => {
      test('show warning if true', async () => {
        element.showNewlineWarningLeft = true;
        await element.updateComplete;
        assert.include(getWarning(element), NO_NEWLINE_LEFT);
      });

      test('hide warning if false', async () => {
        element.showNewlineWarningLeft = false;
        await element.updateComplete;
        assert.notInclude(getWarning(element), NO_NEWLINE_LEFT);
      });
    });

    suite('showNewlineWarningRight', () => {
      test('show warning if true', async () => {
        element.showNewlineWarningRight = true;
        await element.updateComplete;
        assert.include(getWarning(element), NO_NEWLINE_RIGHT);
      });

      test('hide warning if false', async () => {
        element.showNewlineWarningRight = false;
        await element.updateComplete;
        assert.notInclude(getWarning(element), NO_NEWLINE_RIGHT);
      });
    });
  });

  suite('key locations', () => {
    let renderStub: sinon.SinonStub;

    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      renderStub = sinon.stub(element.diffBuilder, 'render');
      await element.updateComplete;
    });

    test('lineOfInterest is a key location', () => {
      element.lineOfInterest = {lineNum: 789, side: Side.LEFT};
      element.renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {789: true},
        right: {},
      });
    });

    test('line comments are key locations', async () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '3');
      element.appendChild(threadEl);
      await element.updateComplete;

      element.renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {},
        right: {3: true},
      });
    });

    test('file comments are key locations', async () => {
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'left');
      element.appendChild(threadEl);
      await element.updateComplete;

      element.renderDiffTable();
      assert.isTrue(renderStub.called);
      assert.deepEqual(renderStub.lastCall.args[0], {
        left: {FILE: true},
        right: {},
      });
    });
  });
  const setupSampleDiff = async function (params: {
    content: DiffContent[];
    ignore_whitespace?: IgnoreWhitespaceType;
    binary?: boolean;
  }) {
    const {ignore_whitespace, content} = params;
    // binary can't be undefined, use false if not set
    const binary = params.binary || false;
    element.prefs = {
      ignore_whitespace: ignore_whitespace || 'IGNORE_ALL',
      context: 10,
      cursor_blink_rate: 0,
      font_size: 12,

      line_length: 100,
      line_wrapping: false,
      show_line_endings: true,
      show_tabs: true,
      show_whitespace_errors: true,
      syntax_highlighting: true,
      tab_size: 8,
    };
    element.diff = {
      intraline_status: 'OK',
      change_type: 'MODIFIED',
      diff_header: [
        'diff --git a/carrot.js b/carrot.js',
        'index 2adc47d..f9c2f2c 100644',
        '--- a/carrot.js',
        '+++ b/carrot.jjs',
        'file differ',
      ],
      content,
      binary,
    };
    await element.updateComplete;
    await element.renderDiffTableTask;
  };

  test('clear diff table content as soon as diff changes', async () => {
    const content = [
      {
        a: ['all work and no play make andybons a dull boy'],
      },
      {
        b: ['Non eram nescius, Brute, cum, quae summis ingeniis '],
      },
    ];
    function assertDiffTableWithContent() {
      assertIsDefined(element.diffTable);
      const diffTable = element.diffTable;
      assert.isTrue(diffTable.innerText.includes(content[0].a?.[0] ?? ''));
    }
    await setupSampleDiff({content});
    assertDiffTableWithContent();
    element.diff = {...element.diff!};
    await element.updateComplete;
    // immediately cleaned up
    assertIsDefined(element.diffTable);
    const diffTable = element.diffTable;
    assert.equal(diffTable.innerHTML, '');
    element.renderDiffTable();
    await element.updateComplete;
    // rendered again
    assertDiffTableWithContent();
  });

  suite('selection test', () => {
    test('user-select set correctly on side-by-side view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});
      await waitEventLoop();

      const diffLine = queryAll<HTMLElement>(element, '.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });

    test('user-select set correctly on unified view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;
      const diffLine = queryAll<HTMLElement>(element, '.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });
  });

  suite('whitespace changes only message', () => {
    test('show the message if ignore_whitespace is criteria matches', async () => {
      await setupSampleDiff({content: [{skip: 100}]});
      element.loading = false;
      assert.isTrue(element.showNoChangeMessage());
    });

    test('do not show the message for binary files', async () => {
      await setupSampleDiff({content: [{skip: 100}], binary: true});
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show the message if still loading', async () => {
      await setupSampleDiff({content: [{skip: 100}]});
      element.loading = true;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show the message if contains valid changes', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});
      element.loading = false;
      assert.equal(element.diffLength, 3);
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show message if ignore whitespace is disabled', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({ignore_whitespace: 'IGNORE_NONE', content});
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });
  });

  test('getDiffLength', () => {
    const diff = createDiff();
    assert.equal(element.getDiffLength(diff), 52);
  });
});
