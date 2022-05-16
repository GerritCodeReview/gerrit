/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-access-section.js';
import {AccessPermissions, toSortedPermissionsArray} from '../../../utils/access-util.js';

const fixture = fixtureFromElement('gr-access-section');

suite('gr-access-section tests', () => {
  let element;

  setup(() => {
    element = fixture.instantiate();
  });

  suite('unit tests', () => {
    setup(() => {
      element.section = {
        id: 'refs/*',
        value: {
          permissions: {
            read: {
              rules: {},
            },
          },
        },
      };
      element.capabilities = {
        accessDatabase: {
          id: 'accessDatabase',
          name: 'Access Database',
        },
        administrateServer: {
          id: 'administrateServer',
          name: 'Administrate Server',
        },
        batchChangesLimit: {
          id: 'batchChangesLimit',
          name: 'Batch Changes Limit',
        },
        createAccount: {
          id: 'createAccount',
          name: 'Create Account',
        },
      };
      element.labels = {
        'Code-Review': {
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not merged as is',
            '-2': 'This shall not be merged',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      };
      element._updateSection(element.section);
      flush();
    });

    test('_updateSection', () => {
      // _updateSection was called in setup, so just make assertions.
      const expectedPermissions = [
        {
          id: 'read',
          value: {
            rules: {},
          },
        },
      ];
      assert.deepEqual(element._permissions, expectedPermissions);
      assert.equal(element._originalId, element.section.id);
    });

    test('_computeLabelOptions', () => {
      const expectedLabelOptions = [
        {
          id: 'label-Code-Review',
          value: {
            name: 'Label Code-Review',
            id: 'label-Code-Review',
          },
        },
        {
          id: 'labelAs-Code-Review',
          value: {
            name: 'Label Code-Review (On Behalf Of)',
            id: 'labelAs-Code-Review',
          },
        },
      ];

      assert.deepEqual(element._computeLabelOptions(element.labels),
          expectedLabelOptions);
    });

    test('_handleAccessSaved', () => {
      assert.equal(element._originalId, 'refs/*');
      element.section.id = 'refs/for/bar';
      element._handleAccessSaved();
      assert.equal(element._originalId, 'refs/for/bar');
    });

    test('_computePermissions', () => {
      const capabilities = {
        push: {
          rules: {},
        },
        read: {
          rules: {},
        },
      };

      const expectedPermissions = [{
        id: 'push',
        value: {
          rules: {},
        },
      },
      ];
      const labelOptions = [
        {
          id: 'label-Code-Review',
          value: {
            name: 'Label Code-Review',
            id: 'label-Code-Review',
          },
        },
        {
          id: 'labelAs-Code-Review',
          value: {
            name: 'Label Code-Review (On Behalf Of)',
            id: 'labelAs-Code-Review',
          },
        },
      ];

      // For global capabilities, just return the sorted array filtered by
      // existing permissions.
      let name = 'GLOBAL_CAPABILITIES';
      assert.deepEqual(element._computePermissions(name, capabilities,
          element.labels), expectedPermissions);

      // For everything else, include possible label values before filtering.
      name = 'refs/for/*';
      assert.deepEqual(
          element._computePermissions(name, capabilities, element.labels),
          labelOptions
              .concat(toSortedPermissionsArray(AccessPermissions))
              .filter(permission => permission.id !== 'read'));
    });

    test('_computePermissionName', () => {
      let name = 'GLOBAL_CAPABILITIES';
      let permission = {
        id: 'administrateServer',
        value: {},
      };
      assert.equal(element._computePermissionName(name, permission,
          element.capabilities),
      element.capabilities[permission.id].name);

      permission = {
        id: 'non-existent',
        value: {},
      };
      assert.isUndefined(element._computePermissionName(name, permission,
          element.capabilities));

      name = 'refs/for/*';
      permission = {
        id: 'abandon',
        value: {},
      };

      assert.equal(element._computePermissionName(
          name, permission, element.capabilities),
      AccessPermissions[permission.id].name);

      name = 'refs/for/*';
      permission = {
        id: 'label-Code-Review',
        value: {
          label: 'Code-Review',
        },
      };

      assert.equal(element._computePermissionName(name, permission,
          element.capabilities),
      'Label Code-Review');

      permission = {
        id: 'labelAs-Code-Review',
        value: {
          label: 'Code-Review',
        },
      };

      assert.equal(element._computePermissionName(name, permission,
          element.capabilities),
      'Label Code-Review(On Behalf Of)');
    });

    test('_computeSectionName', () => {
      let name;
      // When computing the section name for an undefined name, it means a
      // new section is being added. In this case, it should default to
      // 'refs/heads/*'.
      element._editingRef = false;
      assert.equal(element._computeSectionName(name),
          'Reference: refs/heads/*');
      assert.isTrue(element._editingRef);
      assert.equal(element.section.id, 'refs/heads/*');

      // Reset editing to false.
      element._editingRef = false;
      name = 'GLOBAL_CAPABILITIES';
      assert.equal(element._computeSectionName(name), 'Global Capabilities');
      assert.isFalse(element._editingRef);

      name = 'refs/for/*';
      assert.equal(element._computeSectionName(name),
          'Reference: refs/for/*');
      assert.isFalse(element._editingRef);
    });

    test('editReference', () => {
      element.editReference();
      assert.isTrue(element._editingRef);
    });

    test('_computeSectionClass', () => {
      let editingRef = false;
      let canUpload = false;
      let ownerOf = [];
      let editing = false;
      let deleted = false;
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), '');

      editing = true;
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), '');

      ownerOf = ['refs/*'];
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), 'editing');

      ownerOf = [];
      canUpload = true;
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), 'editing');

      editingRef = true;
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), 'editing editingRef');

      deleted = true;
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), 'editing editingRef deleted');

      editingRef = false;
      assert.equal(element._computeSectionClass(editing, canUpload, ownerOf,
          editingRef, deleted), 'editing deleted');
    });

    test('_computeEditBtnClass', () => {
      let name = 'GLOBAL_CAPABILITIES';
      assert.equal(element._computeEditBtnClass(name), 'global');
      name = 'refs/for/*';
      assert.equal(element._computeEditBtnClass(name), '');
    });
  });

  suite('interactive tests', () => {
    setup(() => {
      element.labels = {
        'Code-Review': {
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not merged as is',
            '-2': 'This shall not be merged',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      };
    });
    suite('Global section', () => {
      setup(() => {
        element.section = {
          id: 'GLOBAL_CAPABILITIES',
          value: {
            permissions: {
              accessDatabase: {
                rules: {},
              },
            },
          },
        };
        element.capabilities = {
          accessDatabase: {
            id: 'accessDatabase',
            name: 'Access Database',
          },
          administrateServer: {
            id: 'administrateServer',
            name: 'Administrate Server',
          },
          batchChangesLimit: {
            id: 'batchChangesLimit',
            name: 'Batch Changes Limit',
          },
          createAccount: {
            id: 'createAccount',
            name: 'Create Account',
          },
        };
        element._updateSection(element.section);
        flush();
      });

      test('classes are assigned correctly', () => {
        assert.isFalse(element.$.section.classList.contains('editing'));
        assert.isFalse(element.$.section.classList.contains('deleted'));
        assert.isTrue(element.$.editBtn.classList.contains('global'));
        element.editing = true;
        element.canUpload = true;
        element.ownerOf = [];
        assert.equal(getComputedStyle(element.$.editBtn).display, 'none');
      });
    });

    suite('Non-global section', () => {
      setup(() => {
        element.section = {
          id: 'refs/*',
          value: {
            permissions: {
              read: {
                rules: {},
              },
            },
          },
        };
        element.capabilities = {};
        element._updateSection(element.section);
        flush();
      });

      test('classes are assigned correctly', () => {
        assert.isFalse(element.$.section.classList.contains('editing'));
        assert.isFalse(element.$.section.classList.contains('deleted'));
        assert.isFalse(element.$.editBtn.classList.contains('global'));
        element.editing = true;
        element.canUpload = true;
        element.ownerOf = [];
        flush();
        assert.notEqual(getComputedStyle(element.$.editBtn).display, 'none');
      });

      test('add permission', () => {
        element.editing = true;
        element.$.permissionSelect.value = 'label-Code-Review';
        assert.equal(element._permissions.length, 1);
        assert.equal(Object.keys(element.section.value.permissions).length,
            1);
        MockInteractions.tap(element.$.addBtn);
        flush();

        // The permission is added to both the permissions array and also
        // the section's permission object.
        assert.equal(element._permissions.length, 2);
        let permission = {
          id: 'label-Code-Review',
          value: {
            added: true,
            label: 'Code-Review',
            rules: {},
          },
        };
        assert.equal(element._permissions.length, 2);
        assert.deepEqual(element._permissions[1], permission);
        assert.equal(Object.keys(element.section.value.permissions).length,
            2);
        assert.deepEqual(
            element.section.value.permissions['label-Code-Review'],
            permission.value);

        element.$.permissionSelect.value = 'abandon';
        MockInteractions.tap(element.$.addBtn);
        flush();

        permission = {
          id: 'abandon',
          value: {
            added: true,
            rules: {},
          },
        };

        assert.equal(element._permissions.length, 3);
        assert.deepEqual(element._permissions[2], permission);
        assert.equal(Object.keys(element.section.value.permissions).length,
            3);
        assert.deepEqual(element.section.value.permissions['abandon'],
            permission.value);

        // Unsaved changes are discarded when editing is cancelled.
        element.editing = false;
        assert.equal(element._permissions.length, 1);
        assert.equal(Object.keys(element.section.value.permissions).length,
            1);
      });

      test('edit section reference', async () => {
        element.canUpload = true;
        element.ownerOf = [];
        element.section = {id: 'refs/for/bar', value: {permissions: {}}};
        assert.isFalse(element.$.section.classList.contains('editing'));
        element.editing = true;
        assert.isTrue(element.$.section.classList.contains('editing'));
        assert.isFalse(element._editingRef);
        MockInteractions.tap(element.$.editBtn);
        element.editRefInput().bindValue='new/ref';
        await flush();
        assert.equal(element.section.id, 'new/ref');
        assert.isTrue(element._editingRef);
        assert.isTrue(element.$.section.classList.contains('editingRef'));
        element.editing = false;
        assert.isFalse(element._editingRef);
        assert.equal(element.section.id, 'refs/for/bar');
      });

      test('_handleValueChange', () => {
        // For an existing section.
        const modifiedHandler = sinon.stub();
        element.section = {id: 'refs/for/bar', value: {permissions: {}}};
        assert.notOk(element.section.value.updatedId);
        element.section.id = 'refs/for/baz';
        element.addEventListener('access-modified', modifiedHandler);
        assert.isNotOk(element.section.value.modified);
        element._handleValueChange();
        assert.equal(element.section.value.updatedId, 'refs/for/baz');
        assert.isTrue(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 1);
        element.section.id = 'refs/for/bar';
        element._handleValueChange();
        assert.isFalse(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 2);

        // For a new section.
        element.section.value.added = true;
        element._handleValueChange();
        assert.isFalse(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 2);
        element.section.id = 'refs/for/bar';
        element._handleValueChange();
        assert.isFalse(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 2);
      });

      test('remove section', () => {
        element.editing = true;
        element.canUpload = true;
        element.ownerOf = [];
        assert.isFalse(element._deleted);
        assert.isNotOk(element.section.value.deleted);
        MockInteractions.tap(element.$.deleteBtn);
        flush();
        assert.isTrue(element._deleted);
        assert.isTrue(element.section.value.deleted);
        assert.isTrue(element.$.section.classList.contains('deleted'));
        assert.isTrue(element.section.value.deleted);

        MockInteractions.tap(element.$.undoRemoveBtn);
        flush();
        assert.isFalse(element._deleted);
        assert.isNotOk(element.section.value.deleted);

        MockInteractions.tap(element.$.deleteBtn);
        assert.isTrue(element._deleted);
        assert.isTrue(element.section.value.deleted);
        element.editing = false;
        assert.isFalse(element._deleted);
        assert.isNotOk(element.section.value.deleted);
      });

      test('removing an added permission', () => {
        element.editing = true;
        assert.equal(element._permissions.length, 1);
        element.shadowRoot
            .querySelector('gr-permission').dispatchEvent(
                new CustomEvent('added-permission-removed', {
                  composed: true, bubbles: true,
                }));
        flush();
        assert.equal(element._permissions.length, 0);
      });

      test('remove an added section', () => {
        const removeStub = sinon.stub();
        element.addEventListener('added-section-removed', removeStub);
        element.editing = true;
        element.section.value.added = true;
        MockInteractions.tap(element.$.deleteBtn);
        assert.isTrue(removeStub.called);
      });
    });
  });
});
