# Copyright (C) 2019 The Android Open Source Project
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

# Definitions to run jmh microbenchmarks

def jmh_java_benchmarks(name, srcs, deps = [], tags = [], plugins = [], **kwargs):
    """Builds runnable JMH benchmarks.
    This rule builds a runnable target for one or more JMH benchmarks
    specified as srcs. It takes the same arguments as java_binary,
    except for main_class.
    """
    plugin_name = "_{}_jmh_annotation_processor".format(name)
    native.java_plugin(
        name = plugin_name,
        deps = ["//lib/jmh:jmh"],
        processor_class = "org.openjdk.jmh.generators.BenchmarkProcessor",
        visibility = ["//visibility:private"],
        tags = tags,
    )
    native.java_binary(
        name = name,
        srcs = srcs,
        main_class = "org.openjdk.jmh.Main",
        deps = deps + ["//lib/jmh:jmh"],
        plugins = plugins + [plugin_name],
        tags = tags,
        **kwargs
    )
