#!/bin/bash

gob-ctl -policy admin -b 179338407 <<EOF
update_host {
  host: "gerrit"
  set_gerrit_config_canary {
    name: "cache.diff_cache.runNewDiffCache_ListFiles"
    value: "true"
  }
}
EOF

gob-ctl -policy admin -b 179338407 <<EOF
update_host {
  host: "gerrit"
  set_gerrit_config {
    name: "cache.diff_cache.runNewDiffCache_ListFiles"
    value: "true"
  }
}
EOF
