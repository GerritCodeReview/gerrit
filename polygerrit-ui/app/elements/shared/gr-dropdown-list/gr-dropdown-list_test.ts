/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-dropdown-list';
import {GrDropdownList} from './gr-dropdown-list';
import {
  query,
  queryAll,
  queryAndAssert,
  waitEventLoop,
} from '../../../test/test-utils';
import {PaperListboxElement} from '@polymer/paper-listbox';
import {Timestamp} from '../../../types/common';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-dropdown-list tests', () => {
  let element: GrDropdownList;

  setup(async () => {
    element = await fixture<GrDropdownList>(
      html`<gr-dropdown-list></gr-dropdown-list>`
    );
  });

  test('render', async () => {
    element.value = '2';
    element.items = [
      {
        value: 1,
        text: 'Top Text 1',
      },
      {
        value: 2,
        bottomText: 'Bottom Text 2',
        triggerText: 'Button Text 2',
        text: 'Top Text 2',
        mobileText: 'Mobile Text 2',
      },
      {
        value: 3,
        disabled: true,
        bottomText: 'Bottom Text 3',
        triggerText: 'Button Text 3',
        date: '2017-08-18 23:11:42.569000000' as Timestamp,
        text: 'Top Text 3',
        mobileText: 'Mobile Text 3',
      },
    ];
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-button
          aria-disabled="false"
          class="dropdown-trigger"
          down-arrow=""
          id="trigger"
          link=""
          role="button"
          slot="dropdown-trigger"
          tabindex="0"
        >
          <span id="triggerText"> Button Text 2 </span>
          <gr-copy-clipboard class="copyClipboard" hidden="" hideinput="">
          </gr-copy-clipboard>
        </gr-button>
        <iron-dropdown
          aria-disabled="false"
          aria-hidden="true"
          horizontal-align="left"
          id="dropdown"
          style="outline: none; display: none;"
          vertical-align="top"
        >
          <paper-listbox
            class="dropdown-content"
            role="listbox"
            slot="dropdown-content"
            tabindex="0"
          >
            <paper-item
              aria-disabled="false"
              aria-selected="false"
              data-value="1"
              role="option"
              tabindex="-1"
            >
              <div class="topContent">
                <div><span>Top Text 1</span></div>
              </div>
            </paper-item>
            <paper-item
              aria-disabled="false"
              aria-selected="true"
              class="iron-selected"
              data-value="2"
              role="option"
              tabindex="0"
            >
              <div class="topContent">
                <div><span>Top Text 2</span></div>
              </div>
              <div class="bottomContent">
                <div>Bottom Text 2</div>
              </div>
            </paper-item>
            <paper-item
              aria-disabled="true"
              aria-selected="false"
              data-value="3"
              disabled=""
              role="option"
              style="pointer-events: none;"
              tabindex="-1"
            >
              <div class="topContent">
                <div><span>Top Text 3</span></div>
                <gr-date-formatter> </gr-date-formatter>
              </div>
              <div class="bottomContent">
                <div>Bottom Text 3</div>
              </div>
            </paper-item>
          </paper-listbox>
        </iron-dropdown>
        <gr-select>
          <select>
            <option value="1">Top Text 1</option>
            <option value="2">Mobile Text 2</option>
            <option disabled="" value="3">Mobile Text 3</option>
          </select>
        </gr-select>
      `
    );
  });

  test('hide copy by default', () => {
    const copyEl = query<HTMLElement>(
      element,
      '#triggerText + gr-copy-clipboard'
    )!;
    assert.isOk(copyEl);
    assert.isTrue(copyEl.hidden);
  });

  test('show copy if enabled', async () => {
    element.showCopyForTriggerText = true;
    await element.updateComplete;
    const copyEl = query<HTMLElement>(
      element,
      '#triggerText + gr-copy-clipboard'
    )!;
    assert.isOk(copyEl);
    assert.isFalse(copyEl.hidden);
  });

  test('tap on trigger opens menu', () => {
    sinon.stub(element, 'open').callsFake(() => {
      assertIsDefined(element.dropdown);
      element.dropdown.open();
    });
    assertIsDefined(element.dropdown);
    assert.isFalse(element.dropdown.opened);
    assertIsDefined(element.trigger);
    element.trigger.click();
    assert.isTrue(element.dropdown.opened);
  });

  test('computeMobileText', () => {
    const item: any = {
      value: 1,
      text: 'text',
    };
    assert.equal(element.computeMobileText(item), item.text);
    item.mobileText = 'mobile text';
    assert.equal(element.computeMobileText(item), item.mobileText);
  });

  test('options are selected and laid out correctly', async () => {
    element.value = '2';
    element.items = [
      {
        value: 1,
        text: 'Top Text 1',
      },
      {
        value: 2,
        bottomText: 'Bottom Text 2',
        triggerText: 'Button Text 2',
        text: 'Top Text 2',
        mobileText: 'Mobile Text 2',
      },
      {
        value: 3,
        disabled: true,
        bottomText: 'Bottom Text 3',
        triggerText: 'Button Text 3',
        date: '2017-08-18 23:11:42.569000000' as Timestamp,
        text: 'Top Text 3',
        mobileText: 'Mobile Text 3',
      },
    ];
    await element.updateComplete;
    await waitEventLoop();

    assert.equal(
      queryAndAssert<PaperListboxElement>(element, 'paper-listbox').selected,
      element.value
    );
    assert.equal(element.text, 'Button Text 2');

    const items = queryAll<HTMLInputElement>(element, 'paper-item');
    const mobileItems = queryAll<HTMLOptionElement>(element, 'option');
    assert.equal(items.length, 3);
    assert.equal(mobileItems.length, 3);

    // First Item
    // The first item should be disabled, has no bottom text, and no date.
    assert.isFalse(!!items[0].disabled);
    assert.isFalse(mobileItems[0].disabled);
    assert.isFalse(items[0].classList.contains('iron-selected'));
    assert.isFalse(mobileItems[0].selected);

    assert.isNotOk(items[0].querySelector('gr-date-formatter'));
    assert.isNotOk(items[0].querySelector('.bottomContent'));
    assert.equal(items[0].dataset.value, element.items[0].value as any);
    assert.equal(mobileItems[0].value, element.items[0].value);
    assert.equal(
      queryAndAssert<HTMLDivElement>(items[0], '.topContent div span')
        .innerText,
      element.items[0].text
    );

    // Since no mobile specific text, it should fall back to text.
    assert.equal(mobileItems[0].text, element.items[0].text);

    // Second Item
    // The second item should have top text, bottom text, and no date.
    assert.isFalse(!!items[1].disabled);
    assert.isFalse(mobileItems[1].disabled);
    assert.isTrue(items[1].classList.contains('iron-selected'));
    assert.isTrue(mobileItems[1].selected);

    assert.isNotOk(items[1].querySelector('gr-date-formatter'));
    assert.isOk(items[1].querySelector('.bottomContent'));
    assert.equal(items[1].dataset.value, element.items[1].value as any);
    assert.equal(mobileItems[1].value, element.items[1].value);
    assert.equal(
      queryAndAssert<HTMLDivElement>(items[1], '.topContent div span')
        .textContent,
      element.items[1].text
    );

    // Since there is mobile specific text, it should that.
    assert.equal(mobileItems[1].text, element.items[1].mobileText);

    // Since this item is selected, and it has triggerText defined, that
    // should be used.
    assert.equal(element.text, element.items[1].triggerText);

    // Third item
    // The third item should be disabled, and have a date, and bottom content.
    assert.isTrue(!!items[2].disabled);
    assert.isTrue(mobileItems[2].disabled);
    assert.isFalse(items[2].classList.contains('iron-selected'));
    assert.isFalse(mobileItems[2].selected);

    assert.isOk(items[2].querySelector('gr-date-formatter'));
    assert.isOk(items[2].querySelector('.bottomContent'));
    assert.equal(items[2].dataset.value, element.items[2].value as any);
    assert.equal(mobileItems[2].value, element.items[2].value);
    assert.equal(
      queryAndAssert<HTMLDivElement>(items[2], '.topContent div span')
        .innerText,
      element.items[2].text
    );

    // Since there is mobile specific text, it should that.
    assert.equal(mobileItems[2].text, element.items[2].mobileText);

    // Select a new item.
    items[0].click();
    await element.updateComplete;
    assert.equal(element.value, '1');
    assert.isTrue(items[0].classList.contains('iron-selected'));
    assert.isTrue(mobileItems[0].selected);

    // Since no triggerText, the fallback is used.
    assert.equal(element.text, element.items[0].text);
  });
});
