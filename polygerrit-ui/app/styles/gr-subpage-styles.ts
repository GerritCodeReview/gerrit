/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const subpageStyles = css`
  .main {
    margin: var(--spacing-l);
  }
  .loading {
    display: none;
  }
  #loading.loading {
    display: block;
  }
  #loading:not(.loading) {
    display: none;
  }
`;
