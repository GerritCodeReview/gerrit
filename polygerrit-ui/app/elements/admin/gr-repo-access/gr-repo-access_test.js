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
import './gr-repo-access.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {toSortedPermissionsArray} from '../../../utils/access-util.js';
import {
  addListenerForTest,
  mockPromise,
  stubRestApi,
} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-repo-access');

suite('gr-repo-access tests', () => {
  let element;

  let repoStub;

  const accessRes = {
    local: {
      'refs/*': {
        permissions: {
          owner: {
            rules: {
              234: {action: 'ALLOW'},
              123: {action: 'DENY'},
            },
          },
          read: {
            rules: {
              234: {action: 'ALLOW'},
            },
          },
        },
      },
    },
    groups: {
      Administrators: {
        name: 'Administrators',
      },
      Maintainers: {
        name: 'Maintainers',
      },
    },
    config_web_links: [{
      name: 'gitiles',
      target: '_blank',
      url: 'https://my/site/+log/123/project.config',
    }],
    can_upload: true,
  };
  const accessRes2 = {
    local: {
      GLOBAL_CAPABILITIES: {
        permissions: {
          accessDatabase: {
            rules: {
              group1: {
                action: 'ALLOW',
              },
            },
          },
        },
      },
    },
  };
  const repoRes = {
    labels: {
      'Code-Review': {
        values: {
          ' 0': 'No score',
          '-1': 'I would prefer this is not merged as is',
          '-2': 'This shall not be merged',
          '+1': 'Looks good to me, but someone else must approve',
          '+2': 'Looks good to me, approved',
        },
      },
    },
  };
  const capabilitiesRes = {
    accessDatabase: {
      id: 'accessDatabase',
      name: 'Access Database',
    },
    createAccount: {
      id: 'createAccount',
      name: 'Create Account',
    },
  };
  setup(async () => {
    element = basicFixture.instantiate();
    stubRestApi('getAccount').returns(Promise.resolve(null));
    repoStub = stubRestApi('getRepo').returns(Promise.resolve(repoRes));
    element._loading = false;
    element._ownerOf = [];
    element._canUpload = false;
    await flush();
  });

  test('_repoChanged called when repo name changes', async () => {
    sinon.stub(element, '_repoChanged');
    element.repo = 'New Repo';
    await flush();
    assert.isTrue(element._repoChanged.called);
  });

  test('_repoChanged', async () => {
    const accessStub = stubRestApi(
        'getRepoAccessRights');

    accessStub.withArgs('New Repo').returns(
        Promise.resolve(JSON.parse(JSON.stringify(accessRes))));
    accessStub.withArgs('Another New Repo')
        .returns(Promise.resolve(JSON.parse(JSON.stringify(accessRes2))));
    const capabilitiesStub = stubRestApi(
        'getCapabilities');
    capabilitiesStub.returns(Promise.resolve(capabilitiesRes));

    await element._repoChanged('New Repo');
    assert.isTrue(accessStub.called);
    assert.isTrue(capabilitiesStub.called);
    assert.isTrue(repoStub.called);
    assert.isNotOk(element._inheritsFrom);
    assert.deepEqual(element._local, accessRes.local);
    assert.deepEqual(element._sections,
        toSortedPermissionsArray(accessRes.local));
    assert.deepEqual(element._labels, repoRes.labels);
    assert.equal(getComputedStyle(element.shadowRoot
        .querySelector('.weblinks')).display,
    'block');

    await element._repoChanged('Another New Repo');
    assert.deepEqual(element._sections,
        toSortedPermissionsArray(accessRes2.local));
    assert.equal(getComputedStyle(element.shadowRoot
        .querySelector('.weblinks')).display,
    'none');
  });

  test('_repoChanged when repo changes to undefined returns', async () => {
    const capabilitiesRes = {
      accessDatabase: {
        id: 'accessDatabase',
        name: 'Access Database',
      },
    };
    const accessStub = stubRestApi('getRepoAccessRights')
        .returns(Promise.resolve(JSON.parse(JSON.stringify(accessRes2))));
    const capabilitiesStub = stubRestApi(
        'getCapabilities').returns(Promise.resolve(capabilitiesRes));

    await element._repoChanged();
    assert.isFalse(accessStub.called);
    assert.isFalse(capabilitiesStub.called);
    assert.isFalse(repoStub.called);
  });

  test('_computeParentHref', () => {
    const repoName = 'test-repo';
    assert.equal(element._computeParentHref(repoName),
        '/admin/repos/test-repo,access');
  });

  test('_computeMainClass', () => {
    let ownerOf = ['refs/*'];
    const editing = true;
    const canUpload = false;
    assert.equal(element._computeMainClass(ownerOf, canUpload), 'admin');
    assert.equal(element._computeMainClass(ownerOf, canUpload, editing),
        'admin editing');
    ownerOf = [];
    assert.equal(element._computeMainClass(ownerOf, canUpload), '');
    assert.equal(element._computeMainClass(ownerOf, canUpload, editing),
        'editing');
  });

  test('inherit section', async () => {
    element._local = {};
    element._ownerOf = [];
    sinon.stub(element, '_computeParentHref');
    await flush();

    // Nothing should appear when no inherit from and not in edit mode.
    assert.equal(getComputedStyle(element.$.inheritsFrom).display, 'none');
    // The autocomplete should be hidden, and the link should be  displayed.
    assert.isFalse(element._computeParentHref.called);
    // When in edit mode, the autocomplete should appear.
    element._editing = true;
    // When editing, the autocomplete should still not be shown.
    assert.equal(getComputedStyle(element.$.inheritsFrom).display, 'none');

    element._editing = false;
    element._inheritsFrom = {
      id: '1234',
      name: 'another-repo',
    };
    await flush();

    // When there is a parent project, the link should be displayed.
    assert.notEqual(getComputedStyle(element.$.inheritsFrom).display, 'none');
    assert.notEqual(getComputedStyle(element.$.inheritFromName).display,
        'none');
    assert.equal(getComputedStyle(element.$.editInheritFromInput).display,
        'none');
    assert.isTrue(element._computeParentHref.called);
    element._editing = true;
    // When editing, the autocomplete should be shown.
    assert.notEqual(getComputedStyle(element.$.inheritsFrom).display, 'none');
    assert.equal(getComputedStyle(element.$.inheritFromName).display, 'none');
    assert.notEqual(getComputedStyle(element.$.editInheritFromInput).display,
        'none');
  });

  test('_handleUpdateInheritFrom', async () => {
    element._inheritFromFilter = 'foo bar baz';
    element._handleUpdateInheritFrom({detail: {value: 'abc+123'}});
    await flush();
    assert.isOk(element._inheritsFrom);
    assert.equal(element._inheritsFrom.id, 'abc+123');
    assert.equal(element._inheritsFrom.name, 'foo bar baz');
  });

  test('_computeLoadingClass', () => {
    assert.equal(element._computeLoadingClass(true), 'loading');
    assert.equal(element._computeLoadingClass(false), '');
  });

  test('fires page-error', async () => {
    const response = {status: 404};

    stubRestApi('getRepoAccessRights').callsFake((repoName, errFn) => {
      errFn(response);
      return Promise.resolve(undefined);
    });

    const promise = mockPromise();
    addListenerForTest(document, 'page-error', e => {
      assert.deepEqual(e.detail.response, response);
      promise.resolve();
    });

    element.repo = 'test';
    await promise;
  });

  suite('with defined sections', () => {
    const testEditSaveCancelBtns = async (
        shouldShowSave,
        shouldShowSaveReview
    ) => {
      // Edit button is visible and Save button is hidden.
      assert.equal(getComputedStyle(element.$.saveReviewBtn).display, 'none');
      assert.equal(getComputedStyle(element.$.saveBtn).display, 'none');
      assert.notEqual(getComputedStyle(element.$.editBtn).display, 'none');
      assert.equal(element.$.editBtn.innerText, 'EDIT');
      assert.equal(
          getComputedStyle(element.$.editInheritFromInput).display,
          'none'
      );
      element._inheritsFrom = {
        id: 'test-project',
      };
      await flush();
      assert.equal(
          getComputedStyle(
              element.shadowRoot.querySelector('#editInheritFromInput')
          ).display,
          'none'
      );

      MockInteractions.tap(element.$.editBtn);
      await flush();

      // Edit button changes to Cancel button, and Save button is visible but
      // disabled.
      assert.equal(element.$.editBtn.innerText, 'CANCEL');
      if (shouldShowSaveReview) {
        assert.notEqual(
            getComputedStyle(element.$.saveReviewBtn).display,
            'none'
        );
        assert.isTrue(element.$.saveReviewBtn.disabled);
      }
      if (shouldShowSave) {
        assert.notEqual(getComputedStyle(element.$.saveBtn).display, 'none');
        assert.isTrue(element.$.saveBtn.disabled);
      }
      assert.notEqual(
          getComputedStyle(
              element.shadowRoot.querySelector('#editInheritFromInput')
          ).display,
          'none'
      );

      // Save button should be enabled after access is modified
      element.dispatchEvent(
          new CustomEvent('access-modified', {
            composed: true,
            bubbles: true,
          })
      );
      if (shouldShowSaveReview) {
        assert.isFalse(element.$.saveReviewBtn.disabled);
      }
      if (shouldShowSave) {
        assert.isFalse(element.$.saveBtn.disabled);
      }
    };

    setup(async () => {
      // Create deep copies of these objects so the originals are not modified
      // by any tests.
      element._local = JSON.parse(JSON.stringify(accessRes.local));
      element._ownerOf = [];
      element._sections = toSortedPermissionsArray(element._local);
      element._groups = JSON.parse(JSON.stringify(accessRes.groups));
      element._capabilities = JSON.parse(JSON.stringify(capabilitiesRes));
      element._labels = JSON.parse(JSON.stringify(repoRes.labels));
      await flush();
    });

    test('removing an added section', async () => {
      element.editing = true;
      await flush();
      assert.equal(element._sections.length, 1);
      element.shadowRoot
          .querySelector('gr-access-section').dispatchEvent(
              new CustomEvent('added-section-removed', {
                composed: true, bubbles: true,
              }));
      await flush();
      assert.equal(element._sections.length, 0);
    });

    test('button visibility for non ref owner', () => {
      assert.equal(getComputedStyle(element.$.saveReviewBtn).display, 'none');
      assert.equal(getComputedStyle(element.$.editBtn).display, 'none');
    });

    test('button visibility for non ref owner with upload privilege',
        async () => {
          element._canUpload = true;
          await flush();
          testEditSaveCancelBtns(false, true);
        });

    test('button visibility for ref owner', async () => {
      element._ownerOf = ['refs/for/*'];
      await flush();
      testEditSaveCancelBtns(true, false);
    });

    test('button visibility for ref owner and upload', async () => {
      element._ownerOf = ['refs/for/*'];
      element._canUpload = true;
      await flush();
      testEditSaveCancelBtns(true, false);
    });

    test('_handleAccessModified called with event fired', async () => {
      sinon.spy(element, '_handleAccessModified');
      element.dispatchEvent(
          new CustomEvent('access-modified', {
            composed: true, bubbles: true,
          }));
      await flush();
      assert.isTrue(element._handleAccessModified.called);
    });

    test('_handleAccessModified called when parent changes', async () => {
      element._inheritsFrom = {
        id: 'test-project',
      };
      await flush();
      element.shadowRoot.querySelector('#editInheritFromInput').dispatchEvent(
          new CustomEvent('commit', {
            detail: {},
            composed: true, bubbles: true,
          }));
      sinon.spy(element, '_handleAccessModified');
      element.dispatchEvent(
          new CustomEvent('access-modified', {
            detail: {},
            composed: true, bubbles: true,
          }));
      await flush();
      assert.isTrue(element._handleAccessModified.called);
    });

    test('_handleSaveForReview', async () => {
      const saveStub =
          stubRestApi('setRepoAccessRightsForReview');
      sinon.stub(element, '_computeAddAndRemove').returns({
        add: {},
        remove: {},
      });
      element._handleSaveForReview();
      await flush();
      assert.isFalse(saveStub.called);
    });

    test('_recursivelyRemoveDeleted', () => {
      const obj = {
        'refs/*': {
          permissions: {
            owner: {
              rules: {
                234: {action: 'ALLOW'},
                123: {action: 'DENY', deleted: true},
              },
            },
            read: {
              deleted: true,
              rules: {
                234: {action: 'ALLOW'},
              },
            },
          },
        },
      };
      const expectedResult = {
        'refs/*': {
          permissions: {
            owner: {
              rules: {
                234: {action: 'ALLOW'},
              },
            },
          },
        },
      };
      element._recursivelyRemoveDeleted(obj);
      assert.deepEqual(obj, expectedResult);
    });

    test('_recursivelyUpdateAddRemoveObj on new added section', () => {
      const obj = {
        'refs/for/*': {
          permissions: {
            'label-Code-Review': {
              rules: {
                e798fed07afbc9173a587f876ef8760c78d240c1: {
                  min: -2,
                  max: 2,
                  action: 'ALLOW',
                  added: true,
                },
              },
              added: true,
              label: 'Code-Review',
            },
            'labelAs-Code-Review': {
              rules: {
                'ldap:gerritcodereview-eng': {
                  min: -2,
                  max: 2,
                  action: 'ALLOW',
                  added: true,
                  deleted: true,
                },
              },
              added: true,
              label: 'Code-Review',
            },
          },
          added: true,
        },
      };

      const expectedResult = {
        add: {
          'refs/for/*': {
            permissions: {
              'label-Code-Review': {
                rules: {
                  e798fed07afbc9173a587f876ef8760c78d240c1: {
                    min: -2,
                    max: 2,
                    action: 'ALLOW',
                    added: true,
                  },
                },
                added: true,
                label: 'Code-Review',
              },
              'labelAs-Code-Review': {
                rules: {},
                added: true,
                label: 'Code-Review',
              },
            },
            added: true,
          },
        },
        remove: {},
      };
      const updateObj = {add: {}, remove: {}};
      element._recursivelyUpdateAddRemoveObj(obj, updateObj);
      assert.deepEqual(updateObj, expectedResult);
    });

    test('_handleSaveForReview with no changes', () => {
      assert.deepEqual(element._computeAddAndRemove(), {add: {}, remove: {}});
    });

    test('_handleSaveForReview parent change', async () => {
      element._inheritsFrom = {
        id: 'test-project',
      };
      element._originalInheritsFrom = {
        id: 'test-project-original',
      };
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), {
        parent: 'test-project', add: {}, remove: {},
      });
    });

    test('_handleSaveForReview new parent with spaces', async () => {
      element._inheritsFrom = {id: 'spaces+in+project+name'};
      element._originalInheritsFrom = {id: 'old-project'};
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), {
        parent: 'spaces in project name', add: {}, remove: {},
      });
    });

    test('_handleSaveForReview rules', async () => {
      // Delete a rule.
      element._local['refs/*'].permissions.owner.rules[123].deleted = true;
      await flush();
      let expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {},
                },
              },
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Undo deleting a rule.
      delete element._local['refs/*'].permissions.owner.rules[123].deleted;

      // Modify a rule.
      element._local['refs/*'].permissions.owner.rules[123].modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {action: 'DENY', modified: true},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {},
                },
              },
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
    });

    test('_computeAddAndRemove permissions', async () => {
      // Add a new rule to a permission.
      let expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                  },
                },
              },
            },
          },
        },
        remove: {},
      };

      element.shadowRoot
          .querySelector('gr-access-section').shadowRoot
          .querySelector('gr-permission')
          ._handleAddRuleItem(
              {detail: {value: 'Maintainers'}});

      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Remove the added rule.
      delete element._local['refs/*'].permissions.owner.rules.Maintainers;

      // Delete a permission.
      element._local['refs/*'].permissions.owner.deleted = true;
      await flush();
      expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {
              owner: {rules: {}},
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Undo delete permission.
      delete element._local['refs/*'].permissions.owner.deleted;

      // Modify a permission.
      element._local['refs/*'].permissions.owner.modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              owner: {
                modified: true,
                rules: {
                  234: {action: 'ALLOW'},
                  123: {action: 'DENY'},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {rules: {}},
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
    });

    test('_computeAddAndRemove sections', async () => {
      // Add a new permission to a section
      let expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {},
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {},
      };
      element.shadowRoot
          .querySelector('gr-access-section')._handleAddPermission();
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Add a new rule to the new permission.
      expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    min: -2,
                    max: 2,
                    action: 'ALLOW',
                    added: true,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {},
      };
      const newPermission =
          dom(element.shadowRoot
              .querySelector('gr-access-section').root).querySelectorAll(
              'gr-permission')[2];
      newPermission._handleAddRuleItem(
          {detail: {value: 'Maintainers'}});
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Modify a section reference.
      element._local['refs/*'].updatedId = 'refs/for/bar';
      element._local['refs/*'].modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/for/bar': {
            modified: true,
            updatedId: 'refs/for/bar',
            permissions: {
              'owner': {
                rules: {
                  234: {action: 'ALLOW'},
                  123: {action: 'DENY'},
                },
              },
              'read': {
                rules: {
                  234: {action: 'ALLOW'},
                },
              },
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    min: -2,
                    max: 2,
                    action: 'ALLOW',
                    added: true,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Delete a section.
      element._local['refs/*'].deleted = true;
      await flush();
      expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
    });

    test('_computeAddAndRemove new section', async () => {
      // Add a new permission to a section
      let expectedInput = {
        add: {
          'refs/for/*': {
            added: true,
            permissions: {},
          },
        },
        remove: {},
      };
      MockInteractions.tap(element.$.addReferenceBtn);
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      expectedInput = {
        add: {
          'refs/for/*': {
            added: true,
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {},
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {},
      };
      const newSection = dom(element.root)
          .querySelectorAll('gr-access-section')[1];
      newSection._handleAddPermission();
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Add rule to the new permission.
      expectedInput = {
        add: {
          'refs/for/*': {
            added: true,
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                    max: 2,
                    min: -2,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {},
      };

      newSection.shadowRoot
          .querySelector('gr-permission')._handleAddRuleItem(
              {detail: {value: 'Maintainers'}});
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Modify a the reference from the default value.
      element._local['refs/for/*'].updatedId = 'refs/for/new';
      await flush();
      expectedInput = {
        add: {
          'refs/for/new': {
            added: true,
            updatedId: 'refs/for/new',
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                    max: 2,
                    min: -2,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {},
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
    });

    test('_computeAddAndRemove combinations', async () => {
      // Modify rule and delete permission that it is inside of.
      element._local['refs/*'].permissions.owner.rules[123].modified = true;
      element._local['refs/*'].permissions.owner.deleted = true;
      await flush();
      let expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {
              owner: {rules: {}},
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
      // Delete rule and delete permission that it is inside of.
      element._local['refs/*'].permissions.owner.rules[123].modified = false;
      element._local['refs/*'].permissions.owner.rules[123].deleted = true;
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Also modify a different rule inside of another permission.
      element._local['refs/*'].permissions.read.modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              read: {
                modified: true,
                rules: {
                  234: {action: 'ALLOW'},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {rules: {}},
              read: {rules: {}},
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
      // Modify both permissions with an exclusive bit. Owner is still
      // deleted.
      element._local['refs/*'].permissions.owner.exclusive = true;
      element._local['refs/*'].permissions.owner.modified = true;
      element._local['refs/*'].permissions.read.exclusive = true;
      element._local['refs/*'].permissions.read.modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              read: {
                exclusive: true,
                modified: true,
                rules: {
                  234: {action: 'ALLOW'},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {rules: {}},
              read: {rules: {}},
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Add a rule to the existing permission;
      const readPermission =
          dom(element.shadowRoot
              .querySelector('gr-access-section').root).querySelectorAll(
              'gr-permission')[1];
      readPermission._handleAddRuleItem(
          {detail: {value: 'Maintainers'}});
      await flush();

      expectedInput = {
        add: {
          'refs/*': {
            permissions: {
              read: {
                exclusive: true,
                modified: true,
                rules: {
                  234: {action: 'ALLOW'},
                  Maintainers: {action: 'ALLOW', added: true},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {rules: {}},
              read: {rules: {}},
            },
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Change one of the refs
      element._local['refs/*'].updatedId = 'refs/for/bar';
      element._local['refs/*'].modified = true;
      await flush();

      expectedInput = {
        add: {
          'refs/for/bar': {
            modified: true,
            updatedId: 'refs/for/bar',
            permissions: {
              read: {
                exclusive: true,
                modified: true,
                rules: {
                  234: {action: 'ALLOW'},
                  Maintainers: {action: 'ALLOW', added: true},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      element._local['refs/*'].deleted = true;
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Add a new section.
      MockInteractions.tap(element.$.addReferenceBtn);
      let newSection = dom(element.root)
          .querySelectorAll('gr-access-section')[1];
      newSection._handleAddPermission();
      await flush();
      newSection.shadowRoot
          .querySelector('gr-permission')._handleAddRuleItem(
              {detail: {value: 'Maintainers'}});
      // Modify a the reference from the default value.
      element._local['refs/for/*'].updatedId = 'refs/for/new';
      await flush();

      expectedInput = {
        add: {
          'refs/for/new': {
            added: true,
            updatedId: 'refs/for/new',
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                    max: 2,
                    min: -2,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Modify newly added rule inside new ref.
      element._local['refs/for/*'].permissions['label-Code-Review'].
          rules['Maintainers'].modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/for/new': {
            added: true,
            updatedId: 'refs/for/new',
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                    modified: true,
                    max: 2,
                    min: -2,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Add a second new section.
      MockInteractions.tap(element.$.addReferenceBtn);
      await flush();
      newSection = dom(element.root)
          .querySelectorAll('gr-access-section')[2];
      newSection._handleAddPermission();
      await flush();
      newSection.shadowRoot
          .querySelector('gr-permission')._handleAddRuleItem(
              {detail: {value: 'Maintainers'}});
      // Modify a the reference from the default value.
      element._local['refs/for/**'].updatedId = 'refs/for/new2';
      await flush();
      expectedInput = {
        add: {
          'refs/for/new': {
            added: true,
            updatedId: 'refs/for/new',
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                    modified: true,
                    max: 2,
                    min: -2,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
          'refs/for/new2': {
            added: true,
            updatedId: 'refs/for/new2',
            permissions: {
              'label-Code-Review': {
                added: true,
                rules: {
                  Maintainers: {
                    action: 'ALLOW',
                    added: true,
                    max: 2,
                    min: -2,
                  },
                },
                label: 'Code-Review',
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);
    });

    test('Unsaved added refs are discarded when edit cancelled', async () => {
      // Unsaved changes are discarded when editing is cancelled.
      MockInteractions.tap(element.$.editBtn);
      await flush();
      assert.equal(element._sections.length, 1);
      assert.equal(Object.keys(element._local).length, 1);
      MockInteractions.tap(element.$.addReferenceBtn);
      await flush();
      assert.equal(element._sections.length, 2);
      assert.equal(Object.keys(element._local).length, 2);
      MockInteractions.tap(element.$.editBtn);
      await flush();
      assert.equal(element._sections.length, 1);
      assert.equal(Object.keys(element._local).length, 1);
    });

    test('_handleSave', async () => {
      const repoAccessInput = {
        add: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {action: 'DENY', modified: true},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {},
                },
              },
            },
          },
        },
      };
      stubRestApi('getRepoAccessRights').returns(
          Promise.resolve(JSON.parse(JSON.stringify(accessRes))));
      sinon.stub(GerritNav, 'navigateToChange');
      let resolver;
      const saveStub = stubRestApi(
          'setRepoAccessRights')
          .returns(new Promise(r => resolver = r));

      element.repo = 'test-repo';
      sinon.stub(element, '_computeAddAndRemove').returns(repoAccessInput);

      element._modified = true;
      MockInteractions.tap(element.$.saveBtn);
      await flush();
      assert.equal(element.$.saveBtn.hasAttribute('loading'), true);
      resolver({_number: 1});
      await flush();
      assert.isTrue(saveStub.called);
      assert.isTrue(GerritNav.navigateToChange.notCalled);
    });

    test('_handleSaveForReview', async () => {
      const repoAccessInput = {
        add: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {action: 'DENY', modified: true},
                },
              },
            },
          },
        },
        remove: {
          'refs/*': {
            permissions: {
              owner: {
                rules: {
                  123: {},
                },
              },
            },
          },
        },
      };
      stubRestApi('getRepoAccessRights').returns(
          Promise.resolve(JSON.parse(JSON.stringify(accessRes))));
      sinon.stub(GerritNav, 'navigateToChange');
      let resolver;
      const saveForReviewStub = stubRestApi(
          'setRepoAccessRightsForReview')
          .returns(new Promise(r => resolver = r));

      element.repo = 'test-repo';
      sinon.stub(element, '_computeAddAndRemove').returns(repoAccessInput);

      element._modified = true;
      MockInteractions.tap(element.$.saveReviewBtn);
      await flush();
      assert.equal(element.$.saveReviewBtn.hasAttribute('loading'), true);
      resolver({_number: 1});
      await flush();
      assert.isTrue(saveForReviewStub.called);
      assert.isTrue(GerritNav.navigateToChange
          .lastCall.calledWithExactly({_number: 1}, {}));
    });
  });
});

