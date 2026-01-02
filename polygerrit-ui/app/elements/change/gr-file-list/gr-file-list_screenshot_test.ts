/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../shared/gr-date-formatter/gr-date-formatter';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {FileInfo, PARENT, RevisionPatchSetNum} from '../../../api/rest-api';
import {normalize} from '../../../models/change/files-model';
import {PatchRange} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../api/diff';
import {GrFileList, NormalizedFileInfo} from './gr-file-list';
import './gr-file-list';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-file-list screenshot tests', () => {
  let element: GrFileList;

  function createFiles(
    count: number,
    fileInfo: FileInfo
  ): NormalizedFileInfo[] {
    return Array.from(Array(count).keys()).map(index =>
      normalize(fileInfo, `/file${index}`)
    );
  }

  setup(async () => {
    const patchRange: PatchRange = {
      basePatchNum: PARENT,
      patchNum: 2 as RevisionPatchSetNum,
    };
    const diffPrefs: DiffPreferencesInfo = {
      context: 10,
      tab_size: 8,
      font_size: 12,
      line_length: 100,
      ignore_whitespace: 'IGNORE_NONE',
    };
    element = await fixture(
      html`<gr-file-list
        .patchNum=${patchRange.patchNum}
        .basePatchNum=${patchRange.basePatchNum}
        .diffPrefs=${diffPrefs}
      ></gr-file-list>`
    );
  });

  test('screenshot', async () => {
    element.files = [
      ...createFiles(3, {lines_inserted: 9}),
      ...createFiles(2, {lines_deleted: 14}),
    ];
    await element.updateComplete;

    await visualDiff(element, 'gr-file-list');
    await visualDiffDarkTheme(element, 'gr-file-list');
  });

  test('screenshot with file filter active', async () => {
    element.files = [
      normalize(
        {lines_inserted: 15, lines_deleted: 2},
        'polygerrit-ui/app/elements/change/gr-file-list/gr-file-list.ts'
      ),
      normalize(
        {lines_inserted: 40, lines_deleted: 0},
        'polygerrit-ui/app/elements/change/gr-file-list-filter/gr-file-list-filter.ts'
      ),
      normalize(
        {lines_inserted: 8, lines_deleted: 1},
        'Documentation/rest-api-changes.txt'
      ),
      normalize(
        {lines_inserted: 120, lines_deleted: 5},
        'gerrit-server/src/main/java/ChangeUtil.java'
      ),
      normalize({lines_inserted: 5, lines_deleted: 0}, 'package.json'),
    ];
    element.hiddenFileExtensions = ['.txt', '.java'];
    await element.updateComplete;

    await visualDiff(element, 'gr-file-list-filtered');
    await visualDiffDarkTheme(element, 'gr-file-list-filtered');
  });
});
