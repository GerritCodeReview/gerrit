---
trigger: always_on
description: Mandate uploading agent rationale for Gerrit changes
---

## Gerrit Rationale Upload Policy

Whenever you create a new Gerrit change or upload a patchset (via `git push`,
`hg upload`, `g4 upload`, or any other VCS command), check the `gerrit` skill
(specifically the `upload-rationale` section) to determine whether an AI
rationale should be uploaded, and follow its instructions.
