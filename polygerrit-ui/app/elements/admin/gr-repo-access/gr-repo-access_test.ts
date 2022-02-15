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
import './gr-repo-access';
import {GrRepoAccess} from './gr-repo-access';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {toSortedPermissionsArray} from '../../../utils/access-util';
import {
  addListenerForTest,
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {
  ChangeInfo,
  GitRef,
  RepoName,
  UrlEncodedRepoName,
} from '../../../types/common';
import {PermissionAction} from '../../../constants/constants';
import {PageErrorEvent} from '../../../types/events';
import {GrButton} from '../../shared/gr-button/gr-button';
import {
  AutocompleteCommitEvent,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {GrAccessSection} from '../gr-access-section/gr-access-section';
import {GrPermission} from '../gr-permission/gr-permission';
import {createChange} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-repo-access');

suite('gr-repo-access tests', () => {
  let element: GrRepoAccess;

  let repoStub: sinon.SinonStub;

  const accessRes = {
    local: {
      'refs/*': {
        permissions: {
          owner: {
            rules: {
              234: {action: PermissionAction.ALLOW},
              123: {action: PermissionAction.DENY},
            },
          },
          read: {
            rules: {
              234: {action: PermissionAction.ALLOW},
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
    config_web_links: [
      {
        name: 'gitiles',
        target: '_blank',
        url: 'https://my/site/+log/123/project.config',
      },
    ],
    can_upload: true,
  };
  const accessRes2 = {
    local: {
      GLOBAL_CAPABILITIES: {
        permissions: {
          accessDatabase: {
            rules: {
              group1: {
                action: PermissionAction.ALLOW,
              },
            },
          },
        },
      },
    },
  };
  const repoRes = {
    id: '' as UrlEncodedRepoName,
    labels: {
      'Code-Review': {
        values: {
          '0': 'No score',
          '-1': 'I would prefer this is not submitted as is',
          '-2': 'This shall not be submitted',
          '+1': 'Looks good to me, but someone else must approve',
          '+2': 'Looks good to me, approved',
        },
        default_value: 0,
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
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    repoStub = stubRestApi('getRepo').returns(Promise.resolve(repoRes));
    element._loading = false;
    element._ownerOf = [];
    element._canUpload = false;
    await flush();
  });

  test('_repoChanged called when repo name changes', async () => {
    const repoChangedStub = sinon.stub(element, '_repoChanged');
    element.repo = 'New Repo' as RepoName;
    await flush();
    assert.isTrue(repoChangedStub.called);
  });

  test('_repoChanged', async () => {
    const accessStub = stubRestApi('getRepoAccessRights');

    accessStub
      .withArgs('New Repo' as RepoName)
      .returns(Promise.resolve(JSON.parse(JSON.stringify(accessRes))));
    accessStub
      .withArgs('Another New Repo' as RepoName)
      .returns(Promise.resolve(JSON.parse(JSON.stringify(accessRes2))));
    const capabilitiesStub = stubRestApi('getCapabilities');
    capabilitiesStub.returns(Promise.resolve(capabilitiesRes));

    await element._repoChanged('New Repo' as RepoName);
    assert.isTrue(accessStub.called);
    assert.isTrue(capabilitiesStub.called);
    assert.isTrue(repoStub.called);
    assert.isNotOk(element._inheritsFrom);
    assert.deepEqual(element._local, accessRes.local);
    assert.deepEqual(
      element._sections,
      toSortedPermissionsArray(accessRes.local)
    );
    assert.deepEqual(element._labels, repoRes.labels);
    assert.equal(
      getComputedStyle(queryAndAssert<HTMLDivElement>(element, '.weblinks'))
        .display,
      'block'
    );

    await element._repoChanged('Another New Repo' as RepoName);
    assert.deepEqual(
      element._sections,
      toSortedPermissionsArray(accessRes2.local)
    );
    assert.equal(
      getComputedStyle(queryAndAssert<HTMLDivElement>(element, '.weblinks'))
        .display,
      'none'
    );
  });

  test('_repoChanged when repo changes to undefined returns', async () => {
    const capabilitiesRes = {
      accessDatabase: {
        id: 'accessDatabase',
        name: 'Access Database',
      },
    };
    const accessStub = stubRestApi('getRepoAccessRights').returns(
      Promise.resolve(JSON.parse(JSON.stringify(accessRes2)))
    );
    const capabilitiesStub = stubRestApi('getCapabilities').returns(
      Promise.resolve(capabilitiesRes)
    );

    await element._repoChanged();
    assert.isFalse(accessStub.called);
    assert.isFalse(capabilitiesStub.called);
    assert.isFalse(repoStub.called);
  });

  test('_computeParentHref', () => {
    assert.equal(
      element._computeParentHref('test-repo' as RepoName),
      '/admin/repos/test-repo,access'
    );
  });

  test('_computeMainClass', () => {
    let ownerOf = ['refs/*'] as GitRef[];
    const editing = true;
    const canUpload = false;
    assert.equal(element._computeMainClass(ownerOf, canUpload, false), 'admin');
    assert.equal(
      element._computeMainClass(ownerOf, canUpload, editing),
      'admin editing'
    );
    ownerOf = [];
    assert.equal(element._computeMainClass(ownerOf, canUpload, false), '');
    assert.equal(
      element._computeMainClass(ownerOf, canUpload, editing),
      'editing'
    );
  });

  test('inherit section', async () => {
    element._local = {};
    element._ownerOf = [];
    const computeParentHrefStub = sinon.stub(element, '_computeParentHref');
    await flush();

    // Nothing should appear when no inherit from and not in edit mode.
    assert.equal(getComputedStyle(element.$.inheritsFrom).display, 'none');
    // The autocomplete should be hidden, and the link should be  displayed.
    assert.isFalse(computeParentHrefStub.called);
    // When in edit mode, the autocomplete should appear.
    element._editing = true;
    // When editing, the autocomplete should still not be shown.
    assert.equal(getComputedStyle(element.$.inheritsFrom).display, 'none');

    element._editing = false;
    element._inheritsFrom = {
      id: '1234' as UrlEncodedRepoName,
      name: 'another-repo' as RepoName,
    };
    await flush();

    // When there is a parent project, the link should be displayed.
    assert.notEqual(getComputedStyle(element.$.inheritsFrom).display, 'none');
    assert.notEqual(
      getComputedStyle(element.$.inheritFromName).display,
      'none'
    );
    assert.equal(
      getComputedStyle(
        queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
      ).display,
      'none'
    );
    assert.isTrue(computeParentHrefStub.called);
    element._editing = true;
    // When editing, the autocomplete should be shown.
    assert.notEqual(getComputedStyle(element.$.inheritsFrom).display, 'none');
    assert.equal(getComputedStyle(element.$.inheritFromName).display, 'none');
    assert.notEqual(
      getComputedStyle(
        queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
      ).display,
      'none'
    );
  });

  test('_handleUpdateInheritFrom', async () => {
    element._inheritFromFilter = 'foo bar baz' as RepoName;
    element._handleUpdateInheritFrom({
      detail: {value: 'abc+123'},
    } as CustomEvent);
    await flush();
    assert.isOk(element._inheritsFrom);
    assert.equal(element._inheritsFrom!.id, 'abc+123');
    assert.equal(element._inheritsFrom!.name, 'foo bar baz' as RepoName);
  });

  test('_computeLoadingClass', () => {
    assert.equal(element._computeLoadingClass(true), 'loading');
    assert.equal(element._computeLoadingClass(false), '');
  });

  test('fires page-error', async () => {
    const response = {status: 404} as Response;

    stubRestApi('getRepoAccessRights').callsFake((_repoName, errFn) => {
      if (errFn !== undefined) {
        errFn(response);
      }
      return Promise.resolve(undefined);
    });

    const promise = mockPromise();
    addListenerForTest(document, 'page-error', e => {
      assert.deepEqual((e as PageErrorEvent).detail.response, response);
      promise.resolve();
    });

    element.repo = 'test' as RepoName;
    await promise;
  });

  suite('with defined sections', () => {
    const testEditSaveCancelBtns = async (
      shouldShowSave: boolean,
      shouldShowSaveReview: boolean
    ) => {
      // Edit button is visible and Save button is hidden.
      assert.equal(
        getComputedStyle(queryAndAssert<GrButton>(element, '#saveReviewBtn'))
          .display,
        'none'
      );
      assert.equal(
        getComputedStyle(queryAndAssert<GrButton>(element, '#saveBtn')).display,
        'none'
      );
      assert.notEqual(
        getComputedStyle(queryAndAssert<GrButton>(element, '#editBtn')).display,
        'none'
      );
      assert.equal(
        queryAndAssert<GrButton>(element, '#editBtn').innerText,
        'EDIT'
      );
      assert.equal(
        getComputedStyle(
          queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
        ).display,
        'none'
      );
      element._inheritsFrom = {
        id: 'test-project' as UrlEncodedRepoName,
      };
      await flush();
      assert.equal(
        getComputedStyle(
          queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
        ).display,
        'none'
      );

      MockInteractions.tap(queryAndAssert<GrButton>(element, '#editBtn'));
      await flush();

      // Edit button changes to Cancel button, and Save button is visible but
      // disabled.
      assert.equal(
        queryAndAssert<GrButton>(element, '#editBtn').innerText,
        'CANCEL'
      );
      if (shouldShowSaveReview) {
        assert.notEqual(
          getComputedStyle(queryAndAssert<GrButton>(element, '#saveReviewBtn'))
            .display,
          'none'
        );
        assert.isTrue(
          queryAndAssert<GrButton>(element, '#saveReviewBtn').disabled
        );
      }
      if (shouldShowSave) {
        assert.notEqual(
          getComputedStyle(queryAndAssert<GrButton>(element, '#saveBtn'))
            .display,
          'none'
        );
        assert.isTrue(queryAndAssert<GrButton>(element, '#saveBtn').disabled);
      }
      assert.notEqual(
        getComputedStyle(
          queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
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
        assert.isFalse(
          queryAndAssert<GrButton>(element, '#saveReviewBtn').disabled
        );
      }
      if (shouldShowSave) {
        assert.isFalse(queryAndAssert<GrButton>(element, '#saveBtn').disabled);
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
      element._editing = true;
      await flush();
      assert.equal(element._sections!.length, 1);
      queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      ).dispatchEvent(
        new CustomEvent('added-section-removed', {
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      assert.equal(element._sections!.length, 0);
    });

    test('button visibility for non ref owner', () => {
      assert.equal(getComputedStyle(element.$.saveReviewBtn).display, 'none');
      assert.equal(getComputedStyle(element.$.editBtn).display, 'none');
    });

    test('button visibility for non ref owner with upload privilege', async () => {
      element._canUpload = true;
      await flush();
      testEditSaveCancelBtns(false, true);
    });

    test('button visibility for ref owner', async () => {
      element._ownerOf = ['refs/for/*'] as GitRef[];
      await flush();
      testEditSaveCancelBtns(true, false);
    });

    test('button visibility for ref owner and upload', async () => {
      element._ownerOf = ['refs/for/*'] as GitRef[];
      element._canUpload = true;
      await flush();
      testEditSaveCancelBtns(true, false);
    });

    test('_handleAccessModified called with event fired', async () => {
      const handleAccessModifiedSpy = sinon.spy(
        element,
        '_handleAccessModified'
      );
      element.dispatchEvent(
        new CustomEvent('access-modified', {
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      assert.isTrue(handleAccessModifiedSpy.called);
    });

    test('_handleAccessModified called when parent changes', async () => {
      element._inheritsFrom = {
        id: 'test-project' as UrlEncodedRepoName,
      };
      await flush();
      queryAndAssert<GrAutocomplete>(
        element,
        '#editInheritFromInput'
      ).dispatchEvent(
        new CustomEvent('commit', {
          detail: {},
          composed: true,
          bubbles: true,
        })
      );
      const handleAccessModifiedSpy = sinon.spy(
        element,
        '_handleAccessModified'
      );
      element.dispatchEvent(
        new CustomEvent('access-modified', {
          detail: {},
          composed: true,
          bubbles: true,
        })
      );
      await flush();
      assert.isTrue(handleAccessModifiedSpy.called);
    });

    test('_handleSaveForReview', async () => {
      const saveStub = stubRestApi('setRepoAccessRightsForReview');
      sinon.stub(element, '_computeAddAndRemove').returns({
        add: {},
        remove: {},
      });
      element._handleSaveForReview(new Event('test'));
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
        id: 'test-project' as UrlEncodedRepoName,
      };
      element.originalInheritsFrom = {
        id: 'test-project-original' as UrlEncodedRepoName,
      };
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), {
        parent: 'test-project',
        add: {},
        remove: {},
      });
    });

    test('_handleSaveForReview new parent with spaces', async () => {
      element._inheritsFrom = {
        id: 'spaces+in+project+name' as UrlEncodedRepoName,
      };
      element.originalInheritsFrom = {id: 'old-project' as UrlEncodedRepoName};
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), {
        parent: 'spaces in project name',
        add: {},
        remove: {},
      });
    });

    test('_handleSaveForReview rules', async () => {
      // Delete a rule.
      element._local!['refs/*'].permissions.owner.rules[123].deleted = true;
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
      delete element._local!['refs/*'].permissions.owner.rules[123].deleted;

      // Modify a rule.
      element._local!['refs/*'].permissions.owner.rules[123].modified = true;
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
      let expectedInput = {};

      expectedInput = {
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
      const grAccessSection = queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      );
      queryAndAssert<GrPermission>(
        grAccessSection,
        'gr-permission'
      )._handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);

      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Remove the added rule.
      delete element._local!['refs/*'].permissions.owner.rules.Maintainers;

      // Delete a permission.
      element._local!['refs/*'].permissions.owner.deleted = true;
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
      delete element._local!['refs/*'].permissions.owner.deleted;

      // Modify a permission.
      element._local!['refs/*'].permissions.owner.modified = true;
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
      let expectedInput = {};

      expectedInput = {
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
      queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      )._handleAddPermission();
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
      const grAccessSection = queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      );
      const newPermission = queryAll<GrPermission>(
        grAccessSection,
        'gr-permission'
      )[2];
      newPermission._handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Modify a section reference.
      element._local!['refs/*'].updatedId = 'refs/for/bar';
      element._local!['refs/*'].modified = true;
      await flush();
      expectedInput = {
        add: {
          'refs/for/bar': {
            modified: true,
            updatedId: 'refs/for/bar',
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
      element._local!['refs/*'].deleted = true;
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
      let expectedInput = {};

      expectedInput = {
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
      const newSection = queryAll<GrAccessSection>(
        element,
        'gr-access-section'
      )[1];
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

      queryAndAssert<GrPermission>(
        newSection,
        'gr-permission'
      )._handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Modify a the reference from the default value.
      element._local!['refs/for/*'].updatedId = 'refs/for/new';
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
      element._local!['refs/*'].permissions.owner.rules[123].modified = true;
      element._local!['refs/*'].permissions.owner.deleted = true;
      await flush();
      let expectedInput = {};

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
      // Delete rule and delete permission that it is inside of.
      element._local!['refs/*'].permissions.owner.rules[123].modified = false;
      element._local!['refs/*'].permissions.owner.rules[123].deleted = true;
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Also modify a different rule inside of another permission.
      element._local!['refs/*'].permissions.read.modified = true;
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
      element._local!['refs/*'].permissions.owner.exclusive = true;
      element._local!['refs/*'].permissions.owner.modified = true;
      element._local!['refs/*'].permissions.read.exclusive = true;
      element._local!['refs/*'].permissions.read.modified = true;
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
      const grAccessSection = queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      );
      const readPermission = queryAll<GrPermission>(
        grAccessSection,
        'gr-permission'
      )[1];
      readPermission._handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
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
      element._local!['refs/*'].updatedId = 'refs/for/bar';
      element._local!['refs/*'].modified = true;
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
      element._local!['refs/*'].deleted = true;
      await flush();
      assert.deepEqual(element._computeAddAndRemove(), expectedInput);

      // Add a new section.
      MockInteractions.tap(element.$.addReferenceBtn);
      let newSection = queryAll<GrAccessSection>(
        element,
        'gr-access-section'
      )[1];
      newSection._handleAddPermission();
      await flush();
      queryAndAssert<GrPermission>(
        newSection,
        'gr-permission'
      )._handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      // Modify a the reference from the default value.
      element._local!['refs/for/*'].updatedId = 'refs/for/new';
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
      element._local!['refs/for/*'].permissions['label-Code-Review'].rules[
        'Maintainers'
      ].modified = true;
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
      newSection = queryAll<GrAccessSection>(element, 'gr-access-section')[2];
      newSection._handleAddPermission();
      await flush();
      queryAndAssert<GrPermission>(
        newSection,
        'gr-permission'
      )._handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      // Modify a the reference from the default value.
      element._local!['refs/for/**'].updatedId = 'refs/for/new2';
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
      assert.equal(element._sections!.length, 1);
      assert.equal(Object.keys(element._local!).length, 1);
      MockInteractions.tap(element.$.addReferenceBtn);
      await flush();
      assert.equal(element._sections!.length, 2);
      assert.equal(Object.keys(element._local!).length, 2);
      MockInteractions.tap(element.$.editBtn);
      await flush();
      assert.equal(element._sections!.length, 1);
      assert.equal(Object.keys(element._local!).length, 1);
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
        Promise.resolve(JSON.parse(JSON.stringify(accessRes)))
      );
      const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
      let resolver: (value: Response | PromiseLike<Response>) => void;
      const saveStub = stubRestApi('setRepoAccessRights').returns(
        new Promise(r => (resolver = r))
      );

      element.repo = 'test-repo' as RepoName;
      sinon.stub(element, '_computeAddAndRemove').returns(repoAccessInput);

      element._modified = true;
      MockInteractions.tap(element.$.saveBtn);
      await flush();
      assert.equal(element.$.saveBtn.hasAttribute('loading'), true);
      resolver!({status: 200} as Response);
      await flush();
      assert.isTrue(saveStub.called);
      assert.isTrue(navigateToChangeStub.notCalled);
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
        Promise.resolve(JSON.parse(JSON.stringify(accessRes)))
      );
      const navigateToChangeStub = sinon.stub(GerritNav, 'navigateToChange');
      let resolver: (value: ChangeInfo | PromiseLike<ChangeInfo>) => void;
      const saveForReviewStub = stubRestApi(
        'setRepoAccessRightsForReview'
      ).returns(new Promise(r => (resolver = r)));

      element.repo = 'test-repo' as RepoName;
      sinon.stub(element, '_computeAddAndRemove').returns(repoAccessInput);

      element._modified = true;
      MockInteractions.tap(element.$.saveReviewBtn);
      await flush();
      assert.equal(element.$.saveReviewBtn.hasAttribute('loading'), true);
      resolver!(createChange());
      await flush();
      assert.isTrue(saveForReviewStub.called);
      assert.isTrue(
        navigateToChangeStub.lastCall.calledWithExactly(createChange())
      );
    });
  });
});
