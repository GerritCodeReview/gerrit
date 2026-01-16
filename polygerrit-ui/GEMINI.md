# Gemini Project Profile: gerrit/polygerrit-ui

This document provides a summary of the front-end development environment for the `polygerrit-ui` part of the gerrit project.

## Overview

- **Framework**: The project is built with Web Components, using Lit
- **Language**: TypeScript
- **Build Tool**: Bazel is the primary build tool for the overall project.
- **Testing**:
  - **Runner**: `@web/test-runner`
  - **Framework**: `mocha`
  - **Assertions/Mocks**: `sinon`, `@open-wc/semantic-dom-diff`
  - **Browser Testing**: `playwright`
- **Package Manager**: `yarn`

## Linting

The project uses `eslint` for linting. You can run the linter using the following commands from the root `gerrit` directory:

- `npm run eslint`: Run linter
- `npm run eslintfix:modified`: Fix lint errors in modified files
- `npm run eslintfix`: Fix lint errors in all files

**Note**: Imports should NOT have spaces around braces (e.g., `import {css} from 'lit';`, not `import { css } from 'lit';`).
**Note**: Do not use `_` prefix for private properties or variables.
**Note**: Use `// @ts-expect-error` instead of casting to `any` when suppressing TypeScript errors.

## Key Commands

The following commands should be run from the project root directory.

### Checking for TypeScript Errors

To check for TypeScript errors without running the full test suite, use the `compile` script (must be run from the **project root**, not `polygerrit-ui`):

```bash
yarn compile
```

### Running Tests

- `yarn test`: Run all tests.
- `yarn test:screenshot`: Run visual regression tests.
- `yarn test:screenshot-update`: Run visual regression tests and update baseline images.

## Running Single Tests

Running the full test suite can be slow. For a faster feedback loop during development, you can run tests for a single file. Use the `test:single:nowatch` script with the path to the test file. This will run the test once and exit.

**Command:**

```bash
yarn test:single:nowatch "**/test_file.ts"
```

If you want to run the test in watch mode, you can use `test:single`.

**Examples:**

```bash
yarn test:single:nowatch "**/gr-account-list_test.ts"
yarn test:single:nowatch "**/gr-user-suggestion_test.ts"
```

## Common Test Utilities

The `polygerrit-ui/app/test/test-utils.ts` file exports several useful utilities for testing.

### Stubbing Feature Flags

To stub feature flags in tests, use the `stubFlags` utility.

```typescript
import {stubFlags} from '../../test/test-utils';

// ... inside your test ...
stubFlags('isEnabled').returns(true);
```

## UI Elements

Here is a list of the UI elements found in `polygerrit-ui/app/elements` with a brief description of their purpose.

- `gr-access-section`: Manages a section of the access rights page for a repository.
- `gr-admin-group-list`: Displays a list of user groups in the admin section.
- `gr-admin-view`: The main view for the admin section, containing navigation and sub-views.
- `gr-confirm-delete-item-dialog`: A confirmation dialog for deleting an item.
- `gr-create-change-dialog`: A dialog for creating a new change.
- `gr-create-file-edit-dialog`: A dialog for creating a new file edit.
- `gr-create-group-dialog`: A dialog for creating a new user group.
- `gr-create-pointer-dialog`: A dialog for creating a new branch or tag.
- `gr-create-repo-dialog`: A dialog for creating a new repository.
- `gr-group-audit-log`: Displays the audit log for a user group.
- `gr-group-members`: Manages the members of a user group.
- `gr-group`: Displays and manages the details of a user group.
- `gr-permission`: Manages a single permission within an access section.
- `gr-plugin-config-array-editor`: An editor for a plugin configuration option that is an array.
- `gr-plugin-list`: Displays a list of installed plugins.
- `gr-repo-access`: Manages the access rights for a repository.
- `gr-repo-commands`: Displays repository-related commands.
- `gr-repo-dashboards`: Manages the dashboards for a repository.
- `gr-repo-detail-list`: Displays a list of branches or tags for a repository.
- `gr-repo-list`: Displays a list of repositories.
- `gr-repo-plugin-config`: Manages the plugin configuration for a repository.
- `gr-repo-submit-requirements`: Manages the submit requirements for a repository.
- `gr-repo`: Displays and manages the details of a repository.
- `gr-rule-editor`: An editor for a single access rule.
- `gr-server-info`: Displays information about the Gerrit server.
- `gr-change-list-action-bar`: An action bar for the top of a change list section.
- `gr-change-list-bulk-abandon-flow`: A flow for abandoning multiple changes in bulk.
- `gr-change-list-bulk-vote-flow`: A flow for voting on multiple changes in bulk.
- `gr-change-list-column-requirement`: Displays a single submit requirement in a change list column.
- `gr-change-list-column-requirements-summary`: Displays a summary of submit requirements in a change list column.
- `gr-change-list-copy-link-flow`: A flow for copying links to multiple changes.
- `gr-change-list-hashtag-flow`: A flow for adding hashtags to multiple changes.
- `gr-change-list-item`: A single item in a change list.
- `gr-change-list-reviewer-flow`: A flow for adding reviewers to multiple changes.
- `gr-change-list-section`: A section of a change list.
- `gr-change-list-topic-flow`: A flow for setting the topic of multiple changes.
- `gr-change-list-view`: The main view for a list of changes.
- `gr-change-list`: A list of changes.
- `gr-create-change-help`: A help dialog for creating a new change.
- `gr-create-commands-dialog`: A dialog that displays the commands for creating a new change.
- `gr-create-destination-dialog`: A dialog for selecting the destination for a new change.
- `gr-dashboard-view`: The main view for a user's dashboard.
- `gr-repo-header`: The header for a repository page.
- `gr-user-header`: The header for a user's page.
- `gr-ai-prompt-dialog`: A dialog for generating AI prompts.
- `gr-change-actions`: The action bar for a single change.
- `gr-change-metadata`: The metadata section for a single change.
- `gr-change-summary`: A summary of a single change.
- `gr-checks-chip`: A chip for displaying the status of a check.
- `gr-summary-chip`: A chip for displaying a summary of information.
- `gr-change-view`: The main view for a single change.
- `gr-comments-summary`: A summary of the comments on a change.
- `gr-commit-info`: Displays information about a single commit.
- `gr-confirm-abandon-dialog`: A confirmation dialog for abandoning a change.
- `gr-confirm-cherrypick-conflict-dialog`: A confirmation dialog for a cherry-pick conflict.
- `gr-confirm-cherrypick-dialog`: A confirmation dialog for cherry-picking a change.
- `gr-confirm-move-dialog`: A confirmation dialog for moving a change.
- `gr-confirm-rebase-dialog`: A confirmation dialog for rebasing a change.
- `gr-confirm-revert-dialog`: A confirmation dialog for reverting a change.
- `gr-confirm-submit-dialog`: A confirmation dialog for submitting a change.
- `gr-copy-links`: A dialog for copying links related to a change.
- `gr-download-dialog`: A dialog for downloading a patch set.
- `gr-file-list-header`: The header for a file list.
- `gr-file-list`: A list of files in a patch set.
- `gr-included-in-dialog`: A dialog that displays where a change is included.
- `gr-label-score-row`: A row in the label scores table.
- `gr-label-scores`: A table of label scores.
- `gr-message-scores`: Displays the scores from a change message.
- `gr-message`: A single message in a change's history.
- `gr-messages-list`: A list of messages in a change's history.
- `gr-related-change`: A single related change.
- `gr-related-changes-list`: A list of related changes.
- `gr-related-collapse`: A collapsible section of related changes.
- `gr-reply-dialog`: The reply dialog for a change.
- `gr-reviewer-list`: A list of reviewers on a change.
- `gr-revision-parents`: Displays the parents of a revision.
- `gr-submit-requirement-dashboard-hovercard`: A hovercard for a submit requirement on the dashboard.
- `gr-submit-requirement-hovercard`: A hovercard for a submit requirement.
- `gr-submit-requirements`: A list of submit requirements.
- `gr-thread-list`: A list of comment threads.
- `gr-trigger-vote-hovercard`: A hovercard for a trigger vote.
- `gr-trigger-vote`: A trigger vote chip.
- `gr-validation-options`: Options for validation.
- `gr-checks-action`: An action for a check.
- `gr-checks-attempt`: An attempt for a check.
- `gr-checks-chip-for-label`: A chip for a check on a label.
- `gr-checks-fix-preview`: A preview of a fix for a check.
- `gr-checks-results`: The results of a check.
- `gr-checks-runs`: The runs of a check.
- `gr-checks-tab`: The checks tab.
- `gr-diff-check-result`: The result of a diff check.
- `gr-hovercard-run`: A hovercard for a check run.
- `gr-account-dropdown`: A dropdown for the user's account.
- `gr-error-dialog`: A dialog for displaying an error.
- `gr-error-manager`: Manages errors.
- `gr-key-binding-display`: Displays a key binding.
- `gr-keyboard-shortcuts-dialog`: A dialog for displaying keyboard shortcuts.
- `gr-main-header`: The main header of the application.
- `gr-navigation`: Manages navigation.
- `gr-notifications-prompt`: A prompt for notifications.
- `gr-router`: The main router for the application.
- `gr-search-bar`: The search bar.
- `gr-smart-search`: A search bar with smart suggestions.
- `gr-apply-fix-dialog`: A dialog for applying a fix.
- `gr-comment-api`: The API for comments.
- `gr-diff-host`: The host for a diff.
- `gr-diff-mode-selector`: A selector for the diff mode.
- `gr-diff-preferences-dialog`: A dialog for diff preferences.
- `gr-diff-view`: The main view for a diff.
- `gr-patch-range-select`: A selector for the patch range.
- `gr-documentation-search`: A search bar for documentation.
- `gr-default-editor`: The default editor.
- `gr-edit-controls`: Controls for editing.
- `gr-edit-file-controls`: Controls for editing a file.
- `gr-edit-preferences-dialog`: A dialog for edit preferences.
- `gr-editor-view`: The main view for an editor.
- `gr-app-element`: The main application element.
- `gr-app`: The main application component.
- `fit-controller`: A controller for fitting an element.
- `incremental-repeat`: A directive for incrementally repeating an element.
- `shortcut-controller`: A controller for shortcuts.
- `subscription-controller`: A controller for subscriptions.
- `gr-admin-api`: The API for admin functions.
- `gr-attribute-helper`: A helper for attributes.
- `gr-change-updates-api`: The API for change updates.
- `gr-checks-api`: The API for checks.
- `gr-dom-hooks`: Hooks for the DOM.
- `gr-endpoint-decorator`: A decorator for endpoints.
- `gr-endpoint-param`: A parameter for an endpoint.
- `gr-endpoint-slot`: A slot for an endpoint.
- `gr-event-helper`: A helper for events.
- `gr-plugin-host`: The host for a plugin.
- `gr-plugin-screen`: The screen for a plugin.
- `gr-plugin-popup`: A popup for a plugin.
- `gr-popup-interface`: The interface for a popup.
- `gr-suggestions-api`: The API for suggestions.
- `gr-account-info`: Displays information about an account.
- `gr-agreements-list`: A list of agreements.
- `gr-change-table-editor`: An editor for the change table.
- `gr-cla-view`: The view for a CLA.
- `gr-edit-preferences`: Preferences for editing.
- `gr-email-editor`: An editor for emails.
- `gr-gpg-editor`: An editor for GPG keys.
- `gr-group-list`: A list of groups.
- `gr-http-password`: A password for HTTP.
- `gr-identities`: A list of identities.
- `gr-menu-editor`: An editor for the menu.
- `gr-preferences`: Preferences.
- `gr-registration-dialog`: A dialog for registration.
- `gr-settings-view`: The main view for settings.
- `gr-ssh-editor`: An editor for SSH keys.
- `gr-watched-projects-editor`: An editor for watched projects.
- `gr-account-chip`: A chip for an account.
- `gr-account-entry`: An entry for an account.
- `gr-account-label`: A label for an account.
- `gr-account-list`: A list of accounts.
- `gr-alert`: An alert.
- `gr-autocomplete-dropdown`: A dropdown for an autocomplete.
- `gr-autocomplete`: An autocomplete.
- `gr-avatar`: An avatar.
- `gr-avatar-stack`: A stack of avatars.
- `gr-button`: A button.
- `gr-change-star`: A star for a change.
- `gr-change-status`: The status of a change.
- `gr-comment-model`: The model for a comment.
- `gr-comment-thread`: A thread of comments.
- `gr-comment`: A single comment.
- `gr-confirm-delete-comment-dialog`: A confirmation dialog for deleting a comment.
- `gr-copy-clipboard`: A button for copying to the clipboard.
- `gr-cursor-manager`: Manages the cursor.
- `gr-date-formatter`: Formats a date.
- `gr-dialog`: A dialog.
- `gr-diff-preferences`: Preferences for a diff.
- `gr-download-commands`: Commands for downloading.
- `gr-dropdown-list`: A list for a dropdown.
- `gr-dropdown`: A dropdown.
- `gr-editable-content`: Editable content.
- `gr-editable-label`: An editable label.
- `gr-file-status`: The status of a file.
- `gr-fix-suggestions`: Suggestions for a fix.
- `gr-formatted-text`: Formatted text.
- `gr-hovercard-account`: A hovercard for an account.
- `gr-hovercard`: A hovercard.
- `gr-icon`: An icon.
- `gr-js-api-interface`: The interface for the JS API.
- `gr-label-info`: Information about a label.
- `gr-labeled-autocomplete`: An autocomplete with a label.
- `gr-lib-loader`: Loads a library.
- `gr-limited-text`: Text with a limited length.
- `gr-linked-chip`: A chip with a link.
- `gr-list-view`: A view for a list.
- `gr-marked-element`: An element for markdown.
- `gr-page-nav`: Navigation for a page.
- `gr-repo-branch-picker`: A picker for a repo and branch.
- `gr-rest-api-interface`: The interface for the REST API.
- `gr-select`: A select.
- `gr-selector`: A selector.
- `gr-shell-command`: A shell command.
- `gr-suggestion-diff-preview`: A preview of a suggestion diff.
- `gr-suggestion-textarea`: A textarea with suggestions.
- `gr-tooltip-content`: Content for a tooltip.
- `gr-tooltip`: A tooltip.
- `gr-user-suggestion-fix`: A fix for a user suggestion.
- `gr-vote-chip`: A chip for a vote.
- `gr-weblink`: A weblink.
- `revision-info`: Information about a revision.
