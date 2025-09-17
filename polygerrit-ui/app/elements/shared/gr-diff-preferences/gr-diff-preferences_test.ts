/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-preferences';
import {GrDiffPreferences} from './gr-diff-preferences';
import {
  makePrefixedJSON,
  queryAll,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {DiffPreferencesInfo} from '../../../types/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {assert, fixture, html} from '@open-wc/testing';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';
import {MdOutlinedSelect} from '@material/web/select/outlined-select';

suite('gr-diff-preferences tests', () => {
  let element: GrDiffPreferences;

  let diffPreferences: DiffPreferencesInfo;

  function valueOf(title: string, id: string) {
    const sections = queryAll(element, `#${id} section`) ?? [];
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

    element = await fixture(html`<gr-diff-preferences></gr-diff-preferences>`);

    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div class="gr-form-styles" id="diffPreferences">
        <section>
          <label class="title" for="contextLineSelect"> Context </label>
          <span class="value">
            <md-outlined-select id="contextSelect">
              <md-select-option md-menu-item="" tabindex="0" value="3">
                <div slot="headline">3 lines</div>
              </md-select-option>
              <md-select-option
                data-aria-selected="true"
                md-menu-item=""
                tabindex="-1"
                value="10"
              >
                <div slot="headline">10 lines</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="25">
                <div slot="headline">25 lines</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="50">
                <div slot="headline">50 lines</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="75">
                <div slot="headline">75 lines</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="100">
                <div slot="headline">100 lines</div>
              </md-select-option>
              <md-select-option md-menu-item="" tabindex="-1" value="-1">
                <div slot="headline">Whole file</div>
              </md-select-option>
            </md-outlined-select>
          </span>
        </section>
        <section>
          <label class="title" for="lineWrappingInput"> Fit to screen </label>
          <span class="value">
            <md-checkbox id="lineWrappingInput"> </md-checkbox>
          </span>
        </section>
        <section>
          <label class="title" for="columnsInput"> Diff width </label>
          <span class="value">
            <md-outlined-text-field
              autocomplete=""
              class="showBlueFocusBorder"
              id="columnsInput"
              inputmode=""
              step="1"
              type="number"
            >
            </md-outlined-text-field>
          </span>
        </section>
        <section>
          <label class="title" for="tabSizeInput"> Tab width </label>
          <span class="value">
            <md-outlined-text-field
              autocomplete=""
              class="showBlueFocusBorder"
              id="tabSizeInput"
              inputmode=""
              step="1"
              type="number"
            >
            </md-outlined-text-field>
          </span>
        </section>
        <section>
          <label class="title" for="fontSizeInput"> Font size </label>
          <span class="value">
            <md-outlined-text-field
              autocomplete=""
              class="showBlueFocusBorder"
              id="fontSizeInput"
              inputmode=""
              step="1"
              type="number"
            >
            </md-outlined-text-field>
          </span>
        </section>
        <section>
          <label class="title" for="showTabsInput"> Show tabs </label>
          <span class="value">
            <md-checkbox checked="" id="showTabsInput"> </md-checkbox>
          </span>
        </section>
        <section>
          <label class="title" for="showTrailingWhitespaceInput">
            Show trailing whitespace
          </label>
          <span class="value">
            <md-checkbox checked="" id="showTrailingWhitespaceInput">
            </md-checkbox>
          </span>
        </section>
        <section>
          <label class="title" for="syntaxHighlightInput">
            Syntax highlighting
          </label>
          <span class="value">
            <md-checkbox checked="" id="syntaxHighlightInput"> </md-checkbox>
          </span>
        </section>
        <section>
          <label class="title" for="automaticReviewInput">
            Automatically mark viewed files reviewed
          </label>
          <span class="value">
            <md-checkbox checked="" id="automaticReviewInput"> </md-checkbox>
          </span>
        </section>
        <section>
          <div class="pref">
            <label class="title" for="ignoreWhiteSpace">
              Ignore Whitespace
            </label>
            <span class="value">
              <md-outlined-select id="contextSelect">
                <md-select-option
                  data-aria-selected="true"
                  md-menu-item=""
                  tabindex="0"
                  value="IGNORE_NONE"
                >
                  <div slot="headline">None</div>
                </md-select-option>
                <md-select-option
                  md-menu-item=""
                  tabindex="-1"
                  value="IGNORE_TRAILING"
                >
                  <div slot="headline">Trailing</div>
                </md-select-option>
                <md-select-option
                  md-menu-item=""
                  tabindex="-1"
                  value="IGNORE_LEADING_AND_TRAILING"
                >
                  <div slot="headline">Leading & trailing</div>
                </md-select-option>
                <md-select-option
                  md-menu-item=""
                  tabindex="-1"
                  value="IGNORE_ALL"
                >
                  <div slot="headline">All</div>
                </md-select-option>
              </md-outlined-select>
            </span>
          </div>
        </section>
      </div>`
    );
  });

  test('renders preferences', () => {
    // Rendered with the expected preferences selected.
    const contextInput = valueOf('Context', 'diffPreferences')
      .firstElementChild as MdOutlinedSelect;
    assert.equal(contextInput.value, `${diffPreferences.context}`);

    const lineWrappingInput = valueOf('Fit to screen', 'diffPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(lineWrappingInput.checked, diffPreferences.line_wrapping);

    const lineLengthInput = valueOf('Diff width', 'diffPreferences')
      .firstElementChild as MdOutlinedTextField;
    assert.equal(lineLengthInput.value, `${diffPreferences.line_length}`);

    const tabSizeInput = valueOf('Tab width', 'diffPreferences')
      .firstElementChild as MdOutlinedTextField;
    assert.equal(tabSizeInput.value, `${diffPreferences.tab_size}`);

    const fontSizeInput = valueOf('Font size', 'diffPreferences')
      .firstElementChild as MdOutlinedTextField;
    assert.equal(fontSizeInput.value, `${diffPreferences.font_size}`);

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
    ).firstElementChild as MdOutlinedSelect;
    assert.equal(
      ignoreWhitespaceInput.value,
      diffPreferences.ignore_whitespace
    );

    assert.isFalse(element.hasUnsavedChanges());
  });

  test('save changes', async () => {
    assert.isTrue(element.diffPrefs!.show_whitespace_errors);

    const showTrailingWhitespaceCheckbox = valueOf(
      'Show trailing whitespace',
      'diffPreferences'
    ).firstElementChild as HTMLInputElement;
    showTrailingWhitespaceCheckbox.checked = false;
    element.handleShowTrailingWhitespaceTap();

    assert.isTrue(element.hasUnsavedChanges());

    const savePrefStub = stubRestApi('saveDiffPreferences').resolves(
      new Response(makePrefixedJSON(element.diffPrefs))
    );

    await element.save();
    // Wait for model state update, since this is not awaited by element.save()
    await waitUntil(
      () =>
        !element.getUserModel().getState().diffPreferences
          ?.show_whitespace_errors
    );

    assert.isTrue(savePrefStub.called);
    assert.isFalse(element.diffPrefs!.show_whitespace_errors);
    assert.isFalse(element.hasUnsavedChanges());
  });
});
