package com.umdevcreative.smartkin.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static com.umdevcreative.smartkin.constant.SmartKinAppConstants.ENTITY_PACKAGE;
import static com.umdevcreative.smartkin.constant.SmartKinAppConstants.REPOSITORY_PACKAGE;


/**
 * This is the DataBaseConfig class.
 * It is annotated with @Configuration to indicate that it is a configuration class.
 * It uses @EntityScan to specify the base packages to scan for entity classes.
 * It uses @EnableJpaRepositories to specify the base packages to scan for JPA repositories.
 */
@Configuration
@EntityScan(basePackages = {ENTITY_PACKAGE})
@EnableJpaRepositories(basePackages = {REPOSITORY_PACKAGE})
public class DataBaseConfig {
}
