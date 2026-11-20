package com.bugfarm.lunch.orders.teams;

import com.bugfarm.lunch.orders.OrderHandler;
import com.bugfarm.lunch.orders.parse.MessageText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Receives Teams outgoing-webhook calls and answers in the same thread. */
@RestController
public class TeamsWebhookController {

    private final HmacVerifier verifier;
    private final OrderHandler handler;
    private final ObjectMapper json;

    public TeamsWebhookController(HmacVerifier verifier, OrderHandler handler, ObjectMapper json) {
        this.verifier = verifier;
        this.handler = handler;
        this.json = json;
    }

    @PostMapping(path = "/api/teams/webhook", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> webhook(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (body == null || !verifier.verify(body, authorization)) {
            return ResponseEntity.status(401).build();
        }
        JsonNode message;
        try {
            message = json.readTree(body);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
        if (message == null || !message.isObject()) {
            return ResponseEntity.badRequest().build();
        }
        String userId = message.path("from").path("aadObjectId").asText("").trim();
        String name = message.path("from").path("name").asText("").trim();
        String text = MessageText.clean(message.path("text").asText(""));
        String reply = userId.isEmpty()
                ? "Sorry, I couldn't tell who sent this message."
                : handler.handle(userId, name, text);
        return ResponseEntity.ok(Map.of("type", "message", "text", reply));
    }
}
