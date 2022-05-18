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

import '../../../test/common-test-setup-karma';
import './gr-access-section';
import {
  AccessPermissions,
  toSortedPermissionsArray,
} from '../../../utils/access-util';
import {GrAccessSection} from './gr-access-section';
import {GitRef} from '../../../types/common';
import {queryAndAssert} from '../../../utils/common-util';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html} from '@open-wc/testing-helpers';

suite('gr-access-section tests', () => {
  let element: GrAccessSection;

  setup(async () => {
    element = await fixture<GrAccessSection>(html`
      <gr-access-section></gr-access-section>
    `);
  });

  suite('unit tests', () => {
    setup(async () => {
      element.section = {
        id: 'refs/*' as GitRef,
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
            '-1': 'I would prefer this is not submitted as is',
            '-2': 'This shall not be submitted',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      };
      element.updateSection();
      await element.updateComplete;
    });

    test('updateSection', () => {
      // updateSection was called in setup, so just make assertions.
      const expectedPermissions = [
        {
          id: 'read' as GitRef,
          value: {
            rules: {},
          },
        },
      ];
      assert.deepEqual(element.permissions, expectedPermissions);
      assert.equal(element.originalId, element.section!.id);
    });

    test('computeLabelOptions', () => {
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

      assert.deepEqual(element.computeLabelOptions(), expectedLabelOptions);
    });

    test('handleAccessSaved', () => {
      assert.equal(element.originalId, 'refs/*' as GitRef);
      element.section!.id = 'refs/for/bar' as GitRef;
      element.handleAccessSaved();
      assert.equal(element.originalId, 'refs/for/bar' as GitRef);
    });

    test('computePermissions', () => {
      const capabilities = {
        push: {
          id: '',
          name: '',
          rules: {},
        },
        read: {
          id: '',
          name: '',
          rules: {},
        },
      };

      const expectedPermissions = [
        {
          id: 'push',
          value: {
            id: '',
            name: '',
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

      element.section = {
        id: 'refs/*' as GitRef,
        value: {
          permissions: {
            read: {
              rules: {},
            },
          },
        },
      };

      // For global capabilities, just return the sorted array filtered by
      // existing permissions.
      element.section = {
        id: 'GLOBAL_CAPABILITIES' as GitRef,
        value: {
          permissions: {
            read: {
              rules: {},
            },
          },
        },
      };
      element.capabilities = capabilities;
      assert.deepEqual(element.computePermissions(), expectedPermissions);

      // For everything else, include possible label values before filtering.
      element.section.id = 'refs/for/*' as GitRef;
      assert.deepEqual(
        element.computePermissions(),
        labelOptions
          .concat(toSortedPermissionsArray(AccessPermissions))
          .filter(permission => permission.id !== 'read')
      );
    });

    test('computePermissionName', () => {
      element.section = {
        id: 'GLOBAL_CAPABILITIES' as GitRef,
        value: {
          permissions: {
            read: {
              rules: {},
            },
          },
        },
      };

      let permission;

      permission = {
        id: 'administrateServer' as GitRef,
        value: {rules: {}},
      };
      assert.equal(
        element.computePermissionName(permission),
        element.capabilities![permission.id].name
      );

      permission = {
        id: 'non-existent' as GitRef,
        value: {rules: {}},
      };
      assert.isUndefined(element.computePermissionName(permission));

      element.section.id = 'refs/for/*' as GitRef;
      permission = {
        id: 'abandon' as GitRef,
        value: {rules: {}},
      };

      assert.equal(
        element.computePermissionName(permission),
        AccessPermissions[permission.id].name
      );

      element.section.id = 'refs/for/*' as GitRef;
      permission = {
        id: 'label-Code-Review' as GitRef,
        value: {
          label: 'Code-Review',
          rules: {},
        },
      };

      assert.equal(
        element.computePermissionName(permission),
        'Label Code-Review'
      );

      permission = {
        id: 'labelAs-Code-Review' as GitRef,
        value: {
          label: 'Code-Review',
          rules: {},
        },
      };

      assert.equal(
        element.computePermissionName(permission),
        'Label Code-Review(On Behalf Of)'
      );
    });

    test('computeSectionName', () => {
      // When computing the section name for an undefined name, it means a
      // new section is being added. In this case, it should default to
      // 'refs/heads/*'.
      element.editingRef = false;
      element.section!.id = '' as GitRef;
      assert.equal(element.computeSectionName(), 'Reference: refs/heads/*');
      assert.isTrue(element.editingRef);
      assert.equal(element.section!.id, 'refs/heads/*');

      // Reset editing to false.
      element.editingRef = false;
      element.section!.id = 'GLOBAL_CAPABILITIES' as GitRef;
      assert.equal(element.computeSectionName(), 'Global Capabilities');
      assert.isFalse(element.editingRef);

      element.section!.id = 'refs/for/*' as GitRef;
      assert.equal(element.computeSectionName(), 'Reference: refs/for/*');
      assert.isFalse(element.editingRef);
    });

    test('editReference', () => {
      element.editReference();
      assert.isTrue(element.editingRef);
    });

    test('computeSectionClass', () => {
      element.editingRef = false;
      element.canUpload = false;
      element.ownerOf = [];
      element.editing = false;
      element.deleted = false;
      assert.equal(element.computeSectionClass(), '');

      element.editing = true;
      assert.equal(element.computeSectionClass(), '');

      element.ownerOf = ['refs/*' as GitRef];
      assert.equal(element.computeSectionClass(), 'editing');

      element.ownerOf = [];
      element.canUpload = true;
      assert.equal(element.computeSectionClass(), 'editing');

      element.editingRef = true;
      assert.equal(element.computeSectionClass(), 'editing editingRef');

      element.deleted = true;
      assert.equal(element.computeSectionClass(), 'editing editingRef deleted');

      element.editingRef = false;
      assert.equal(element.computeSectionClass(), 'editing deleted');
    });
  });

  suite('interactive tests', () => {
    setup(() => {
      element.labels = {
        'Code-Review': {
          values: {
            ' 0': 'No score',
            '-1': 'I would prefer this is not submitted as is',
            '-2': 'This shall not be submitted',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          default_value: 0,
        },
      };
    });
    suite('Global section', () => {
      setup(async () => {
        element.section = {
          id: 'GLOBAL_CAPABILITIES' as GitRef,
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
        element.updateSection();
        await element.updateComplete;
      });

      test('classes are assigned correctly', () => {
        assert.isFalse(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('editing')
        );
        assert.isFalse(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('deleted')
        );
        assert.isTrue(
          queryAndAssert<GrButton>(element, '#editBtn').classList.contains(
            'global'
          )
        );
        element.editing = true;
        element.canUpload = true;
        element.ownerOf = [];
        assert.equal(
          getComputedStyle(queryAndAssert<GrButton>(element, '#editBtn'))
            .display,
          'none'
        );
      });
    });

    suite('Non-global section', () => {
      setup(async () => {
        element.section = {
          id: 'refs/*' as GitRef,
          value: {
            permissions: {
              read: {
                rules: {},
              },
            },
          },
        };
        element.capabilities = {};
        element.updateSection();
        await element.updateComplete;
      });

      test('classes are assigned correctly', async () => {
        assert.isFalse(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('editing')
        );
        assert.isFalse(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('deleted')
        );
        assert.isFalse(
          queryAndAssert<GrButton>(element, '#editBtn').classList.contains(
            'global'
          )
        );
        element.editing = true;
        element.canUpload = true;
        element.ownerOf = [];
        await element.updateComplete;
        assert.notEqual(
          getComputedStyle(queryAndAssert<GrButton>(element, '#editBtn'))
            .display,
          'none'
        );
      });

      test('add permission', async () => {
        element.editing = true;
        queryAndAssert<HTMLSelectElement>(element, '#permissionSelect').value =
          'label-Code-Review';
        assert.equal(element.permissions!.length, 1);
        assert.equal(Object.keys(element.section!.value.permissions).length, 1);
        queryAndAssert<GrButton>(element, '#addBtn').click();
        await element.updateComplete;

        // The permission is added to both the permissions array and also
        // the section's permission object.
        assert.equal(element.permissions!.length, 2);
        let permission;

        permission = {
          id: 'label-Code-Review' as GitRef,
          value: {
            added: true,
            label: 'Code-Review',
            rules: {},
          },
        };
        assert.equal(element.permissions!.length, 2);
        assert.deepEqual(element.permissions![1], permission);
        assert.equal(Object.keys(element.section!.value.permissions).length, 2);
        assert.deepEqual(
          element.section!.value.permissions['label-Code-Review'],
          permission.value
        );

        queryAndAssert<HTMLSelectElement>(element, '#permissionSelect').value =
          'abandon';
        queryAndAssert<GrButton>(element, '#addBtn').click();
        await element.updateComplete;

        permission = {
          id: 'abandon' as GitRef,
          value: {
            added: true,
            rules: {},
          },
        };

        assert.equal(element.permissions!.length, 3);
        assert.deepEqual(element.permissions![2], permission);
        assert.equal(Object.keys(element.section!.value.permissions).length, 3);
        assert.deepEqual(
          element.section!.value.permissions['abandon'],
          permission.value
        );

        // Unsaved changes are discarded when editing is cancelled.
        element.editing = false;
        await element.updateComplete;
        assert.equal(element.permissions!.length, 1);
        assert.equal(Object.keys(element.section!.value.permissions).length, 1);
      });

      test('edit section reference', async () => {
        element.canUpload = true;
        element.ownerOf = [];
        element.section = {
          id: 'refs/for/bar' as GitRef,
          value: {permissions: {}},
        };
        await element.updateComplete;
        assert.isFalse(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('editing')
        );
        element.editing = true;
        await element.updateComplete;
        assert.isTrue(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('editing')
        );
        assert.isFalse(element.editingRef);
        queryAndAssert<GrButton>(element, '#editBtn').click();
        element.editRefInput().bindValue = 'new/ref';
        await element.updateComplete;
        assert.equal(element.section.id, 'new/ref');
        assert.isTrue(element.editingRef);
        assert.isTrue(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('editingRef')
        );
        element.editing = false;
        await element.updateComplete;
        assert.isFalse(element.editingRef);
        assert.equal(element.section.id, 'refs/for/bar');
      });

      test('handleValueChange', async () => {
        // For an existing section.
        const modifiedHandler = sinon.stub();
        element.section = {
          id: 'refs/for/bar' as GitRef,
          value: {permissions: {}},
        };
        await element.updateComplete;
        assert.notOk(element.section.value.updatedId);
        element.section.id = 'refs/for/baz' as GitRef;
        await element.updateComplete;
        element.addEventListener('access-modified', modifiedHandler);
        assert.isNotOk(element.section.value.modified);
        element.handleValueChange();
        assert.equal(element.section.value.updatedId, 'refs/for/baz');
        assert.isTrue(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 1);
        element.section.id = 'refs/for/bar' as GitRef;
        await element.updateComplete;
        element.handleValueChange();
        assert.isFalse(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 2);

        // For a new section.
        element.section.value.added = true;
        await element.updateComplete;
        element.handleValueChange();
        assert.isFalse(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 2);
        element.section.id = 'refs/for/bar' as GitRef;
        await element.updateComplete;
        element.handleValueChange();
        assert.isFalse(element.section.value.modified);
        assert.equal(modifiedHandler.callCount, 2);
      });

      test('remove section', async () => {
        element.editing = true;
        element.canUpload = true;
        element.ownerOf = [];
        await element.updateComplete;
        assert.isFalse(element.deleted);
        assert.isNotOk(element.section!.value.deleted);
        queryAndAssert<GrButton>(element, '#deleteBtn').click();
        await element.updateComplete;
        assert.isTrue(element.deleted);
        assert.isTrue(element.section!.value.deleted);
        assert.isTrue(
          queryAndAssert<HTMLFieldSetElement>(
            element,
            '#section'
          ).classList.contains('deleted')
        );
        assert.isTrue(element.section!.value.deleted);

        queryAndAssert<GrButton>(element, '#undoRemoveBtn').click();
        await element.updateComplete;
        assert.isFalse(element.deleted);
        assert.isNotOk(element.section!.value.deleted);

        queryAndAssert<GrButton>(element, '#deleteBtn').click();
        await element.updateComplete;
        assert.isTrue(element.deleted);
        assert.isTrue(element.section!.value.deleted);
        element.editing = false;
        await element.updateComplete;
        assert.isFalse(element.deleted);
        assert.isNotOk(element.section!.value.deleted);
      });

      test('removing an added permission', async () => {
        element.editing = true;
        await element.updateComplete;
        assert.equal(element.permissions!.length, 1);
        element.shadowRoot!.querySelector('gr-permission')!.dispatchEvent(
          new CustomEvent('added-permission-removed', {
            composed: true,
            bubbles: true,
          })
        );
        await element.updateComplete;
        assert.equal(element.permissions!.length, 0);
      });

      test('remove an added section', async () => {
        const removeStub = sinon.stub();
        element.addEventListener('added-section-removed', removeStub);
        element.editing = true;
        element.section!.value.added = true;
        await element.updateComplete;
        queryAndAssert<GrButton>(element, '#deleteBtn').click();
        await element.updateComplete;
        assert.isTrue(removeStub.called);
      });
    });
  });
});
