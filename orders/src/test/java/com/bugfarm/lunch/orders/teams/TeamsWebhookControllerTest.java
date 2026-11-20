package com.bugfarm.lunch.orders.teams;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bugfarm.lunch.orders.JdbcOrderService;
import com.bugfarm.lunch.orders.OrderHandler;
import com.bugfarm.lunch.orders.fakes.FakesConfig;
import com.bugfarm.lunch.orders.parse.CommandParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TeamsWebhookController.class,
        properties = "lunch.teams.security-token=" + TeamsWebhookControllerTest.TOKEN)
@Import({TeamsSecurityConfig.class, FakesConfig.class, CommandParser.class, OrderHandler.class})
class TeamsWebhookControllerTest {

    static final String TOKEN = "dGVzdC1zZWNyZXQta2V5LTAxMjM0NTY3ODlhYmNkZWY="; // Base64("test-secret-key-0123456789abcdef")
    private static final byte[] KEY = Base64.getDecoder().decode(TOKEN);

    private static final String MENU_MESSAGE = """
            {"type":"message","id":"1","text":"<at>SandwichBot</at>&nbsp;menu",
             "from":{"id":"29:abc","name":"Jan Peeters","aadObjectId":"aad-jan"}}""";

    @Autowired MockMvc mvc;
    @MockitoBean JdbcOrderService orders;

    @Test
    void badSignatureReturns401() throws Exception {
        byte[] body = MENU_MESSAGE.getBytes(StandardCharsets.UTF_8);
        byte[] otherKey = "another-key-that-is-not-teams-0000".getBytes(StandardCharsets.UTF_8);

        mvc.perform(post("/api/teams/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", HmacVerifierTest.header(otherKey, body))
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void missingSignatureReturns401() throws Exception {
        mvc.perform(post("/api/teams/webhook").contentType(MediaType.APPLICATION_JSON).content(MENU_MESSAGE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRequestRepliesWithMessageJson() throws Exception {
        byte[] body = MENU_MESSAGE.getBytes(StandardCharsets.UTF_8);

        mvc.perform(post("/api/teams/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", HmacVerifierTest.header(KEY, body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("message"))
                .andExpect(jsonPath("$.text", startsWith("Today's menu:")));
    }

    @Test
    void signedButMalformedJsonReturns400() throws Exception {
        byte[] body = "not json".getBytes(StandardCharsets.UTF_8);

        mvc.perform(post("/api/teams/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", HmacVerifierTest.header(KEY, body))
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingSenderIdIsNotHandled() throws Exception {
        byte[] body = """
                {"type":"message","text":"<at>SandwichBot</at> sandwich: tuna","from":{"name":"Jan"}}"""
                .getBytes(StandardCharsets.UTF_8);

        mvc.perform(post("/api/teams/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", HmacVerifierTest.header(KEY, body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Sorry, I couldn't tell who sent this message."));
    }
}
