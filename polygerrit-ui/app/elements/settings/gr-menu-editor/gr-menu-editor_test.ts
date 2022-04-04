/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-menu-editor';
import {GrMenuEditor} from './gr-menu-editor';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {query, queryAndAssert} from '../../../test/test-utils';
import {PaperButtonElement} from '@polymer/paper-button';
import {TopMenuItemInfo} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-menu-editor tests', () => {
  let element: GrMenuEditor;
  let menu: TopMenuItemInfo[];

  function assertMenuNamesEqual(
    element: GrMenuEditor,
    expected: Array<string>
  ) {
    const names = element.menuItems.map(i => i.name);
    assert.equal(names.length, expected.length);
    for (let i = 0; i < names.length; i++) {
      assert.equal(names[i], expected[i]);
    }
  }

  // Click the up/down button (according to direction) for the index'th row.
  // The index of the first row is 0, corresponding to the array.
  function move(element: GrMenuEditor, index: number, direction: string) {
    const selector = `tr:nth-child(${index + 1}) .move${direction}Button`;

    const button = query<PaperButtonElement>(
      query<HTMLElement>(query<HTMLTableElement>(element, 'tbody'), selector),
      'paper-button'
    );
    MockInteractions.tap(button!);
  }

  setup(async () => {
    element = await fixture<GrMenuEditor>(
      html`<gr-menu-editor></gr-menu-editor>`
    );
    menu = [
      {url: '/first/url', name: 'first name', target: '_blank'},
      {url: '/second/url', name: 'second name', target: '_blank'},
      {url: '/third/url', name: 'third name', target: '_blank'},
    ];
    element.menuItems = menu;
    await element.updateComplete;
  });

  test('renders', () => {
    expect(element).shadowDom.to.equal(/* HTML */ `
      <div class="gr-form-styles">
        <h2 class="edited heading-2" id="Menu">Menu</h2>
        <fieldset id="menu">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>URL</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>first name</td>
                <td class="urlCell">/first/url</td>
                <td class="buttonColumn">
                  <gr-button
                    aria-disabled="false"
                    class="moveUpButton"
                    data-index="0"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    ↑
                  </gr-button>
                </td>
                <td class="buttonColumn">
                  <gr-button
                    aria-disabled="false"
                    class="moveDownButton"
                    data-index="0"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    ↓
                  </gr-button>
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    class="remove-button"
                    data-index="0"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
              <tr>
                <td>second name</td>
                <td class="urlCell">/second/url</td>
                <td class="buttonColumn">
                  <gr-button
                    aria-disabled="false"
                    class="moveUpButton"
                    data-index="1"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    ↑
                  </gr-button>
                </td>
                <td class="buttonColumn">
                  <gr-button
                    aria-disabled="false"
                    class="moveDownButton"
                    data-index="1"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    ↓
                  </gr-button>
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    class="remove-button"
                    data-index="1"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
              <tr>
                <td>third name</td>
                <td class="urlCell">/third/url</td>
                <td class="buttonColumn">
                  <gr-button
                    aria-disabled="false"
                    class="moveUpButton"
                    data-index="2"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    ↑
                  </gr-button>
                </td>
                <td class="buttonColumn">
                  <gr-button
                    aria-disabled="false"
                    class="moveDownButton"
                    data-index="2"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    ↓
                  </gr-button>
                </td>
                <td>
                  <gr-button
                    aria-disabled="false"
                    class="remove-button"
                    data-index="2"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    Delete
                  </gr-button>
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th>
                  <iron-input>
                    <input is="iron-input" placeholder="New Title" />
                  </iron-input>
                </th>
                <th>
                  <iron-input>
                    <input class="newUrlInput" placeholder="New URL" />
                  </iron-input>
                </th>
                <th></th>
                <th></th>
                <th>
                  <gr-button
                    aria-disabled="true"
                    disabled=""
                    id="add"
                    link=""
                    role="button"
                    tabindex="-1"
                  >
                    Add
                  </gr-button>
                </th>
              </tr>
            </tfoot>
          </table>
          <gr-button
            aria-disabled="false"
            id="saveMenu"
            role="button"
            tabindex="0"
          >
            Save changes
          </gr-button>
          <gr-button
            aria-disabled="false"
            id="resetButton"
            link=""
            role="button"
            tabindex="0"
          >
            Reset
          </gr-button>
        </fieldset>
      </div>
    `);
  });

  test('add button disabled', async () => {
    element.newName = 'test-name';
    await element.updateComplete;
    let addButton = queryAndAssert<GrButton>(element, 'gr-button#add');
    assert.isTrue(addButton.hasAttribute('disabled'));

    element.newUrl = 'test-url';
    await element.updateComplete;
    addButton = queryAndAssert<GrButton>(element, 'gr-button#add');
    assert.isFalse(addButton.hasAttribute('disabled'));
  });

  test('add a new menu item', async () => {
    const newName = 'new name';
    const newUrl = 'new url';
    const originalMenuLength = element.menuItems.length;

    element.newName = newName;
    element.newUrl = newUrl;
    await element.updateComplete;

    const addButton = queryAndAssert<GrButton>(element, 'gr-button#add');
    assert.isFalse(addButton.hasAttribute('disabled'));
    addButton.click();

    assert.equal(element.menuItems.length, originalMenuLength + 1);
    assert.equal(element.menuItems[element.menuItems.length - 1].name, newName);
    assert.equal(element.menuItems[element.menuItems.length - 1].url, newUrl);
  });

  test('move items down', () => {
    assertMenuNamesEqual(element, ['first name', 'second name', 'third name']);

    // Move the middle item down
    move(element, 1, 'Down');
    assertMenuNamesEqual(element, ['first name', 'third name', 'second name']);

    // Moving the bottom item down is a no-op.
    move(element, 2, 'Down');
    assertMenuNamesEqual(element, ['first name', 'third name', 'second name']);
  });

  test('move items up', () => {
    assertMenuNamesEqual(element, ['first name', 'second name', 'third name']);

    // Move the last item up twice to be the first.
    move(element, 2, 'Up');
    move(element, 1, 'Up');
    assertMenuNamesEqual(element, ['third name', 'first name', 'second name']);

    // Moving the top item up is a no-op.
    move(element, 0, 'Up');
    assertMenuNamesEqual(element, ['third name', 'first name', 'second name']);
  });

  test('remove item', () => {
    assertMenuNamesEqual(element, ['first name', 'second name', 'third name']);

    // Tap the delete button for the middle item.
    MockInteractions.tap(
      query<PaperButtonElement>(
        query<HTMLElement>(
          query<HTMLTableElement>(element, 'tbody'),
          'tr:nth-child(2) .remove-button'
        ),
        'paper-button'
      )!
    );

    assertMenuNamesEqual(element, ['first name', 'third name']);

    // Delete remaining items.
    for (let i = 0; i < 2; i++) {
      MockInteractions.tap(
        query<PaperButtonElement>(
          query<HTMLElement>(
            query<HTMLTableElement>(element, 'tbody'),
            'tr:first-child .remove-button'
          ),
          'paper-button'
        )!
      );
    }
    assertMenuNamesEqual(element, []);

    // Add item to empty menu.
    element.newName = 'new name';
    element.newUrl = 'new url';
    element.handleAddButton();
    assertMenuNamesEqual(element, ['new name']);
  });
});
