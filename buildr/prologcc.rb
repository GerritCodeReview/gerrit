# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with this
# work for additional information regarding copyright ownership.  The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
# License for the specific language governing permissions and limitations under
# the License.

module Buildr
  # Provides PrologCC compile tasks. Require explicitly using <code>require "buildr/prologcc"</code>.
  module PrologCC

    REQUIRES = [ "com.googlecode.prolog-cafe:PrologCafe:jar:1.3" ]

    Java.classpath << REQUIRES

    class << self

      def prologcc(*args)
        options = Hash === args.last ? args.pop : {}
        rake_check_options options, :output
        
        args = args.flatten.map(&:to_s).collect { |f| File.directory?(f) ? FileList[f + "/**/*.pl"] : f }.flatten
        Java.load
        intermediate_dir = options[:output]
        mkdir_p intermediate_dir
        args.each do |prolog_file|
          puts "Converting prolog file: #{prolog_file} ..."
          Java.com.googlecode.prolog_cafe.compiler.Compiler.new.prologToJavaSource(prolog_file, intermediate_dir)
          # TODO: how appropriate check return value here?
          #== 0 or fail "Failed to run PrologCC, see errors above."
        end  
      end

    end

    def prologcc(*args)
      if Hash === args.last
        options = args.pop
        in_package = options[:in_package].split(".")
      else
        in_package = []
      end
      path = path_to(:target, :generated, :prologcc)
      file(path_to(:target, :generated, :prologcc)=>args.flatten) do |task|
        PrologCC.prologcc task.prerequisites, 
        :output=>File.join(task.name)
      end
    end

  end

  class Project
    include PrologCC
  end
end
