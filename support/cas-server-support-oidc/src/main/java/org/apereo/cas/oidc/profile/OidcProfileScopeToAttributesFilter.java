package org.apereo.cas.oidc.profile;

import org.apache.shiro.util.ClassUtils;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.oidc.OidcConstants;
import org.apereo.cas.oidc.claims.BaseOidcScopeAttributeReleasePolicy;
import org.apereo.cas.oidc.claims.OidcAddressScopeAttributeReleasePolicy;
import org.apereo.cas.oidc.claims.OidcCustomScopeAttributeReleasePolicy;
import org.apereo.cas.oidc.claims.OidcEmailScopeAttributeReleasePolicy;
import org.apereo.cas.oidc.claims.OidcPhoneScopeAttributeReleasePolicy;
import org.apereo.cas.oidc.claims.OidcProfileScopeAttributeReleasePolicy;
import org.apereo.cas.services.ChainingAttributeReleasePolicy;
import org.apereo.cas.services.DenyAllAttributeReleasePolicy;
import org.apereo.cas.services.OidcRegisteredService;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.oauth.profile.DefaultOAuth20ProfileScopeToAttributesFilter;
import org.apereo.cas.support.oauth.util.OAuthUtils;
import org.pac4j.core.context.J2EContext;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is {@link OidcProfileScopeToAttributesFilter}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public class OidcProfileScopeToAttributesFilter extends DefaultOAuth20ProfileScopeToAttributesFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcProfileScopeToAttributesFilter.class);

    private Map<String, BaseOidcScopeAttributeReleasePolicy> filters;
    private final PrincipalFactory principalFactory;
    private final ServicesManager servicesManager;

    public OidcProfileScopeToAttributesFilter(final PrincipalFactory principalFactory,
                                              final ServicesManager servicesManager) {
        filters = new HashMap<>();

        final String packageName = BaseOidcScopeAttributeReleasePolicy.class.getPackage().getName();
        final Reflections reflections =
                new Reflections(new ConfigurationBuilder()
                        .filterInputsBy(new FilterBuilder().includePackage(packageName))
                        .setUrls(ClasspathHelper.forPackage(packageName))
                        .setScanners(new SubTypesScanner(true)));

        final Set<Class<? extends BaseOidcScopeAttributeReleasePolicy>> subTypes =
                reflections.getSubTypesOf(BaseOidcScopeAttributeReleasePolicy.class);
        subTypes.forEach(t -> {
            final BaseOidcScopeAttributeReleasePolicy ex = (BaseOidcScopeAttributeReleasePolicy) ClassUtils.newInstance(t);
            filters.put(ex.getScopeName(), ex);
        });
        this.principalFactory = principalFactory;
        this.servicesManager = servicesManager;
    }

    @Override
    public Principal filter(final Service service, final Principal profile,
                            final RegisteredService registeredService, final J2EContext context) {
        final Principal principal = super.filter(service, profile, registeredService, context);

        final OidcRegisteredService oidcService = (OidcRegisteredService) registeredService;
        final Collection<String> scopes = new ArrayList<>(OAuthUtils.getRequestedScopes(context));
        scopes.addAll(oidcService.getScopes());

        if (!scopes.contains(OidcConstants.OPENID)) {
            LOGGER.debug("Request does not indicate a scope [{}] that can identify OpenID Connect", scopes);
            return principal;
        }

        final Map<String, Object> attributes = new HashMap<>();

        scopes.stream()
                .distinct()
                .filter(s -> this.filters.containsKey(s))
                .forEach(s -> {
                    final BaseOidcScopeAttributeReleasePolicy policy = filters.get(s);
                    attributes.putAll(policy.getAttributes(principal, registeredService));
                });

        return this.principalFactory.createPrincipal(profile.getId(), attributes);
    }

    @Override
    public void reconcile(final RegisteredService service) {
        if (!(service instanceof OidcRegisteredService)) {
            super.reconcile(service);
            return;
        }

        final List<String> otherScopes = new ArrayList<>();
        final ChainingAttributeReleasePolicy policy = new ChainingAttributeReleasePolicy();
        final OidcRegisteredService oidc = OidcRegisteredService.class.cast(service);

        oidc.getScopes().forEach(s -> {
            switch (s.trim().toLowerCase()) {
                case OidcConstants.EMAIL:
                    policy.getPolicies().add(new OidcEmailScopeAttributeReleasePolicy());
                    break;
                case OidcConstants.ADDRESS:
                    policy.getPolicies().add(new OidcAddressScopeAttributeReleasePolicy());
                    break;
                case OidcConstants.PROFILE:
                    policy.getPolicies().add(new OidcProfileScopeAttributeReleasePolicy());
                    break;
                case OidcConstants.PHONE:
                    policy.getPolicies().add(new OidcPhoneScopeAttributeReleasePolicy());
                    break;
                case OidcConstants.OFFLINE_ACCESS:
                    oidc.setGenerateRefreshToken(true);
                    break;
                default:
                    otherScopes.add(s.trim());
            }
        });
        otherScopes.remove(OidcConstants.OPENID);
        if (!otherScopes.isEmpty()) {
            policy.getPolicies().add(new OidcCustomScopeAttributeReleasePolicy(otherScopes));
        }

        if (policy.getPolicies().isEmpty()) {
            oidc.setAttributeReleasePolicy(new DenyAllAttributeReleasePolicy());
        } else {
            oidc.setAttributeReleasePolicy(policy);
        }
        this.servicesManager.save(oidc);
    }
}