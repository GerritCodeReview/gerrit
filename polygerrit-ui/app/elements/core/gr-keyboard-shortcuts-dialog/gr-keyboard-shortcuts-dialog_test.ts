/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-keyboard-shortcuts-dialog';
import {GrKeyboardShortcutsDialog} from './gr-keyboard-shortcuts-dialog';
import {
  SectionView,
  ShortcutSection,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';

const basicFixture = fixtureFromElement('gr-keyboard-shortcuts-dialog');

suite('gr-keyboard-shortcuts-dialog tests', () => {
  let element: GrKeyboardShortcutsDialog;

  setup(async () => {
    element = basicFixture.instantiate();
    await flush();
  });

  function update(directory: Map<ShortcutSection, SectionView>) {
    element.onDirectoryUpdated(directory);
    flush();
  }

  suite('left and right contents', () => {
    test('empty dialog', () => {
      assert.isEmpty(element.left);
      assert.isEmpty(element.right);
    });

    test('everywhere goes on left', () => {
      const sectionView = [{binding: [], text: 'everywhere shortcuts'}];
      update(new Map([[ShortcutSection.EVERYWHERE, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.EVERYWHERE,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('navigation goes on left', () => {
      const sectionView = [{binding: [], text: 'navigation shortcuts'}];
      update(new Map([[ShortcutSection.NAVIGATION, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.NAVIGATION,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('actions go on right', () => {
      const sectionView = [{binding: [], text: 'actions shortcuts'}];
      update(new Map([[ShortcutSection.ACTIONS, sectionView]]));
      assert.deepEqual(element.right, [
        {
          section: ShortcutSection.ACTIONS,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.left);
    });

    test('reply dialog goes on left', () => {
      const sectionView = [{binding: [], text: 'reply dialog shortcuts'}];
      update(new Map([[ShortcutSection.REPLY_DIALOG, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.REPLY_DIALOG,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('file list goes on left', () => {
      const sectionView = [{binding: [], text: 'file list shortcuts'}];
      update(new Map([[ShortcutSection.FILE_LIST, sectionView]]));
      assert.deepEqual(element.left, [
        {
          section: ShortcutSection.FILE_LIST,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.right);
    });

    test('diffs go on right', () => {
      const sectionView = [{binding: [], text: 'diffs shortcuts'}];
      update(new Map([[ShortcutSection.DIFFS, sectionView]]));
      assert.deepEqual(element.right, [
        {
          section: ShortcutSection.DIFFS,
          shortcuts: sectionView,
        },
      ]);
      assert.isEmpty(element.left);
    });

    test('multiple sections on each side', () => {
      const actionsSectionView = [{binding: [], text: 'actions shortcuts'}];
      const diffsSectionView = [{binding: [], text: 'diffs shortcuts'}];
      const everywhereSectionView = [
        {binding: [], text: 'everywhere shortcuts'},
      ];
      const navigationSectionView = [
        {binding: [], text: 'navigation shortcuts'},
      ];
      update(
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
