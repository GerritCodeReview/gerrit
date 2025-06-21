/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-documentation-search';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrDocumentationSearch} from './gr-documentation-search';
import {DocResult} from '../../../types/common';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-documentation-search screenshot tests', () => {
  let element: GrDocumentationSearch;

  setup(async () => {
    const mockSearches: DocResult[] = [
      {
        title: 'Documentation A',
        url: 'Documentation/a.html',
      },
      {
        title: 'Documentation B',
        url: 'Documentation/b.html',
      },
    ];
    element = await fixture<GrDocumentationSearch>(
      html`<gr-documentation-search></gr-documentation-search>`
    );
    element.documentationSearches = mockSearches;
    element.loading = false;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-documentation-search');
    await visualDiffDarkTheme(element, 'gr-documentation-search');
  });
});
