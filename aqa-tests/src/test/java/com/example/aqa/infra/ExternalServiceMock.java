package com.example.aqa.infra;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class ExternalServiceMock {

    public static final String AUTH = "/auth";
    public static final String DO_ACTION = "/doAction";

    private final WireMockServer server;

    public ExternalServiceMock(int port) {
        this.server = new WireMockServer(WireMockConfiguration.options().port(port));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public void stubAuth(String token, int status) {
        stub(AUTH, token, aResponse().withStatus(status).withBody("{}"));
    }

    public void stubAuth(String token, ResponseDefinitionBuilder response) {
        stub(AUTH, token, response);
    }

    public void stubDoAction(String token, int status) {
        stub(DO_ACTION, token, aResponse().withStatus(status).withBody("{}"));
    }

    public void stubDoAction(String token, ResponseDefinitionBuilder response) {
        stub(DO_ACTION, token, response);
    }

    public void stubAuthFault(String token, Fault fault) {
        stub(AUTH, token, aResponse().withFault(fault));
    }

    public void stubDoActionFault(String token, Fault fault) {
        stub(DO_ACTION, token, aResponse().withFault(fault));
    }

    private void stub(String path, String token, ResponseDefinitionBuilder response) {
        server.stubFor(post(urlEqualTo(path))
                .withRequestBody(equalTo(formBody(token)))
                .willReturn(response));
    }

    public List<LoggedRequest> authRequests(String token) {
        return requests(AUTH, token);
    }

    public List<LoggedRequest> doActionRequests(String token) {
        return requests(DO_ACTION, token);
    }

    public List<LoggedRequest> requestsContaining(String path, String fragment) {
        return server.findAll(postRequestedFor(urlEqualTo(path)).withRequestBody(containing(fragment)));
    }

    private List<LoggedRequest> requests(String path, String token) {
        RequestPatternBuilder pattern = postRequestedFor(urlEqualTo(path))
                .withRequestBody(equalTo(formBody(token)));
        return server.findAll(pattern);
    }

    public static String formBody(String token) {
        return "token=" + token;
    }
}
