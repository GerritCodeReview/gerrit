/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-edit-preferences';
import {
  makePrefixedJSON,
  queryAll,
  stubRestApi,
  waitUntil,
} from '../../../test/test-utils';
import {GrEditPreferences} from './gr-edit-preferences';
import {EditPreferencesInfo} from '../../../types/common';
import {IronInputElement} from '@polymer/iron-input';
import {createDefaultEditPrefs} from '../../../constants/constants';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-edit-preferences tests', () => {
  let element: GrEditPreferences;
  let editPreferences: EditPreferencesInfo;

  function valueOf(title: string, id: string): Element {
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
    editPreferences = createDefaultEditPrefs();

    stubRestApi('getEditPreferences').returns(Promise.resolve(editPreferences));

    element = await fixture(html`<gr-edit-preferences></gr-edit-preferences>`);

    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <h2 id="EditPreferences">Edit Preferences</h2>
        <fieldset id="editPreferences">
          <div class="gr-form-styles" id="editPreferences">
            <section>
              <label class="title" for="editTabWidth"> Tab width </label>
              <span class="value">
                <iron-input>
                  <input id="editTabWidth" type="number" />
                </iron-input>
              </span>
            </section>
            <section>
              <label class="title" for="editColumns"> Columns </label>
              <span class="value">
                <iron-input>
                  <input id="editColumns" type="number" />
                </iron-input>
              </span>
            </section>
            <section>
              <label class="title" for="editIndentUnit"> Indent unit </label>
              <span class="value">
                <iron-input>
                  <input id="editIndentUnit" type="number" />
                </iron-input>
              </span>
            </section>
            <section>
              <label class="title" for="editSyntaxHighlighting">
                Syntax highlighting
              </label>
              <span class="value">
                <input checked="" id="editSyntaxHighlighting" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="editShowTabs"> Show tabs </label>
              <span class="value">
                <input checked="" id="editShowTabs" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="showTrailingWhitespaceInput">
                Show trailing whitespace
              </label>
              <span class="value">
                <input
                  checked=""
                  id="editShowTrailingWhitespaceInput"
                  type="checkbox"
                />
              </span>
            </section>
            <section>
              <label class="title" for="showMatchBrackets">
                Match brackets
              </label>
              <span class="value">
                <input checked="" id="showMatchBrackets" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="editShowLineWrapping">
                Line wrapping
              </label>
              <span class="value">
                <input id="editShowLineWrapping" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="showIndentWithTabs">
                Indent with tabs
              </label>
              <span class="value">
                <input id="showIndentWithTabs" type="checkbox" />
              </span>
            </section>
            <section>
              <label class="title" for="showAutoCloseBrackets">
                Auto close brackets
              </label>
              <span class="value">
                <input id="showAutoCloseBrackets" type="checkbox" />
              </span>
            </section>
          </div>
          <gr-button
            aria-disabled="true"
            disabled=""
            id="saveEditPrefs"
            role="button"
            tabindex="-1"
          >
            Save changes
          </gr-button>
        </fieldset>
      `
    );
  });

  test('input values match preferences', () => {
    // Rendered with the expected preferences selected.
    const tabWidthInput = valueOf('Tab width', 'editPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(tabWidthInput.bindValue, `${editPreferences.tab_size}`);

    const columnsInput = valueOf('Columns', 'editPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(columnsInput.bindValue, `${editPreferences.line_length}`);

    const indentInput = valueOf('Indent unit', 'editPreferences')
      .firstElementChild as IronInputElement;
    assert.equal(indentInput.bindValue, `${editPreferences.indent_unit}`);

    const syntaxInput = valueOf('Syntax highlighting', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(syntaxInput.checked, editPreferences.syntax_highlighting);

    const tabsInput = valueOf('Show tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(tabsInput.checked, editPreferences.show_tabs);

    const bracketsInput = valueOf('Match brackets', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(bracketsInput.checked, editPreferences.match_brackets);

    const wrappingInput = valueOf('Line wrapping', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(wrappingInput.checked, editPreferences.line_wrapping);

    const indentTabsInput = valueOf('Indent with tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(indentTabsInput.checked, editPreferences.indent_with_tabs);

    const autoCloseInput = valueOf('Auto close brackets', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    assert.equal(autoCloseInput.checked, editPreferences.auto_close_brackets);

    assert.isFalse(element.hasUnsavedChanges());
  });

  test('save changes', async () => {
    assert.isTrue(element.editPrefs?.show_tabs);

    const showTabsCheckbox = valueOf('Show tabs', 'editPreferences')
      .firstElementChild as HTMLInputElement;
    showTabsCheckbox.checked = false;
    element.handleEditShowTabsChanged();

    assert.isTrue(element.hasUnsavedChanges());

    const savePrefStub = stubRestApi('saveEditPreferences').resolves(
      new Response(makePrefixedJSON(element.editPrefs))
    );

    await element.save();
    // Wait for model state update, since this is not awaited by element.save()
    await waitUntil(
      () => !element.getUserModel().getState().editPreferences?.show_tabs
    );

    assert.isTrue(savePrefStub.called);
    assert.isFalse(element.editPrefs?.show_tabs);
    assert.isFalse(element.hasUnsavedChanges());
  });
});
