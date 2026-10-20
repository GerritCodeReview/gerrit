/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-preferences';
import {fixture, html} from '@open-wc/testing';
import {setViewport} from '@web/test-runner-commands';
// @ts-expect-error The visual regression package does not provide declarations.
import {visualDiff} from '@web/test-runner-visual-regression';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken} from '../../../models/user/user-model';
import {createDefaultPreferences} from '../../../constants/constants';
import {GrPreferences} from './gr-preferences';
import {queryAndAssert, visualDiffDarkTheme} from '../../../test/test-utils';

suite('account thread preference screenshot', () => {
  test('thread preference', async () => {
    testResolver(userModelToken).setPreferences(createDefaultPreferences());
    await setViewport({width: 800, height: 600});
    const element = await fixture<GrPreferences>(
      html`<gr-preferences></gr-preferences>`
    );
    const section = queryAndAssert(element, '#threadedChanges').closest(
      'section'
    )!;
    await visualDiff(section, 'gr-preferences-threaded-changes');
    await visualDiffDarkTheme(section, 'gr-preferences-threaded-changes');
  });
});
