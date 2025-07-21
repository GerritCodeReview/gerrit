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
import {Timestamp} from '../../../types/common';
import {assertIsDefined} from '../../../utils/common-util';
import {assert, fixture, html} from '@open-wc/testing';

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
        <div class="dropdown">
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
          <md-menu
            anchor="trigger"
            default-focus="none"
            aria-hidden="true"
            id="dropdown"
            quick=""
            tabindex="-1"
          >
            <md-menu-item md-menu-item="" tabindex="0">
              <div class="topContent">
                <div>
                <span class="desktopText">
                   Top Text 1
                </span>
                <span class="mobileText">
                  Top Text 1
                </div>
              </div>
            </md-menu-item>
            <md-divider role="separator" tabindex="-1"> </md-divider>
            <md-menu-item active="" md-menu-item="" selected="" tabindex="-1">
              <div class="topContent">
                <div>
                <span class="desktopText">
                  Top Text 2
                </span>
                  <span class="mobileText"> Mobile Text 2 </span>
                </div>
              </div>
              <div class="bottomContent">
                <div>Bottom Text 2</div>
              </div>
            </md-menu-item>
            <md-divider role="separator" tabindex="-1"> </md-divider>
            <md-menu-item disabled="" md-menu-item="" tabindex="-1">
              <div class="topContent">
                <div>
                <span class="desktopText">
                  Top Text 3
                </span>
                  <span class="mobileText"> Mobile Text 3 </span>
                </div>
                <gr-date-formatter> </gr-date-formatter>
              </div>
              <div class="bottomContent">
                <div>Bottom Text 3</div>
              </div>
            </md-menu-item>
          </md-menu>
        </div>
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
      element.dropdown.show();
    });
    assertIsDefined(element.dropdown);
    assert.isFalse(element.dropdown.open);
    assertIsDefined(element.trigger);
    element.trigger.click();
    assert.isTrue(element.dropdown.open);
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

    const menu = queryAndAssert<HTMLElement>(element, 'md-menu');
    const items = queryAll<HTMLElement>(menu, 'md-menu-item');

    assert.equal(items.length, 3);

    // Item 0 (First)
    assert.isFalse(items[0].hasAttribute('disabled'));
    assert.isFalse(items[0].hasAttribute('selected'));
    assert.isNotOk(items[0].querySelector('gr-date-formatter'));
    assert.isNotOk(items[0].querySelector('.bottomContent'));
    assert.equal(
      queryAndAssert<HTMLSpanElement>(items[0], '.topContent div span')
        .innerText,
      element.items[0].text
    );

    // Item 1 (Second)
    assert.isFalse(items[1].hasAttribute('disabled'));
    assert.isTrue(items[1].hasAttribute('selected'));
    assert.isNotOk(items[1].querySelector('gr-date-formatter'));
    assert.isOk(items[1].querySelector('.bottomContent'));
    assert.equal(
      queryAndAssert<HTMLSpanElement>(items[1], '.topContent div span')
        .textContent,
      element.items[1].text
    );
    assert.equal(element.text, element.items[1].triggerText);

    // Item 2 (Third)
    assert.isTrue(items[2].hasAttribute('disabled'));
    assert.isFalse(items[2].hasAttribute('selected'));
    assert.isOk(items[2].querySelector('gr-date-formatter'));
    assert.isOk(items[2].querySelector('.bottomContent'));
    assert.equal(
      queryAndAssert<HTMLSpanElement>(items[2], '.topContent div span')
        .innerText,
      element.items[2].text
    );

    // Select first item
    items[0].click();
    await element.updateComplete;

    assert.equal(element.value, '1');
    assert.isTrue(items[0].hasAttribute('selected'));
    assert.equal(element.text, element.items[0].text);
  });
});
