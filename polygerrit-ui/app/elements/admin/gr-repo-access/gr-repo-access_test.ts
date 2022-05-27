/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {GrAccessSection} from '../gr-access-section/gr-access-section';
import {GrPermission} from '../gr-permission/gr-permission';
import {createChange} from '../../../test/test-data-generators';
import {fixture, html} from '@open-wc/testing-helpers';

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
    element = await fixture<GrRepoAccess>(html`
      <gr-repo-access></gr-repo-access>
    `);
    stubRestApi('getAccount').returns(Promise.resolve(undefined));
    repoStub = stubRestApi('getRepo').returns(Promise.resolve(repoRes));
    element.loading = false;
    element.ownerOf = [];
    element.canUpload = false;
    await element.updateComplete;
  });

  test('_repoChanged called when repo name changes', async () => {
    const repoChangedStub = sinon.stub(element, '_repoChanged');
    element.repo = 'New Repo' as RepoName;
    await element.updateComplete;
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
    assert.isNotOk(element.inheritsFrom);
    assert.deepEqual(element.local, accessRes.local);
    assert.deepEqual(
      element.sections,
      toSortedPermissionsArray(accessRes.local)
    );
    assert.deepEqual(element.labels, repoRes.labels);
    assert.equal(
      getComputedStyle(queryAndAssert<HTMLDivElement>(element, '.weblinks'))
        .display,
      'block'
    );

    await element._repoChanged('Another New Repo' as RepoName);
    assert.deepEqual(
      element.sections,
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

  test('computeParentHref', () => {
    element.inheritsFrom!.name = 'test-repo' as RepoName;
    assert.equal(element.computeParentHref(), '/admin/repos/test-repo,access');
  });

  test('computeMainClass', () => {
    element.ownerOf = ['refs/*'] as GitRef[];
    element.editing = false;
    element.canUpload = false;
    assert.equal(element.computeMainClass(), 'admin');
    element.editing = true;
    assert.equal(element.computeMainClass(), 'admin editing');
    element.ownerOf = [];
    element.editing = false;
    assert.equal(element.computeMainClass(), '');
    element.editing = true;
    assert.equal(element.computeMainClass(), 'editing');
  });

  test('inherit section', async () => {
    element.local = {};
    element.ownerOf = [];
    const computeParentHrefStub = sinon.stub(element, 'computeParentHref');
    await element.updateComplete;

    // Nothing should appear when no inherit from and not in edit mode.
    assert.equal(
      getComputedStyle(
        queryAndAssert<HTMLHeadingElement>(element, '#inheritsFrom')
      ).display,
      'none'
    );
    // When in edit mode, the autocomplete should appear.
    element.editing = true;
    // When editing, the autocomplete should still not be shown.
    assert.equal(
      getComputedStyle(
        queryAndAssert<HTMLHeadingElement>(element, '#inheritsFrom')
      ).display,
      'none'
    );

    element.editing = false;
    element.inheritsFrom = {
      id: '1234' as UrlEncodedRepoName,
      name: 'another-repo' as RepoName,
    };
    await element.updateComplete;

    // When there is a parent project, the link should be displayed.
    assert.notEqual(
      getComputedStyle(
        queryAndAssert<HTMLHeadingElement>(element, '#inheritsFrom')
      ).display,
      'none'
    );
    assert.notEqual(
      getComputedStyle(
        queryAndAssert<HTMLAnchorElement>(element, '#inheritFromName')
      ).display,
      'none'
    );
    assert.equal(
      getComputedStyle(
        queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
      ).display,
      'none'
    );
    assert.isTrue(computeParentHrefStub.called);
    element.editing = true;
    await element.updateComplete;
    // When editing, the autocomplete should be shown.
    assert.notEqual(
      getComputedStyle(
        queryAndAssert<HTMLHeadingElement>(element, '#inheritsFrom')
      ).display,
      'none'
    );
    assert.equal(
      getComputedStyle(
        queryAndAssert<HTMLAnchorElement>(element, '#inheritFromName')
      ).display,
      'none'
    );
    assert.notEqual(
      getComputedStyle(
        queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
      ).display,
      'none'
    );
  });

  test('handleUpdateInheritFrom', async () => {
    element.inheritFromFilter = 'foo bar baz' as RepoName;
    await element.updateComplete;
    element.handleUpdateInheritFrom({
      detail: {value: 'abc+123'},
    } as CustomEvent);
    await element.updateComplete;
    assert.isOk(element.inheritsFrom);
    assert.equal(element.inheritsFrom!.id, 'abc+123');
    assert.equal(element.inheritsFrom!.name, 'foo bar baz' as RepoName);
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
      element.inheritsFrom = {
        id: 'test-project' as UrlEncodedRepoName,
      };
      await element.updateComplete;
      assert.equal(
        getComputedStyle(
          queryAndAssert<GrAutocomplete>(element, '#editInheritFromInput')
        ).display,
        'none'
      );

      queryAndAssert<GrButton>(element, '#editBtn').click();
      await element.updateComplete;

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
      element.local = JSON.parse(JSON.stringify(accessRes.local));
      element.ownerOf = [];
      element.sections = toSortedPermissionsArray(element.local);
      element.groups = JSON.parse(JSON.stringify(accessRes.groups));
      element.capabilities = JSON.parse(JSON.stringify(capabilitiesRes));
      element.labels = JSON.parse(JSON.stringify(repoRes.labels));
      await element.updateComplete;
    });

    test('removing an added section', async () => {
      element.editing = true;
      await element.updateComplete;
      assert.equal(element.sections!.length, 1);
      queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      ).dispatchEvent(
        new CustomEvent('added-section-removed', {
          composed: true,
          bubbles: true,
        })
      );
      await element.updateComplete;
      assert.equal(element.sections!.length, 0);
    });

    test('button visibility for non ref owner', async () => {
      assert.equal(
        getComputedStyle(queryAndAssert<GrButton>(element, '#saveReviewBtn'))
          .display,
        'none'
      );
      assert.equal(
        getComputedStyle(queryAndAssert<GrButton>(element, '#editBtn')).display,
        'none'
      );
    });

    test('button visibility for non ref owner with upload privilege', async () => {
      element.canUpload = true;
      await element.updateComplete;
      testEditSaveCancelBtns(false, true);
    });

    test('button visibility for ref owner', async () => {
      element.ownerOf = ['refs/for/*'] as GitRef[];
      await element.updateComplete;
      testEditSaveCancelBtns(true, false);
    });

    test('button visibility for ref owner and upload', async () => {
      element.ownerOf = ['refs/for/*'] as GitRef[];
      element.canUpload = true;
      await element.updateComplete;
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
      await element.updateComplete;
      assert.isTrue(handleAccessModifiedSpy.called);
    });

    test('_handleAccessModified called when parent changes', async () => {
      element.inheritsFrom = {
        id: 'test-project' as UrlEncodedRepoName,
      };
      await element.updateComplete;
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
      await element.updateComplete;
      assert.isTrue(handleAccessModifiedSpy.called);
    });

    test('handleSaveForReview', async () => {
      const saveStub = stubRestApi('setRepoAccessRightsForReview');
      sinon.stub(element, 'computeAddAndRemove').returns({
        add: {},
        remove: {},
      });
      element.handleSaveForReview(new Event('test'));
      await element.updateComplete;
      assert.isFalse(saveStub.called);
    });

    test('recursivelyRemoveDeleted', () => {
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
      element.recursivelyRemoveDeleted(obj);
      assert.deepEqual(obj, expectedResult);
    });

    test('recursivelyUpdateAddRemoveObj on new added section', () => {
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
      element.recursivelyUpdateAddRemoveObj(obj, updateObj);
      assert.deepEqual(updateObj, expectedResult);
    });

    test('handleSaveForReview with no changes', () => {
      assert.deepEqual(element.computeAddAndRemove(), {add: {}, remove: {}});
    });

    test('handleSaveForReview parent change', async () => {
      element.inheritsFrom = {
        id: 'test-project' as UrlEncodedRepoName,
      };
      element.originalInheritsFrom = {
        id: 'test-project-original' as UrlEncodedRepoName,
      };
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), {
        parent: 'test-project',
        add: {},
        remove: {},
      });
    });

    test('handleSaveForReview new parent with spaces', async () => {
      element.inheritsFrom = {
        id: 'spaces+in+project+name' as UrlEncodedRepoName,
      };
      element.originalInheritsFrom = {id: 'old-project' as UrlEncodedRepoName};
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), {
        parent: 'spaces in project name',
        add: {},
        remove: {},
      });
    });

    test('handleSaveForReview rules', async () => {
      // Delete a rule.
      element.local!['refs/*'].permissions.owner.rules[123].deleted = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Undo deleting a rule.
      delete element.local!['refs/*'].permissions.owner.rules[123].deleted;

      // Modify a rule.
      element.local!['refs/*'].permissions.owner.rules[123].modified = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
    });

    test('computeAddAndRemove permissions', async () => {
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
      await queryAndAssert<GrPermission>(
        grAccessSection,
        'gr-permission'
      ).handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Remove the added rule.
      delete element.local!['refs/*'].permissions.owner.rules.Maintainers;

      // Delete a permission.
      element.local!['refs/*'].permissions.owner.deleted = true;
      await element.updateComplete;

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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Undo delete permission.
      delete element.local!['refs/*'].permissions.owner.deleted;

      // Modify a permission.
      element.local!['refs/*'].permissions.owner.modified = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
    });

    test('computeAddAndRemove sections', async () => {
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
      ).handleAddPermission();
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

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
      await queryAll<GrPermission>(
        grAccessSection,
        'gr-permission'
      )[2].handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Modify a section reference.
      element.local!['refs/*'].updatedId = 'refs/for/bar';
      element.local!['refs/*'].modified = true;
      await element.updateComplete;

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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Delete a section.
      element.local!['refs/*'].deleted = true;
      await element.updateComplete;
      expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
    });

    test('computeAddAndRemove new section', async () => {
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
      queryAndAssert<GrButton>(element, '#addReferenceBtn').click();
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

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
      newSection.handleAddPermission();
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

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

      await queryAndAssert<GrPermission>(
        newSection,
        'gr-permission'
      ).handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Modify a the reference from the default value.
      element.local!['refs/for/*'].updatedId = 'refs/for/new';
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
    });

    test('computeAddAndRemove combinations', async () => {
      // Modify rule and delete permission that it is inside of.
      element.local!['refs/*'].permissions.owner.rules[123].modified = true;
      element.local!['refs/*'].permissions.owner.deleted = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
      // Delete rule and delete permission that it is inside of.
      element.local!['refs/*'].permissions.owner.rules[123].modified = false;
      element.local!['refs/*'].permissions.owner.rules[123].deleted = true;
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Also modify a different rule inside of another permission.
      element.local!['refs/*'].permissions.read.modified = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
      // Modify both permissions with an exclusive bit. Owner is still
      // deleted.
      element.local!['refs/*'].permissions.owner.exclusive = true;
      element.local!['refs/*'].permissions.owner.modified = true;
      element.local!['refs/*'].permissions.read.exclusive = true;
      element.local!['refs/*'].permissions.read.modified = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Add a rule to the existing permission;
      const grAccessSection = queryAndAssert<GrAccessSection>(
        element,
        'gr-access-section'
      );
      await queryAll<GrPermission>(
        grAccessSection,
        'gr-permission'
      )[1].handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);

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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Change one of the refs
      element.local!['refs/*'].updatedId = 'refs/for/bar';
      element.local!['refs/*'].modified = true;
      await element.updateComplete;

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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      expectedInput = {
        add: {},
        remove: {
          'refs/*': {
            permissions: {},
          },
        },
      };
      element.local!['refs/*'].deleted = true;
      await element.updateComplete;
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Add a new section.
      queryAndAssert<GrButton>(element, '#addReferenceBtn').click();
      await element.updateComplete;
      let newSection = queryAll<GrAccessSection>(
        element,
        'gr-access-section'
      )[1];
      newSection.handleAddPermission();
      await element.updateComplete;
      await queryAndAssert<GrPermission>(
        newSection,
        'gr-permission'
      ).handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      // Modify a the reference from the default value.
      element.local!['refs/for/*'].updatedId = 'refs/for/new';
      await element.updateComplete;

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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Modify newly added rule inside new ref.
      element.local!['refs/for/*'].permissions['label-Code-Review'].rules[
        'Maintainers'
      ].modified = true;
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);

      // Add a second new section.
      queryAndAssert<GrButton>(element, '#addReferenceBtn').click();
      await element.updateComplete;
      newSection = queryAll<GrAccessSection>(element, 'gr-access-section')[2];
      newSection.handleAddPermission();
      await element.updateComplete;
      await queryAndAssert<GrPermission>(
        newSection,
        'gr-permission'
      ).handleAddRuleItem({
        detail: {value: 'Maintainers'},
      } as AutocompleteCommitEvent);
      // Modify a the reference from the default value.
      element.local!['refs/for/**'].updatedId = 'refs/for/new2';
      await element.updateComplete;
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
      assert.deepEqual(element.computeAddAndRemove(), expectedInput);
    });

    test('Unsaved added refs are discarded when edit cancelled', async () => {
      // Unsaved changes are discarded when editing is cancelled.
      queryAndAssert<GrButton>(element, '#editBtn').click();
      await element.updateComplete;
      assert.equal(element.sections!.length, 1);
      assert.equal(Object.keys(element.local!).length, 1);
      queryAndAssert<GrButton>(element, '#addReferenceBtn').click();
      await element.updateComplete;
      assert.equal(element.sections!.length, 2);
      assert.equal(Object.keys(element.local!).length, 2);
      queryAndAssert<GrButton>(element, '#editBtn').click();
      await element.updateComplete;
      assert.equal(element.sections!.length, 1);
      assert.equal(Object.keys(element.local!).length, 1);
    });

    test('handleSave', async () => {
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
      sinon.stub(element, 'computeAddAndRemove').returns(repoAccessInput);

      element.modified = true;
      queryAndAssert<GrButton>(element, '#saveBtn').click();
      await element.updateComplete;
      assert.equal(
        queryAndAssert<GrButton>(element, '#saveBtn').hasAttribute('loading'),
        true
      );
      resolver!({status: 200} as Response);
      await element.updateComplete;
      assert.isTrue(saveStub.called);
      assert.isTrue(navigateToChangeStub.notCalled);
    });

    test('handleSaveForReview', async () => {
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
      sinon.stub(element, 'computeAddAndRemove').returns(repoAccessInput);

      element.modified = true;
      queryAndAssert<GrButton>(element, '#saveReviewBtn').click();
      await element.updateComplete;
      assert.equal(
        queryAndAssert<GrButton>(element, '#saveReviewBtn').hasAttribute(
          'loading'
        ),
        true
      );
      resolver!(createChange());
      await element.updateComplete;
      assert.isTrue(saveForReviewStub.called);
      assert.isTrue(
        navigateToChangeStub.lastCall.calledWithExactly(createChange())
      );
    });
  });
});
