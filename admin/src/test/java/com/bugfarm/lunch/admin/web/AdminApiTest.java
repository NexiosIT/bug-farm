package com.bugfarm.lunch.admin.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bugfarm.lunch.admin.AdminTestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminTestConfig.class)
class AdminApiTest {

    @DynamicPropertySource
    static void adminAccount(DynamicPropertyRegistry registry) {
        registry.add("lunch.admin.username", () -> "manager");
        registry.add("lunch.admin.password-hash", () -> new BCryptPasswordEncoder().encode("secret"));
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private static RequestPostProcessor admin() {
        return user("manager");
    }

    /**
     * Sends a CSRF token the way the SPA does: the XSRF-TOKEN cookie plus the same value
     * in the X-XSRF-TOKEN header. (spring-security-test's csrf() is avoided on purpose: it
     * swaps the shared filter's token repository for a session-based one, which hides
     * whether the real cookie repository is in use.)
     */
    private static RequestPostProcessor xsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "test-token"));
            request.addHeader("X-XSRF-TOKEN", "test-token");
            return request;
        };
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"));
        mvc.perform(get("/api/admin/orders/today"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordGets401() throws Exception {
        mvc.perform(post("/api/admin/login").with(xsrf())
                        .param("username", "manager").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenMeReturnsUsername() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/admin/login").with(xsrf())
                        .param("username", "manager").param("password", "secret"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/admin/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manager"));

        mvc.perform(post("/api/admin/logout").session(session).with(xsrf()))
                .andExpect(status().isOk());
    }

    @Test
    void todayOrdersShowsOpenAndCutOff() throws Exception {
        // AdminTestConfig's clock: Wednesday 2026-11-18, 09:00 in Brussels.
        mvc.perform(get("/api/admin/orders/today").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-18"))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.cutOff").value("10:00"))
                .andExpect(jsonPath("$.orders", hasSize(1)))
                .andExpect(jsonPath("$.orders[0].id").value(AdminTestConfig.JAN_ORDER_ID.toString()))
                .andExpect(jsonPath("$.orders[0].displayName").value("Jan Peeters"))
                .andExpect(jsonPath("$.orders[0].foodType").value("Sandwich"))
                .andExpect(jsonPath("$.orders[0].item").value("Ham & cheese"))
                .andExpect(jsonPath("$.orders[0].notes").value("no butter"))
                .andExpect(jsonPath("$.orders[0].placedAt").value("2026-11-18T07:45:00Z"));
    }

    @Test
    void deleteUnknownOrderGives404() throws Exception {
        mvc.perform(delete("/api/admin/orders/00000000-0000-0000-0000-000000000000").with(admin()).with(xsrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void menuCrudRoundTrip() throws Exception {
        JsonNode type = body(mvc.perform(post("/api/admin/menu/food-types").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Wraps \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Wraps"))
                .andExpect(jsonPath("$.items", hasSize(0))));
        long typeId = type.get("id").asLong();

        JsonNode item = body(mvc.perform(post("/api/admin/menu/food-types/" + typeId + "/items").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Falafel\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Falafel")));
        long itemId = item.get("id").asLong();

        mvc.perform(put("/api/admin/menu/items/" + itemId).with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Falafel wrap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.name").value("Falafel wrap"));

        mvc.perform(put("/api/admin/menu/food-types/" + typeId).with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Wrap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wrap"))
                .andExpect(jsonPath("$.items[0].name").value("Falafel wrap"));

        mvc.perform(get("/api/admin/menu").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + typeId + ")].items[0].name").value("Falafel wrap"));

        mvc.perform(post("/api/admin/menu/food-types/" + typeId + "/items").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Name must not be blank"));

        mvc.perform(delete("/api/admin/menu/items/" + itemId).with(admin()).with(xsrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/menu/items/" + itemId).with(admin()).with(xsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/menu/food-types/" + typeId).with(admin()).with(xsrf()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/admin/menu/food-types/" + typeId).with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gone\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateFoodTypeGives409() throws Exception {
        mvc.perform(post("/api/admin/menu/food-types").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Soup\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/menu/food-types").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" SOUP \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Name already exists"));
    }

    @Test
    void settingsRejects2500With400AndKeepsOldValue() throws Exception {
        String before = mvc.perform(get("/api/admin/settings").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(put("/api/admin/settings").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cutOff\":\"25:00\",\"shopEmail\":\"orders@shop.example\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("cutOff: must be HH:mm"));

        mvc.perform(get("/api/admin/settings").with(admin()))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString()).isEqualTo(before));
    }

    @Test
    void settingsRejectsBadEmail() throws Exception {
        mvc.perform(put("/api/admin/settings").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cutOff\":\"10:30\",\"shopEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("shopEmail: must be a valid email address"));
    }

    @Test
    void settingsAcceptsValidValues() throws Exception {
        mvc.perform(put("/api/admin/settings").with(admin()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cutOff\":\"10:00\",\"shopEmail\":\"orders@shop.example\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cutOff").value("10:00"))
                .andExpect(jsonPath("$.shopEmail").value("orders@shop.example"));
    }

    @Test
    void postWithoutCsrfGets403() throws Exception {
        mvc.perform(post("/api/admin/menu/food-types").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pizza\"}"))
                .andExpect(status().isForbidden());
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
