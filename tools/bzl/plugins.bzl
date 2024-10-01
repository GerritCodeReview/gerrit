CORE_PLUGINS = [
    "codemirror-editor",
    "commit-message-length-validator",
    "delete-project",
    "download-commands",
    "gitiles",
    "hooks",
    "plugin-manager",
    "replication",
    "replication:replication-api",
    "reviewnotes",
    "singleusergroup",
    "webhooks",
]

CUSTOM_PLUGINS = [
    # Add custom core plugins here
    "cached-refdb",
    #"virtualhost",
    #"pull-replication",
    #"owners",
    #"ghs-upload-pack-metrics",
    #"github",
    "git-repo-metrics",
]

CUSTOM_PLUGINS_TEST_DEPS = [
    #"owners",
    # Add custom core plugins with tests deps here
    #"owners-common",
]
