Script Realm plugin for Jenkins
===============================

This Jenkins plugin allows you to use a user-written custom script to authenticate the username and password (also supports groups). 
This is useful if you need to plug into a custom authentication scheme, but don't want to write your own plugin.

Find more at https://wiki.jenkins-ci.org/display/JENKINS/Script+Security+Realm.


Resolving e-mail and full name
------------------------------

In order to display e-mail and full name in the authenticated users' page, this plugin triggers any `MailAddressResolver` and `UserNameResolver` installed on the Jenkins instance.
You may disable resolution or use a specific resolver for each of them.

For instance, the [LDAP Email Plugin](https://wiki.jenkins-ci.org/display/JENKINS/LDAP+Email+Plugin) provides a `MailAddressResolver`.

Since this plugin does not know how resolvers from external plugins are to be configured, using a random resolver might not work at all.