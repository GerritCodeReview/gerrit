/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-keyboard-shortcuts-dialog';
import {GrKeyboardShortcutsDialog} from './gr-keyboard-shortcuts-dialog';
import {
  SectionView,
  ShortcutSection,
} from '../../../services/shortcuts/shortcuts-service';
import {assert, fixture, html} from '@open-wc/testing';
import {waitEventLoop} from '../../../test/test-utils';

const x = ['x'];
const ctrlX = ['Ctrl', 'x'];
const shiftMetaX = ['Shift', 'Meta', 'x'];

suite('gr-keyboard-shortcuts-dialog tests', () => {
  let element: GrKeyboardShortcutsDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-keyboard-shortcuts-dialog></gr-keyboard-shortcuts-dialog>`
    );
    await waitEventLoop();
  });

  async function update(directory: Map<ShortcutSection, SectionView>) {
    element.onDirectoryUpdated(directory);
    await waitEventLoop();
  }

  test('renders left and right contents', async () => {
    const directory = new Map([
      [
        ShortcutSection.NAVIGATION,
        [{binding: [x, ctrlX], text: 'navigation shortcuts'}],
      ],
      [
        ShortcutSection.ACTIONS,
        [{binding: [shiftMetaX], text: 'navigation shortcuts'}],
      ],
    ]);
    await update(directory);
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <header>
          <h3 class="heading-2">Keyboard shortcuts</h3>
          <gr-button aria-disabled="false" link="" role="button" tabindex="0">
            Close
          </gr-button>
        </header>
        <main>
          <div class="column">
            <table>
              <caption class="heading-3">
                Navigation
              </caption>
              <thead>
                <tr>
                  <th>
                    <strong> Action </strong>
                  </th>
                  <th>
                    <strong> Key </strong>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>navigation shortcuts</td>
                  <td>
                    <gr-key-binding-display> </gr-key-binding-display>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="column">
            <table>
              <caption class="heading-3">
                Actions
              </caption>
              <thead>
                <tr>
                  <th>
                    <strong> Action </strong>
                  </th>
                  <th>
                    <strong> Key </strong>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>navigation shortcuts</td>
                  <td>
                    <gr-key-binding-display> </gr-key-binding-display>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </main>
        <footer></footer>
      `
    );
  });

  suite('left and right contents', () => {
    test('empty dialog', () => {
      assert.isEmpty(element.left);
      assert.isEmpty(element.right);
    });

    test('everywhere goes on left', async () => {
      const sectionView = [{binding: [], text: 'everywhere shortcuts'}];
      await update(new Map([[ShortcutSection.EVERYWHERE, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.EVERYWHERE,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('navigation goes on left', async () => {
      const sectionView = [{binding: [], text: 'navigation shortcuts'}];
      await update(new Map([[ShortcutSection.NAVIGATION, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.NAVIGATION,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('actions go on right', async () => {
      const sectionView = [{binding: [], text: 'actions shortcuts'}];
      await update(new Map([[ShortcutSection.ACTIONS, sectionView]]));
      assert.deepEqual(element.right, [
        {
          section: ShortcutSection.ACTIONS,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.left);
    });

    test('reply dialog goes on left', async () => {
      const sectionView = [{binding: [], text: 'reply dialog shortcuts'}];
      await update(new Map([[ShortcutSection.REPLY_DIALOG, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.REPLY_DIALOG,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('file list goes on left', async () => {
      const sectionView = [{binding: [], text: 'file list shortcuts'}];
      await update(new Map([[ShortcutSection.FILE_LIST, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.FILE_LIST,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('diffs go on right', async () => {
      const sectionView = [{binding: [], text: 'diffs shortcuts'}];
      await update(new Map([[ShortcutSection.DIFFS, sectionView]]));
      assert.deepEqual(element.right, [
        {
          section: ShortcutSection.DIFFS,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.left);
    });

    test('multiple sections on each side', async () => {
      const actionsSectionView = [{binding: [], text: 'actions shortcuts'}];
      const diffsSectionView = [{binding: [], text: 'diffs shortcuts'}];
      const everywhereSectionView = [
        {binding: [], text: 'everywhere shortcuts'},
      ];
      const navigationSectionView = [
        {binding: [], text: 'navigation shortcuts'},
      ];
      await update(
        new Map([
          [ShortcutSection.ACTIONS, actionsSectionView],
          [ShortcutSection.DIFFS, diffsSectionView],
          [ShortcutSection.EVERYWHERE, everywhereSectionView],
          [ShortcutSection.NAVIGATION, navigationSectionView],
        ])
      );
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.EVERYWHERE,
          shortcuts: everywhereSectionView,
        },
        {
          section: ShortcutSection.NAVIGATION,
          shortcuts: navigationSectionView,
        },
      ]);
      assert.deepEqual(element.right, [
        {
          section: ShortcutSection.ACTIONS,
          shortcuts: actionsSectionView,
        },
        {
          section: ShortcutSection.DIFFS,
          shortcuts: diffsSectionView,
        },
      ]);
    });
  });
});
