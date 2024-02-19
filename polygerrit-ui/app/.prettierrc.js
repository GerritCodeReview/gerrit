/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/* Adding a comment to test */
module.exports = {
  "overrides": [
    {
      "files": ["**/*.ts"],
      "options": {
          ...require('gts/.prettierrc.json')
      }
    }
  ]
};
