/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-file-status';
import {GrFileStatus} from './gr-file-status';
import {fixture} from '@open-wc/testing-helpers';
import {FileInfoStatus} from '../../../api/rest-api';

suite('gr-file-status tests', () => {
  let element: GrFileStatus;

  setup(async () => {
    element = await fixture<GrFileStatus>('<gr-file-status></gr-file-status>');
    await setStatus();
  });

  const setStatus = async (status?: FileInfoStatus, newly = false) => {
    element.status = status;
    element.newlyChanged = newly;
    await element.updateComplete;
  };

  suite('semantic dom diff tests', () => {
    test('empty status', async () => {
      expect(element).shadowDom.to.equal(/* HTML */ `
        <gr-tooltip-content has-tooltip="" title="">
          <div class="status" aria-label="" tabindex="0"></div>
        </gr-tooltip-content>
      `);
    });

    test('added', async () => {
      await setStatus(FileInfoStatus.ADDED);
      expect(element).shadowDom.to.equal(/* HTML */ `
        <gr-tooltip-content has-tooltip="" title="Added">
          <div class="A status" aria-label="Added" tabindex="0">A</div>
        </gr-tooltip-content>
      `);
    });

    test('newly added', async () => {
      await setStatus(FileInfoStatus.ADDED, true);
      expect(element).shadowDom.to.equal(/* HTML */ `
        <gr-tooltip-content has-tooltip="" title="Newly Added">
          <iron-icon
            class="new size-16"
            icon="gr-icons:new"
            aria-label="Newly Added"
          ></iron-icon>
        </gr-tooltip-content>
        <gr-tooltip-content has-tooltip="" title="Newly Added">
          <div class="A status" aria-label="Newly Added" tabindex="0">A</div>
        </gr-tooltip-content>
      `);
    });
  });
});
