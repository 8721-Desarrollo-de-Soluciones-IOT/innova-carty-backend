package com.innovacarty.platform.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Steps of the Sprint 2 features. They call the API like its three clients do: the ESP32 of the
 * cart (X-Device-Key), the shopper app (JWT of a SHOPPER) and the Web Console (JWT of an ADMIN).
 */
public class SmartCartSteps {
    private static final AtomicInteger NEXT_CART = new AtomicInteger(900);
    private static final String ADMIN_EMAIL = "lucia.medina@retailco.pe";
    private static final String DEMO_PASSWORD = "demo1234";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Value("${innovacarty.device.api-key}")
    private String deviceKey;
    @Value("${innovacarty.payments.webhook-secret}")
    private String webhookSecret;

    private String cartId;
    private String shopperToken;
    private List<Map<String, Object>> lastItems = new ArrayList<>();
    private MockHttpServletResponse response;

    // ------------------------------------------------------------------ cart (ESP32)

    @Given("un carrito inteligente nuevo conectado a la tienda")
    public void aNewCart() throws Exception {
        cartId = "CART-" + NEXT_CART.getAndIncrement();
        deviceEvent("BOOT", "Entrada");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Given("el carrito reporta los siguientes tags RFID:")
    public void theCartReportsTags(DataTable table) throws Exception {
        lastItems = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            lastItems.add(Map.of("tag", row.get("tag"), "quantity", Integer.parseInt(row.get("cantidad"))));
        }
        deviceState(lastItems, -1, deviceKey);
    }

    @Given("la celda de carga reporta un peso estable de {int} g")
    public void theLoadCellReports(int grams) throws Exception {
        deviceState(lastItems, grams, deviceKey);
    }

    @When("el carrito reporta su estado con la clave de dispositivo {string}")
    public void theCartReportsWithKey(String key) throws Exception {
        deviceState(List.of(), -1, key);
    }

    @When("el carrito reporta el evento {string} en la zona {string}")
    public void theCartReportsEvent(String type, String zone) throws Exception {
        deviceEvent(type, zone);
    }

    @Then("el carrito queda en estado {string}")
    public void theCartStatusIs(String status) throws Exception {
        MockHttpServletResponse heartbeat = call(get("/api/v1/devices/carts/" + cartId).header("X-Device-Key", deviceKey));
        assertThat(read(heartbeat, "$.status", String.class)).isEqualTo(status);
    }

    @Then("el carrito tiene {int} productos y un total de S\\/ {double}")
    public void theCartHas(int items, double total) throws Exception {
        MockHttpServletResponse heartbeat = call(get("/api/v1/devices/carts/" + cartId).header("X-Device-Key", deviceKey));
        assertThat(read(heartbeat, "$.itemsCount", Integer.class)).isEqualTo(items);
        assertThat(read(heartbeat, "$.total", Double.class)).isEqualTo(total);
    }

    // ------------------------------------------------------------------ shopper app

    @Given("un comprador registrado en la app")
    public void aRegisteredShopper() throws Exception {
        String email = "comprador." + UUID.randomUUID().toString().substring(0, 8) + "@test.pe";
        response = send(post("/api/v1/authentication/sign-up"),
                Map.of("fullName", "Comprador de Prueba", "email", email, "password", DEMO_PASSWORD), null);
        assertThat(response.getStatus()).isEqualTo(201);
        shopperToken = read(response, "$.token", String.class);
    }

    @Given("el comprador vincula el carrito escaneando su QR")
    public void theShopperLinksTheCart() throws Exception {
        response = send(post("/api/v1/me/cart"), Map.of("cartId", cartId), shopperToken);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @When("el comprador consulta su carrito")
    public void theShopperOpensTheCart() throws Exception {
        response = call(get("/api/v1/me/cart").header("Authorization", "Bearer " + shopperToken));
    }

    @Then("la app muestra {int} productos y un total de S\\/ {double}")
    public void theAppShows(int items, double total) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read(response, "$.cart.itemsCount", Integer.class)).isEqualTo(items);
        assertThat(read(response, "$.cart.total", Double.class)).isEqualTo(total);
        assertThat(read(response, "$.session.total", Double.class)).isEqualTo(total);
    }

    @When("el comprador fija un presupuesto de S\\/ {double}")
    public void theShopperSetsABudget(double budget) throws Exception {
        response = send(patch("/api/v1/me/cart"), Map.of("budget", budget), shopperToken);
    }

    @Then("el presupuesto del carrito es S\\/ {double}")
    public void theBudgetIs(double budget) throws Exception {
        assertThat(read(response, "$.cart.budget", Double.class)).isEqualTo(budget);
        assertThat(read(response, "$.session.budget", Double.class)).isEqualTo(budget);
    }

    @Given("el comprador paga con {string}")
    public void theShopperPays(String method) throws Exception {
        response = send(post("/api/v1/payments"), Map.of("cartId", cartId, "method", method), shopperToken);
    }

    @Then("el pago queda aprobado por S\\/ {double} con un código de operación de 8 dígitos")
    public void thePaymentIsApproved(double amount) throws Exception {
        assertThat(read(response, "$.status", String.class)).isEqualTo("APPROVED");
        assertThat(read(response, "$.amount", Double.class)).isEqualTo(amount);
        assertThat(read(response, "$.operationCode", String.class)).matches("\\d{8}");
    }

    @When("el comprador consulta sus compras")
    public void theShopperListsPurchases() throws Exception {
        response = call(get("/api/v1/me/sessions").header("Authorization", "Bearer " + shopperToken));
    }

    // ------------------------------------------------------------------ payment provider (webhook)

    @Given("el proveedor confirma el pago {string} del evento {string} por S\\/ {double}")
    public void theProviderConfirms(String provider, String eventId, double amount) throws Exception {
        webhook(provider, eventId, amount, "APPROVED", webhookSecret);
    }

    @When("el proveedor rechaza el pago {string} del evento {string}")
    public void theProviderRejects(String provider, String eventId) throws Exception {
        webhook(provider, eventId, 0, "REJECTED", webhookSecret);
    }

    @When("llega una notificación de pago con el secreto {string}")
    public void aNotificationWithSecret(String secret) throws Exception {
        webhook("YAPE", "evt-sin-secreto", 9.80, "APPROVED", secret);
    }

    @Then("el webhook responde {string}")
    public void theWebhookAnswers(String result) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read(response, "$.result", String.class)).isEqualTo(result);
    }

    @Then("el carrito tiene {int} pago registrado")
    public void theCartHasPayments(int count) throws Exception {
        MockHttpServletResponse list = call(get("/api/v1/payments").param("cartId", cartId)
                .header("Authorization", "Bearer " + adminToken()));
        assertThat(read(list, "$.length()", Integer.class)).isEqualTo(count);
    }

    // ------------------------------------------------------------------ Web Console (admin)

    @When("el administrador consulta las órdenes de compra del carrito")
    public void theAdminListsOrders() throws Exception {
        response = call(get("/api/v1/shopping-sessions").param("cartId", cartId)
                .header("Authorization", "Bearer " + adminToken()));
    }

    @Then("la orden aparece con estado {string}, {int} líneas de producto y un total de S\\/ {double}")
    public void theOrderIs(String status, int lines, double total) throws Exception {
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read(response, "$[0].cartId", String.class)).isEqualTo(cartId);
        assertThat(read(response, "$[0].status", String.class)).isEqualTo(status);
        assertThat(read(response, "$[0].items.length()", Integer.class)).isEqualTo(lines);
        assertThat(read(response, "$[0].total", Double.class)).isEqualTo(total);
    }

    @Then("la consola muestra una alerta {string} abierta para el carrito")
    public void theConsoleShowsAnAlert(String type) throws Exception {
        MockHttpServletResponse alerts = call(get("/api/v1/alerts").param("cartId", cartId).param("status", "OPEN")
                .header("Authorization", "Bearer " + adminToken()));
        List<String> types = JsonPath.read(alerts.getContentAsString(), "$[*].type");
        assertThat(types).contains(type);
    }

    // ------------------------------------------------------------------ common

    @Then("la respuesta tiene el código {int}")
    public void theResponseCodeIs(int status) {
        assertThat(response.getStatus()).isEqualTo(status);
    }

    private void deviceState(List<Map<String, Object>> items, int measuredWeight, String key) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("measuredWeight", measuredWeight);
        body.put("battery", 90);
        body.put("zone", "Pasillo 3");
        response = call(put("/api/v1/devices/carts/" + cartId + "/state").header("X-Device-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    /** The event id gets the cart id so every scenario sends its own provider events. */
    private void webhook(String provider, String eventId, double amount, String status, String secret) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId + "-" + cartId);
        body.put("provider", provider);
        body.put("cartId", cartId);
        body.put("operationCode", "12345678");
        body.put("amount", amount);
        body.put("status", status);
        response = call(post("/api/v1/payments/webhook").header("X-Webhook-Secret", secret)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private void deviceEvent(String type, String zone) throws Exception {
        response = call(post("/api/v1/devices/carts/" + cartId + "/events").header("X-Device-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type", type, "detail", "", "zone", zone))));
    }

    private String adminToken() throws Exception {
        MockHttpServletResponse signIn = send(post("/api/v1/authentication/sign-in"),
                Map.of("email", ADMIN_EMAIL, "password", DEMO_PASSWORD), null);
        assertThat(signIn.getStatus()).isEqualTo(200);
        return read(signIn, "$.token", String.class);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return call(request);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private static <T> T read(MockHttpServletResponse response, String path, Class<T> type) throws Exception {
        return JsonPath.parse(response.getContentAsString()).read(path, type);
    }
}
