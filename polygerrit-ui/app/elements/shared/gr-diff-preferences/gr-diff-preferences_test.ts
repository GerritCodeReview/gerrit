/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import './gr-diff-preferences';
import {GrDiffPreferences} from './gr-diff-preferences';
import {stubRestApi} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../types/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {IronInputElement} from '@polymer/iron-input';
import {GrSelect} from '../gr-select/gr-select';

const basicFixture = fixtureFromElement('gr-diff-preferences');

suite('gr-diff-preferences tests', () => {
  let element: GrDiffPreferences;

  let diffPreferences: DiffPreferencesInfo;

  function valueOf(title: string, id: string) {
    const sections = element.root?.querySelectorAll(`#${id} section`) ?? [];
    let titleEl;
    for (let i = 0; i < sections.length; i++) {
      titleEl = sections[i].querySelector('.title');
      if (titleEl?.textContent?.trim() === title) {
        const el = sections[i].querySelector('.value');
        if (el) return el;
      }
    }
    assert.fail(`element with title ${title} not found`);
  }

  setup(async () => {
    diffPreferences = createDefaultDiffPrefs();

    stubRestApi('getDiffPreferences').returns(Promise.resolve(diffPreferences));

    element = basicFixture.instantiate();

    await flush();
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(`<div
      class="gr-form-styles"
      id="diffPreferences"
    >
    <section>
      <label class="title" for="contextLineSelect">Context</label>
      <span class="value">
        <gr-select id="contextSelect">
          <select id="contextLineSelect">
            <option value="3">3 lines</option>
            <option value="10">10 lines</option>
            <option value="25">25 lines</option>
            <option value="50">50 lines</option>
            <option value="75">75 lines</option>
            <option value="100">100 lines</option>
            <option value="-1">Whole file</option>
          </select>
        </gr-select>
      </span>
    </section>
    <section>
      <label class="title" for="lineWrappingInput">Fit to screen</label>
      <span class="value">
        <input id="lineWrappingInput" type="checkbox">
      </span>
    </section>
    <section>
      <label class="title" for="columnsInput">Diff width</label>
      <span class="value">
        <iron-input allowed-pattern="[0-9]">
          <input id="columnsInput" type="number">
        </iron-input>
      </span>
    </section>
    <section>
      <label class="title" for="tabSizeInput">Tab width</label>
      <span class="value">
        <iron-input allowed-pattern="[0-9]">
          <input id="tabSizeInput" type="number">
        </iron-input>
      </span>
    </section>
    <section>
      <label class="title" for="fontSizeInput">Font size</label>
      <span class="value">
        <iron-input allowed-pattern="[0-9]">
          <input id="fontSizeInput" type="number">
        </iron-input>
      </span>
    </section>
    <section>
      <label class="title" for="showTabsInput">Show tabs</label>
      <span class="value">
        <input id="showTabsInput" type="checkbox">
      </span>
    </section>
    <section>
      <label class="title" for="showTrailingWhitespaceInput">
        Show trailing whitespace
      </label>
      <span class="value">
        <input id="showTrailingWhitespaceInput" type="checkbox">
      </span>
    </section>
    <section>
      <label class="title" for="syntaxHighlightInput">
        Syntax highlighting
      </label>
      <span class="value">
        <input id="syntaxHighlightInput" type="checkbox">
      </span>
    </section>
    <section>
      <label class="title" for="automaticReviewInput">
        Automatically mark viewed files reviewed
      </label>
      <span class="value">
        <input id="automaticReviewInput" type="checkbox">
      </span>
    </section>
    <section>
      <div class="pref">
        <label class="title" for="ignoreWhiteSpace">
          Ignore Whitespace
        </label>
        <span class="value">
          <gr-select>
            <select id="ignoreWhiteSpace">
              <option value="IGNORE_NONE">None</option>
              <option value="IGNORE_TRAILING">Trailing</option>
              <option value="IGNORE_LEADING_AND_TRAILING">
                Leading & trailing
              </option>
              <option value="IGNORE_ALL">All</option>
            </select>
          </gr-select>
        </span>
      </div>
    </section>
  </div>`);
  });

  test('renders preferences', () => {
    // Rendered with the expected preferences selected.
    const contextInput = valueOf('Context', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(contextInput.bindValue, `${diffPreferences.context}`);

    const lineWrappingInput = valueOf('Fit to screen', 'diffPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(lineWrappingInput.checked, diffPreferences.line_wrapping);

    const lineLengthInput = valueOf('Diff width', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(lineLengthInput.bindValue, `${diffPreferences.line_length}`);

    const tabSizeInput = valueOf('Tab width', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(tabSizeInput.bindValue, `${diffPreferences.tab_size}`);

    const fontSizeInput = valueOf('Font size', 'diffPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(fontSizeInput.bindValue, `${diffPreferences.font_size}`);

    const showTabsInput = valueOf('Show tabs', 'diffPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(showTabsInput.checked, diffPreferences.show_tabs);

    const showWhitespaceErrorsInput = valueOf(
      'Show trailing whitespace',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    assert.equal(
      showWhitespaceErrorsInput.checked,
      diffPreferences.show_whitespace_errors
    );

    const syntaxHighlightingInput = valueOf(
      'Syntax highlighting',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    assert.equal(
      syntaxHighlightingInput.checked,
      diffPreferences.syntax_highlighting
    );

    const manualReviewInput = valueOf(
      'Automatically mark viewed files reviewed',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    assert.equal(manualReviewInput.checked, !diffPreferences.manual_review);

    const ignoreWhitespaceInput = valueOf(
      'Ignore Whitespace',
      'diffPreferences'
    ).firstElementChild as GrSelect;
    assert.equal(
      ignoreWhitespaceInput.bindValue,
      diffPreferences.ignore_whitespace
    );

    assert.isFalse(element.hasUnsavedChanges);
  });

  test('save changes', async () => {
    const showTrailingWhitespaceCheckbox = valueOf(
      'Show trailing whitespace',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    showTrailingWhitespaceCheckbox.checked = false;
    element._handleShowTrailingWhitespaceTap();

    assert.isTrue(element.hasUnsavedChanges);

    // Save the change.
    await element.save();
    assert.isFalse(element.hasUnsavedChanges);
  });
});
