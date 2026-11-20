package com.bugfarm.lunch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bugfarm.lunch.admin.ai.ClaudeOrderInterpreter;
import com.bugfarm.lunch.admin.menu.JdbcMenuCatalog;
import com.bugfarm.lunch.admin.settings.SettingsOrderingWindow;
import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.OrderInterpreter;
import com.bugfarm.lunch.contracts.OrderService;
import com.bugfarm.lunch.contracts.OrderingWindow;
import com.bugfarm.lunch.orders.JdbcOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The real implementations from orders and admin are wired together in the app. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wiring;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class WiringTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private MenuCatalog menuCatalog;

    @Autowired
    private OrderingWindow orderingWindow;

    @Autowired
    private OrderInterpreter orderInterpreter;

    @Autowired
    private MockMvc mvc;

    @Test
    void realImplementationsAreWired() {
        assertThat(AopUtils.getTargetClass(orderService)).isEqualTo(JdbcOrderService.class);
        assertThat(AopUtils.getTargetClass(menuCatalog)).isEqualTo(JdbcMenuCatalog.class);
        assertThat(AopUtils.getTargetClass(orderingWindow)).isEqualTo(SettingsOrderingWindow.class);
        assertThat(AopUtils.getTargetClass(orderInterpreter)).isEqualTo(ClaudeOrderInterpreter.class);
    }

    @Test
    void webhookAndAdminChainsBothActive() throws Exception {
        // Teams chain (@Order(1)): no CSRF, so an unsigned POST reaches the HMAC check → 401, not 403.
        mvc.perform(post("/api/teams/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        // Admin chain (@Order(2)): not logged in → 401.
        mvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spaRoutesForwardToIndexHtml() throws Exception {
        mvc.perform(get("/settings")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/menu/edit")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void apiAndFilePathsAreNotForwarded() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(forwardedUrl(null));
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
    }
}
