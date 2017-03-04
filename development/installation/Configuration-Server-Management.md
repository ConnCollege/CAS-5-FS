---
layout: default
title: CAS - Configuration Server
---

# Configuration Server

As your CAS deployment moves through the deployment pipeline from dev to test and into production
you can manage the configuration between those environments and be certain that applications
have everything they need to run when they migrate through the use of an external configuration server
provided by the [Spring Cloud](https://github.com/spring-cloud/spring-cloud-config) project. As an alternative,
you may decide to simply run CAS in a standalone mode removing the need for external configuration server deployment,
though at the cost of losing features and capabilities relevant for a cloud deployment.

The configuration behavior of CAS is controlled and defined by the `src/main/resources/bootstrap.properties` file.

## Configuration Profiles

The CAS server web application responds to the following strategies that dictate how settings should be consumed.

### Standalone

This is the default configuration mode which indicates that CAS does **NOT** require connections to an external configuration server
and will run in an embedded *standalone mode*. When this option is turned on, CAS by default will attempt to locate settings and properties
indicated under the setting name `cas.standalone.config` and otherwise falls back to using `/etc/cas/config` as the configuration directory.
You may instruct CAS to use this setting via the methods [outlined here](Configuration-Management.html#overview).

Similar to the Spring Cloud external configuration server, the contents of this directory include `(cas|application).(yml|properties)`
files that can used to control CAS behavior. Also note that this configuration directory can be monitored by CAS to auto-pick up changes
and refresh the application context as needed. Please [review this guide](Configuration-Management-Reload.html#reload-strategy) to learn more.

Note that by default, all CAS settings and configuration is controlled via the embedded `application.properties` file in the CAS server
web application. Settings found in external configuration files are and will be able to override the defaults provide by CAS.

<div class="alert alert-info"><strong>Keep What You Need!</strong><p>You are advised to not overlay or otherwise
modify the built in <code>application.properties</code> file. This will only complicate and weaken your deployment.
Instead try to comply with the CAS defaults, or otherwise instruct CAS to locate configuration files external to its own.</p></div>

### Spring Cloud

CAS also provides the ability to communicate to an external configuration server to obtain state and settings.
The configuration server provides a very abstract way for CAS (and all of its other clients) to obtain settings from a variety
of sources, such as file system, `git` or `svn` repositories, MongoDb, Vault, etc. The beauty about this solution is that to the CAS
web application server, it matters not where settings come from and it has no knowledge of the underlying property sources. It simply
talks to the configuration server to locate settings and move on.

A full comprehensive guide is provided by the [Spring Cloud project](https://cloud.spring.io/spring-cloud-config/spring-cloud-config.html).

#### Overlay

The configuration server itself, similar to CAS, is a Spring-Boot application and can be deployed
via the following module in it own [WAR overlay](https://github.com/apereo/cas-configserver-overlay):

```xml
<dependency>
  <groupId>org.apereo.cas</groupId>
  <artifactId>cas-server-webapp-config-server</artifactId>
  <version>${cas.version}</version>
</dependency>
```

The configuration and behavior of the configuration server is also controlled by its own
`src/main/resources/bootstrap.properties` file. By default, it runs under port `8888` at `/casconfigserver` inside
an embedded Apache Tomcat server whose endpoints are protected with basic authentication
where the default credentials are `casuser` and `Mellon`. Furthermore, by default it runs
under a `native` profile described below.

The following endpoints are secured and exposed by the configuration server:

| Parameter                         | Description
|-----------------------------------|------------------------------------------
| `/encrypt`                        | Accepts a `POST` to encrypt CAS configuration settings.
| `/decrypt`                        | Accepts a `POST` to decrypt CAS configuration settings.
| `/cas/default`                    | Describes what the configuration server knows about the `default` settings profile.
| `/cas/native`                     | Describes what the configuration server knows about the `native` settings profile.
| `/bus/refresh`                    | Reload the configuration of all CAS nodes in the cluster if the cloud bus is turned on.
| `/bus/env`                        | Sends key/values pairs to update each CAS node if the cloud bus is turned on.

Once you have the configuration server deployed, you can observe the collection of settings via:

```bash
curl -u casuser:Mellon http://localhost:8888/casconfigserver/cas/native
```

### CAS Server

To let the CAS server web application talk to the configuration server, the following settings need to be applied
to CAS' own `src/main/resources/bootstrap.properties` file:

```properties
spring.cloud.config.uri=http://casuser:Mellon@localhost:8888/casconfigserver
spring.cloud.config.profile=native
spring.cloud.config.enabled=true
```

### Profiles

Various profiles exist to determine how configuration server should retrieve properties and settings.

#### Native

The server is configured by default to load `cas.(properties|yml)` files from an external location that is `/etc/cas/config`.
This location is constantly monitored by the server to detect external changes. Note that this location simply needs to
exist, and does not require any special permissions or structure. The name of the configuration file that goes inside this
directory needs to match the `spring.application.name` (i.e. `cas.properties`).

If you want to use additional configuration files, they need to have the
form `application-<profile>.(properties|yml)`.
A file named `application.(properties|yml)` will be included by default. The profile specific
files can be activated by using the `spring.profiles.include` configuration option,
controlled via the `src/main/resources/bootstrap.properties` file:

```properties
spring.profiles.active=native
spring.cloud.config.server.native.searchLocations=file:///etc/cas/config
spring.profiles.include=profile1,profile2
```

An example of an external `.properties` file hosted by an external location follows:

```properties
cas.server.name=...
```

You could have just as well used a `cas.yml` file to host the changes.

#### Default

The configuration server is also able to handle `git` or `svn` based repositories that host CAS configuration.
Such repositories can either be local to the deployment, or they could be on the cloud in form of GitHub/BitBucket. Access to
cloud-based repositories can either be in form of a username/password, or via SSH so as long the appropriate keys are configured in the
CAS deployment environment which is really no different than how one would normally access a git repository via SSH.

```properties
# spring.profiles.active=default
# spring.cloud.config.server.git.uri=https://github.com/repoName/config
# spring.cloud.config.server.git.uri=file://${user.home}/config
# spring.cloud.config.server.git.username=
# spring.cloud.config.server.git.password=

# spring.cloud.config.server.svn.basedir=
# spring.cloud.config.server.svn.uri=
# spring.cloud.config.server.svn.username=
# spring.cloud.config.server.svn.password=
# spring.cloud.config.server.svn.default-label=trunk
```

Needless to say, the repositories could use both YAML and properties syntax to host configuration files.

<div class="alert alert-info"><strong>Keep What You Need!</strong><p>Again, in all of the above strategies,
an adopter is encouraged to only keep and maintain properties needed for their particular deployment. It is
UNNECESSARY to grab a copy of all CAS settings and move them to an external location. Settings that are
defined by the external configuration location or repository are able to override what is provided by CAS
as a default.</p></div>

#### MongoDb

The server is also able to locate properties entirely from a MongoDb instance.

Support is provided via the following dependency in the WAR overlay:

```xml
<dependency>
     <groupId>org.apereo.cas</groupId>
     <artifactId>cas-server-core-configuration-mongo</artifactId>
     <version>${cas.version}</version>
</dependency>
```

Note that to access and review the collection of CAS properties,
you will need to use [the CAS administrative interfaces](Monitoring-Statistics.html), or you may
also use your own native tooling for MongoDB to configure and inject settings.

MongoDb documents are required to be found in the collection `MongoDbProperty`, as the following document:

```json
{
    "id": "kfhf945jegnsd45sdg93452",
    "name": "the-setting-name",
    "value": "the-setting-value"
}
```

To see the relevant list of CAS properties for this feature, please [review this guide](Configuration-Properties.html#mongodb).

#### HashiCorp Vault

CAS is also able to use [Vault](https://www.vaultproject.io/) to
locate properties and settings. [Please review this guide](Configuration-Properties-Security.html).

## Property Overrides

The configuration server has an "overrides" feature that allows the operator to provide configuration properties to all applications that cannot be accidentally changed by the application using the normal change events and hooks. To declare overrides add a map of name-value pairs to `spring.cloud.config.server.overrides`. For example:

```yml
spring:
  cloud:
    config:
      server:
        overrides:
          foo: bar
```

This will cause the CAS server (as the client of the configuration server) to read foo=bar independent of its own configuration.

## Securing Settings

To learn how sensitive CAS settings can be secured via encryption, [please review this guide](Configuration-Properties-Security.html).

## Reloading Changes

To lean more about CAS allows you to reload configuration changes,
please [review this guide](Configuration-Management-Reload.html).

## Clustered Deployments

CAS uses the [Spring Cloud Bus](http://cloud.spring.io/spring-cloud-static/spring-cloud.html)
to manage configuration in a distributed deployment. Spring Cloud Bus links nodes of a
distributed system with a lightweight message broker. This can then be used to broadcast state
changes (e.g. configuration changes) or other management instructions.

To learn how sensitive CAS settings can be secured via encryption, [please review this guide](Configuration-Management-Clustered.html).