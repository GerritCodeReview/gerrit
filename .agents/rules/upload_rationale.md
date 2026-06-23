---
trigger: always_on
description: Mandate uploading agent rationale for Gerrit changes
---

## Gerrit Rationale Upload Policy

Whenever you create a new Gerrit change or upload a patchset (via `git push`,
`hg upload`, `g4 upload`, or any other VCS command), you **MUST** immediately
upload AI rationales for that patchset.

Follow this two-step workflow:

1.  **Push Code**: Execute your VCS push/upload command so the new patchset is
    created on Gerrit.
2.  **Upload Rationale**: Read the `gerrit` skill (specifically the
    `upload-rationale` section) and follow its instructions to construct and
    upload the complete set of rationales for the patchset.
