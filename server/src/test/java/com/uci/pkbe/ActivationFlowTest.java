package com.uci.pkbe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.pkbe.store.CeremonyStore;
import com.uci.pkbe.store.SessionStore;
import com.uci.pkbe.store.UserStore;
import com.uci.pkbe.webauthn.SoftPasskey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ActivationFlowTest {

    private static final String DEVICE_A = "11111111-1111-1111-1111-111111111111";
    private static final String DEVICE_B = "22222222-2222-2222-2222-222222222222";
    private static final String ORIGIN = "http://localhost:8080";
    private static final String RP_ID = "localhost";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserStore userStore;

    @Autowired
    SessionStore sessionStore;

    @Autowired
    CeremonyStore ceremonyStore;

    @BeforeEach
    void reset() {
        userStore.clear();
        sessionStore.clear();
        ceremonyStore.clear();
    }

    @Test
    void appleAppSiteAssociationIsJsonWithoutAuth() throws Exception {
        mvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webcredentials.apps[0]").value("TEAMID.com.uci.pkbe"));
    }

    @Test
    void publicConfigAndAssetLinksAreUnauthenticated() throws Exception {
        mvc.perform(get("/v1/public-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpId").value("localhost"))
                .andExpect(jsonPath("$.iosBundleId").value("com.uci.pkbe"));
        mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void meRequiresAuth() throws Exception {
        mvc.perform(get("/v1/me").header("X-Device-Id", DEVICE_A)).andExpect(status().isUnauthorized());
    }

    @Test
    void firstDeviceEnrollThenHandoverSwapsActiveDevice() throws Exception {
        String tokenA = login("alice");
        String tokenB = login("alice");

        JsonNode createA = startRegister(tokenA, DEVICE_A);
        SoftPasskey passkeyA = new SoftPasskey(userHandle(createA));
        JsonNode meAfterA = verifyRegister(tokenA, DEVICE_A, passkeyA.create(createA.get("challenge").asText(), ORIGIN, RP_ID));
        assertThat(meAfterA.get("thisDeviceStatus").asText()).isEqualTo("ACTIVE");
        assertThat(meAfterA.get("activeDeviceId").asText()).isEqualTo(DEVICE_A);

        JsonNode createB = startRegister(tokenB, DEVICE_B);
        SoftPasskey passkeyB = new SoftPasskey(userHandle(createB));
        JsonNode mePending = verifyRegister(tokenB, DEVICE_B, passkeyB.create(createB.get("challenge").asText(), ORIGIN, RP_ID));
        assertThat(mePending.get("thisDeviceStatus").asText()).isEqualTo("PENDING");
        assertThat(mePending.get("activeDeviceId").asText()).isEqualTo(DEVICE_A);

        JsonNode assertionOptions = startHandover(tokenB, DEVICE_B);
        assertThat(assertionOptions.get("allowCredentials")).isNotNull();
        assertThat(assertionOptions.get("hints").toString()).contains("hybrid");

        JsonNode afterHandover = verifyHandover(
                tokenB,
                DEVICE_B,
                passkeyA.get(assertionOptions.get("challenge").asText(), ORIGIN, RP_ID));
        assertThat(afterHandover.get("thisDeviceStatus").asText()).isEqualTo("ACTIVE");
        assertThat(afterHandover.get("activeDeviceId").asText()).isEqualTo(DEVICE_B);
        assertThat(afterHandover.get("pendingDeviceId").isNull()).isTrue();

        JsonNode deviceAView = me(tokenA, DEVICE_A);
        assertThat(deviceAView.get("thisDeviceStatus").asText()).isEqualTo("NONE");
        assertThat(deviceAView.get("activeDeviceId").asText()).isEqualTo(DEVICE_B);
    }

    @Test
    void handoverWithoutPendingEnrollFails() throws Exception {
        String tokenA = login("alice");
        JsonNode createA = startRegister(tokenA, DEVICE_A);
        SoftPasskey passkeyA = new SoftPasskey(userHandle(createA));
        verifyRegister(tokenA, DEVICE_A, passkeyA.create(createA.get("challenge").asText(), ORIGIN, RP_ID));

        String tokenB = login("alice");
        mvc.perform(post("/v1/handover/options")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + DEVICE_B + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("NO_PENDING_ENROLL"));
    }

    @Test
    void replayedHandoverAssertionFails() throws Exception {
        String tokenA = login("alice");
        String tokenB = login("alice");
        JsonNode createA = startRegister(tokenA, DEVICE_A);
        SoftPasskey passkeyA = new SoftPasskey(userHandle(createA));
        verifyRegister(tokenA, DEVICE_A, passkeyA.create(createA.get("challenge").asText(), ORIGIN, RP_ID));

        JsonNode createB = startRegister(tokenB, DEVICE_B);
        SoftPasskey passkeyB = new SoftPasskey(userHandle(createB));
        verifyRegister(tokenB, DEVICE_B, passkeyB.create(createB.get("challenge").asText(), ORIGIN, RP_ID));

        JsonNode assertionOptions = startHandover(tokenB, DEVICE_B);
        String assertion = passkeyA.get(assertionOptions.get("challenge").asText(), ORIGIN, RP_ID);
        verifyHandover(tokenB, DEVICE_B, assertion);

        mvc.perform(post("/v1/handover/verify")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + DEVICE_B + "\",\"credential\":" + assertion + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CHALLENGE_EXPIRED"));
    }

    private String login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode startRegister(String token, String deviceId) throws Exception {
        MvcResult result = mvc.perform(post("/v1/register/options")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode verifyRegister(String token, String deviceId, String credentialJson) throws Exception {
        MvcResult result = mvc.perform(post("/v1/register/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + deviceId + "\",\"credential\":" + credentialJson + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode startHandover(String token, String deviceId) throws Exception {
        MvcResult result = mvc.perform(post("/v1/handover/options")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode verifyHandover(String token, String deviceId, String credentialJson) throws Exception {
        MvcResult result = mvc.perform(post("/v1/handover/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + deviceId + "\",\"credential\":" + credentialJson + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode me(String token, String deviceId) throws Exception {
        MvcResult result = mvc.perform(get("/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Id", deviceId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private byte[] userHandle(JsonNode createOptions) {
        String id = createOptions.get("user").get("id").asText();
        return java.util.Base64.getUrlDecoder().decode(pad(id));
    }

    private static String pad(String base64Url) {
        return base64Url + "=".repeat((4 - base64Url.length() % 4) % 4);
    }
}
