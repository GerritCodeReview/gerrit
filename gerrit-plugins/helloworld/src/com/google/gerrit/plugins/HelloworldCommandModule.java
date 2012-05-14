package com.google.gerrit.plugins;

public class HelloworldCommandModule extends PluginCommandModule {
  public void configure() {
    command("helloworld").to(HelloworldCommand.class);
  }
}
