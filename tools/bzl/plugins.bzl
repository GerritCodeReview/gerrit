CORE_PLUGINS = [
    "codemirror-editor",
    "commit-message-length-validator",
    "delete-project",
    "download-commands",
    "gitiles",
    "hooks",
    "replication",
    "reviewnotes",
    "singleusergroup",
    "webhooks",
]

# TODO(dborowitz): Figure out how to include UI-only plugins in release.war.
CUSTOM_PLUGINS = [
    "automerger",
    "binary-size",
    "buildbucket",
    "checks",
    #"chromium-behavior",
    #"chromium-coverage",
    #"chromium-style",
    "chumpdetector",
    "find-owners",
    "git-numberer",
    "hide-actions",
    #"image-diff",
    "landingwidget",
    "reviewers",
    "simple-submit-rules",
    "supermanifest",
    "tricium",
    "uploadvalidator",
]

CUSTOM_PLUGINS_TEST_DEPS = [
    # Add custom core plugins with tests deps here
]
