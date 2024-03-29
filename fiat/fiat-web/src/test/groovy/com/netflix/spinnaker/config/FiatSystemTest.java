package com.netflix.spinnaker.config;

import com.netflix.spinnaker.fiat.Main;
import com.netflix.spinnaker.fiat.config.ResourcesConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

// This file must be .java because groovy barfs on the composite annotation style.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebAppConfiguration()
@TestPropertySource(properties = {"spring.config.location=classpath:fiat-test.yml"})
@DirtiesContext
@ContextConfiguration(
    classes = {
      TestUserRoleProviderConfig.class,
      ResourcesConfig.class,
      Main.class,
      TestDataLoaderConfig.class
    })
@ExtendWith(SpringExtension.class)
@SpringBootTest
public @interface FiatSystemTest {}
