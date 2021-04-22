#!/bin/bash

gob-ctl -policy admin -b 179338407 <<EOF
update_host {
  host: "gerrit"
  remove_gerrit_config_canary: "cache.diff_cache.runNewDiffCacheAsync_listFiles"
}
EOF

gob-ctl -policy admin -b 179338407 <<EOF
update_host {
  host: "gerrit"
  remove_gerrit_config: "cache.diff_cache.runNewDiffCacheAsync_listFiles"
}
EOF
