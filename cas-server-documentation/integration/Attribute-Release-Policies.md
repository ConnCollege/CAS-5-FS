---
layout: default
title: CAS - Attribute Release Policies
---

# Attribute Release Policies

The release policy decides how attributes are to be released for a given service. Each policy has
the ability to apply an optional filter.

The following settings are shared by all attribute release policies:

| Name                    | Value
|---------------------------------------+---------------------------------------------------------------+
| `authorizedToReleaseCredentialPassword` | Boolean to define whether the service is authorized to [release the credential as an attribute](ClearPass.html).
| `authorizedToReleaseProxyGrantingTicket` | Boolean to define whether the service is authorized to [release the proxy-granting ticket id as an attribute](../installation/Configuring-Proxy-Authentication.html)

<div class="alert alert-warning"><strong>Usage Warning!</strong><p>Think VERY CAREFULLY before turning on the above settings. Blindly authorizing an application to receive a proxy-granting ticket or the user credential
may produce an oppurunity for security leaks and attacks. Make sure you actually need to enable those features and that you understand the why. Avoid where and when you can, specially when it comes to sharing the user credential.</p></div>

## Return All

Return all resolved attributes to the service.

```json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "sample",
  "name" : "sample",
  "id" : 100,
  "description" : "sample",
  "attributeReleasePolicy" : {
    "@class" : "org.apereo.cas.services.ReturnAllAttributeReleasePolicy"
  }
}
```

## Return Allowed

Only return the attributes that are explicitly allowed by the configuration.

```json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "sample",
  "name" : "sample",
  "id" : 100,
  "description" : "sample",
  "attributeReleasePolicy" : {
    "@class" : "org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy",
    "allowedAttributes" : [ "java.util.ArrayList", [ "cn", "mail", "sn" ] ]
  }
}
```


## Return Mapped

Similar to above, this policy will return a collection of allowed attributes for the
service, but also allows those attributes to be mapped and "renamed" at the more granular service level.

For example, the following configuration will recognize the resolved
attributes `eduPersonAffiliation` and `groupMembership` and will then
release `affiliation` and `group` to the web application configured.

```json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "sample",
  "name" : "sample",
  "id" : 300,
  "description" : "sample",
  "attributeReleasePolicy" : {
    "@class" : "org.apereo.cas.services.ReturnMappedAttributeReleasePolicy",
    "allowedAttributes" : {
      "@class" : "java.util.TreeMap",
      "eduPersonAffiliation" : "affiliation",
      "groupMembership" : "group"
    }
  }
}
```

## Groovy Script

Let an external Groovy script decide how attributes should be released.

```json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "sample",
  "name" : "sample",
  "id" : 300,
  "description" : "sample",
  "attributeReleasePolicy" : {
    "@class" : "org.apereo.cas.services.GroovyScriptAttributeReleasePolicy",
    "groovyScript" : "classpath:/script.groovy"
  }
}
```

The script itself may be designed as:

```groovy
import java.util.*

class SampleGroovyPersonAttributeDao {
    def Map<String, List<Object>> run(final Object... args) {
        def currentAttributes = args[0]
        def logger = args[1]
        
        logger.debug("Current attributes received are {}", currentAttributes)
        return[name:["something"], likes:["cheese", "food"], id:[1234,2,3,4,5], another:"attribute"]
    }
}
```

## Attribute Filters

While each policy defines what attributes may be allowed for a given service,
there are optional attribute filters that can be set per policy to further weed out attributes based on their **values**.

### Regex

The regex filter that is responsible to make sure only attributes whose value
matches a certain regex pattern are released.

Suppose that the following attributes are resolved:

| Name       							| Value
|---------------------------------------+---------------------------------------------------------------+
| `uid`        							| jsmith
| `groupMembership`        	| std  
| `cn`        							| JohnSmith   

The following configuration for instance considers the initial list of `uid`,
`groupMembership` and then only allows and releases attributes whose value's length
is 3 characters. Therefor, out of the above list, only `groupMembership` is released to the application.

```json
{
  "@class" : "org.apereo.cas.services.RegexRegisteredService",
  "serviceId" : "sample",
  "name" : "sample",
  "id" : 200,
  "description" : "sample",
  "attributeReleasePolicy" : {
    "@class" : "org.apereo.cas.services.ReturnAllowedAttributeReleasePolicy",
    "attributeFilter" : {
      "@class" : "org.apereo.cas.services.support.RegisteredServiceRegexAttributeFilter",
      "pattern" : "^\w{3}$"
    },
    "allowedAttributes" : [ "java.util.ArrayList", [ "uid", "groupMembership" ] ]
  }
}
```