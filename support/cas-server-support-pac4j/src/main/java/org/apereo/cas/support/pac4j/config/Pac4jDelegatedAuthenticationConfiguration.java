package org.apereo.cas.support.pac4j.config;

import lombok.extern.slf4j.Slf4j;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.audit.AuditableExecution;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.core.util.EncryptionJwtSigningJwtCryptographyProperties;
import org.apereo.cas.configuration.model.support.pac4j.Pac4jDelegatedSessionCookieProperties;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.validation.Pac4jServiceTicketValidationAuthorizer;
import org.apereo.cas.validation.RegisteredServiceDelegatedAuthenticationPolicyAuditableEnforcer;
import org.apereo.cas.validation.ServiceTicketValidationAuthorizer;
import org.apereo.cas.validation.ServiceTicketValidationAuthorizerConfigurer;
import org.apereo.cas.validation.ServiceTicketValidationAuthorizersExecutionPlan;
import org.apereo.cas.web.pac4j.DelegatedSessionCookieCipherExecutor;
import org.apereo.cas.web.pac4j.DelegatedSessionCookieManager;
import org.apereo.cas.web.pac4j.SessionStoreCookieGenerator;
import org.apereo.cas.web.support.CookieRetrievingCookieGenerator;
import org.apereo.cas.web.support.DefaultCasCookieValueManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This is {@link Pac4jDelegatedAuthenticationConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("pac4jDelegatedAuthenticationConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
public class Pac4jDelegatedAuthenticationConfiguration implements ServiceTicketValidationAuthorizerConfigurer {

    @Autowired
    @Qualifier("servicesManager")
    private ServicesManager servicesManager;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Bean
    @RefreshScope
    @ConditionalOnMissingBean(name = "registeredServiceDelegatedAuthenticationPolicyAuditableEnforcer")
    public AuditableExecution registeredServiceDelegatedAuthenticationPolicyAuditableEnforcer() {
        return new RegisteredServiceDelegatedAuthenticationPolicyAuditableEnforcer();
    }

    @Bean
    @ConditionalOnMissingBean(name = "pac4jDelegatedSessionCookieManager")
    public DelegatedSessionCookieManager pac4jDelegatedSessionCookieManager() {
        return new DelegatedSessionCookieManager(pac4jSessionStoreCookieGenerator());
    }

    @Bean
    @ConditionalOnMissingBean(name = "pac4jSessionStoreCookieGenerator")
    public CookieRetrievingCookieGenerator pac4jSessionStoreCookieGenerator() {
        final Pac4jDelegatedSessionCookieProperties c = casProperties.getAuthn().getPac4j().getCookie();
        return new SessionStoreCookieGenerator(
            new DefaultCasCookieValueManager(pac4jDelegatedSessionStoreCookieCipherExecutor()),
            c.getName(), c.getPath(), c.getMaxAge(),
            c.isSecure(), c.getDomain(), c.isHttpOnly());
    }

    @Bean
    @ConditionalOnMissingBean(name = "pac4jDelegatedSessionStoreCookieCipherExecutor")
    public CipherExecutor pac4jDelegatedSessionStoreCookieCipherExecutor() {
        final EncryptionJwtSigningJwtCryptographyProperties c = casProperties.getAuthn().getPac4j().getCookie().getCrypto();
        if (c.isEnabled()) {
            return new DelegatedSessionCookieCipherExecutor(c.getEncryption().getKey(), c.getSigning().getKey(), c.getAlg());
        }
        LOGGER.info("Delegated authentication cookie encryption/signing is turned off and "
            + "MAY NOT be safe in a production environment. "
            + "Consider using other choices to handle encryption, signing and verification of "
            + "delegated authentication cookie.");
        return CipherExecutor.noOp();
    }

    @Bean
    public ServiceTicketValidationAuthorizer pac4jServiceTicketValidationAuthorizer() {
        return new Pac4jServiceTicketValidationAuthorizer(this.servicesManager,
            registeredServiceDelegatedAuthenticationPolicyAuditableEnforcer());
    }

    @Override
    public void configureAuthorizersExecutionPlan(final ServiceTicketValidationAuthorizersExecutionPlan plan) {
        plan.registerAuthorizer(pac4jServiceTicketValidationAuthorizer());
    }
}
