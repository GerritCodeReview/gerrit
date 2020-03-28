NO_CACHE=true
IMAGE=gcr.io/api-project-164060093628/rbe-ubuntu18-04
# This Dockerfile upgrades Git version to version >2.6.
# This is needed for Git  wire protocol 2 tests to pass, because
# this test is using Git client, and support for new Git protocol
# was added in 2.18.0. Git version shipped in Ubuntu 16.04: 2.7.4
# and Git version shipped in Ubuntu 18.04: 2.17.1.
#
# To authenticate to GCloud registry:
# gcloud auth configure-docker
#
build:
	docker build --no-cache=$(NO_CACHE) \
        -t ${IMAGE} .
publish:
	docker push ${IMAGE}
clean:
	docker rmi -f ${IMAGE}
run:
	docker run -it ${IMAGE}
.PHONY: clean image publish

