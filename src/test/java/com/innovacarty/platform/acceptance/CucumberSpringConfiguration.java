package com.innovacarty.platform.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole API once for every scenario, with the in-memory H2 database and the demo data
 * (products of the prototype and the demo accounts). Each scenario uses its own cart and shopper.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
public class CucumberSpringConfiguration {
}
