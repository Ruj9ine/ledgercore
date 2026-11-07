package com.ledgercore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgercore.AbstractIntegrationTest;
import com.ledgercore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Discovers every endpoint from Spring's routing table and checks its protection, so an endpoint added later
 * cannot be forgotten. Requests carry an empty JSON body: the security filters decide before validation runs.
 */
class EndpointSecuritySweepIntegrationTest extends AbstractIntegrationTest {

    private static final String PLACEHOLDER = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_PREFIX = "/api/v1/admin/";
    private static final String PUBLIC_PREFIX = "/api/v1/auth/";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

    private ApiClient api;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(mvc, objectMapper);
    }

    private record Route(String method, String path) {

        String concretePath() {
            return path.replaceAll("\\{[^}]+\\}", PLACEHOLDER);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (pattern.startsWith("/error")) {
                    continue;   // Spring Boot's own error page, not part of our API
                }
                assertThat(methods).as("route %s must declare its HTTP methods", pattern).isNotEmpty();
                for (RequestMethod method : methods) {
                    routes.add(new Route(method.name(), pattern));
                }
            }
        }
        routes.sort(Comparator.comparing(Route::path).thenComparing(Route::method));
        return routes;
    }

    private int statusOf(Route route, String token) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(route.method()), route.concretePath())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void theSweepSeesTheWholeApi() {
        List<Route> routes = routes();

        assertThat(routes).as("routes found: %s", routes).hasSizeGreaterThanOrEqualTo(20);
        assertThat(routes.stream().filter(route -> route.path().startsWith(ADMIN_PREFIX)))
                .as("admin routes found").hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    void everyEndpointExceptLoginAndRegistrationRejectsRequestsWithoutAToken() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Route route : routes()) {
            if (route.path().startsWith(PUBLIC_PREFIX)) {
                continue;
            }
            int status = statusOf(route, null);
            if (status != 401) {
                failures.add(route + " answered " + status + " without a token");
            }
        }

        assertThat(failures).as("endpoints that did not answer 401 without a token").isEmpty();
    }

    @Test
    void everyAdminEndpointRejectsOrdinaryUsers() throws Exception {
        String alice = api.tokenFor("alice@example.com");

        List<String> failures = new ArrayList<>();
        for (Route route : routes()) {
            if (!route.path().startsWith(ADMIN_PREFIX)) {
                continue;
            }
            int status = statusOf(route, alice);
            if (status != 403) {
                failures.add(route + " answered " + status + " to an ordinary user");
            }
        }

        assertThat(failures).as("admin endpoints that did not answer 403 to an ordinary user").isEmpty();
    }

    @Test
    void anAdminTokenGetsPastTheGateOnEveryAdminEndpoint() throws Exception {
        String admin = api.tokenFor("admin@example.com");
        promoteToAdmin("admin@example.com");

        // This keeps the previous test honest: a 403 there must come from the role check, not from a broken route.
        List<String> failures = new ArrayList<>();
        for (Route route : routes()) {
            if (!route.path().startsWith(ADMIN_PREFIX)) {
                continue;
            }
            int status = statusOf(route, admin);
            if (status == 401 || status == 403) {
                failures.add(route + " answered " + status + " to an admin");
            }
        }

        assertThat(failures).as("admin endpoints that refused an admin").isEmpty();
    }

    @Test
    void onlyLoginAndRegistrationAreOpenToAnonymousUsers() {
        Set<String> open = routes().stream()
                .filter(route -> route.path().startsWith(PUBLIC_PREFIX))
                .map(Route::toString)
                .collect(Collectors.toSet());

        // Adding another route under /api/v1/auth/ makes it public, so doing so must be a conscious edit to this list.
        assertThat(open).containsExactlyInAnyOrder("POST /api/v1/auth/login", "POST /api/v1/auth/register");
    }
}