/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import {GrChecksChip} from './gr-checks-chip';
import {Category} from '../../../api/checks';

suite('gr-checks-chip test', () => {
  let element: GrChecksChip;
  setup(async () => {
    element = await fixture(html`<gr-checks-chip
      .statusOrCategory=${Category.SUCCESS}
      .text=${'0'}
    ></gr-checks-chip>`);
  });

  test('is defined', () => {
    const el = document.createElement('gr-checks-chip');
    assert.instanceOf(el, GrChecksChip);
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `<div
        aria-label="0 success result"
        class="check_circle checksChip font-small"
        role="link"
        tabindex="0"
      >
        <gr-icon icon="check_circle"></gr-icon>
        <div class="text">0</div>
      </div>`
    );
  });

  test('renders specific check', async () => {
    element.text = 'Super Check';
    element.statusOrCategory = Category.ERROR;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div
          aria-label="error for check Super Check"
          class="checksChip error font-small"
          role="link"
          tabindex="0"
        >
          <gr-icon icon="error" filled></gr-icon>
          <div class="text">Super Check</div>
        </div>
      `
    );
  });

  test('renders check with link', async () => {
    element.text = 'LinkProducer';
    element.statusOrCategory = Category.WARNING;
    element.links = ['http://www.google.com'];
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div
          aria-label="warning for check LinkProducer"
          class="checksChip warning font-small"
          role="link"
          tabindex="0"
        >
          <gr-icon icon="warning" filled></gr-icon>
          <a
            aria-label="Link to check details"
            href="http://www.google.com"
            target="_blank"
            rel="noopener noreferrer"
          >
            <gr-icon class="launch" icon="open_in_new"> </gr-icon>
          </a>
          <div class="text">LinkProducer</div>
        </div>
      `
    );
  });
});
