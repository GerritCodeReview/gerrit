# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

A2X ?= a2x

all: man

clean:
	@rm -f gerrit-*

CMD_CORE_SSH_CMD := \
cmd-ban-commit.txt     \
cmd-create-account.txt \
cmd-create-group.txt   \
cmd-create-project.txt \
cmd-flush-caches.txt   \
cmd-gc.txt             \
cmd-gsql.txt           \
cmd-ls-groups.txt      \
cmd-ls-projects.txt    \
cmd-ls-user-refs.txt   \
cmd-query.txt          \
cmd-rename-group.txt   \
cmd-review.txt         \
cmd-set-account.txt    \
cmd-set-project-parent.txt \
cmd-set-project.txt    \
cmd-set-reviewers.txt  \
cmd-show-caches.txt    \
cmd-show-connections.txt   \
cmd-show-queue.txt     \
cmd-stream-events.txt  \
cmd-test-submit-rule.txt   \
cmd-version.txt

GERRIT_CORE_SSH_CMD := $(patsubst cmd-%,gerrit-%,$(CMD_CORE_SSH_CMD))
DOC_MAN := $(patsubst %.txt,%.1,$(GERRIT_CORE_SSH_CMD))

man: $(GERRIT_CORE_SSH_CMD) $(DOC_MAN)

$(GERRIT_CORE_SSH_CMD) : gerrit-%.txt : ../cmd-%.txt
	@cp $< $@

$(DOC_MAN) : %.1 : %.txt
	@echo "creating man page for $@ ..."
	@rm -f $@
	@$(eval TITLE := $(join $(basename $<),\(1\)))
	@$(eval SEPERATOR := $(shell echo $(TITLE) | sed 's/./=/g'))
	@sed -i -re '1s/^.*$//$(TITLE)/' $<
	@sed -i -re '2s/^=.*/$(SEPERATOR)/' $<
	@sed -i -re '6s/^gerrit\s+(\w)/gerrit-\1/' $<
	@$(A2X) --doctype manpage --format manpage $<
