Modular Script Realm plugin for Jenkins
=======================================

This fork of the "Script Realm" plugin for Jenkins adds e-mail and display name resolution support.

The original plugin allows to use a user-written custom script to authenticate the username and password (also supports groups).
This is useful if you need to plug into a custom authentication scheme, but don't want to write your own plugin.
Find more about the original plugin at https://wiki.jenkins-ci.org/display/JENKINS/Script+Security+Realm.

By default this fork triggers any `MailAddressResolver` and `UserNameResolver` installed on the Jenkins instance.
You can disable resolution for e-mail and/or display name or use a specific resolver.

For instance, the [LDAP Email Plugin](https://wiki.jenkins-ci.org/display/JENKINS/LDAP+Email+Plugin) provides a `MailAddressResolver`. If installed, you may configure "Modular Script Realm" to use a custom script to authenticate users and the "LDAP Email Plugin" to resolve their e-mail.

Since this plugin does not know how resolvers from external plugins are to be configured, using a random resolver might not work at all.

You will soon find here a list of known working configurations.
