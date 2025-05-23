package es.iti.wakamiti.rest;


import com.fasterxml.jackson.databind.JsonNode;
import es.iti.wakamiti.api.WakamitiException;
import es.iti.wakamiti.api.datatypes.Assertion;
import es.iti.wakamiti.api.plan.DataTable;
import es.iti.wakamiti.api.plan.Document;
import es.iti.wakamiti.api.util.JsonUtils;
import es.iti.wakamiti.api.util.MatcherAssertion;
import es.iti.wakamiti.api.util.XmlUtils;
import es.iti.wakamiti.api.util.http.oauth.GrantType;
import es.iti.wakamiti.api.util.http.oauth.Oauth2ProviderConfig;
import io.restassured.RestAssured;
import org.apache.xmlbeans.XmlObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static es.iti.wakamiti.rest.TestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;


@RunWith(MockitoJUnitRunner.class)
public class RestStepContributorTest {

    private static final Integer PORT = 4321;
    private static final String BASE_URL = String.format("https://localhost:%s", PORT);
    private static final String TOKEN_PATH = "wakamiti/data/token.txt";

    private static final ClientAndServer client = startClientAndServer(PORT);

    private final RestConfigContributor configurator = new RestConfigContributor();
    @Spy
    private RestStepContributor contributor;

    @BeforeClass
    public static void setup() {
        HttpsURLConnection.setDefaultSSLSocketFactory(new KeyStoreFactory(
                Configuration.configuration(),
                new MockServerLogger()).sslContext().getSocketFactory());
    }

    @AfterClass
    public static void shutdown() {
        client.close();
    }

    @Before
    public void beforeEach() throws NoSuchFieldException, IllegalAccessException {
        configurator.configurer().configure(contributor, configurator.defaultConfiguration().appendFromPairs(
                RestConfigContributor.BASE_URL, BASE_URL
        ));
        RestAssured.config = RestAssured.config().multiPartConfig(
                RestAssured.config().getMultiPartConfig().defaultBoundary("asdf1234")
        );
        keys().clear();
        client.reset();
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testWhenDefaultsWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader(
                                header("Content-Type", String.format("%s.*", MediaType.APPLICATION_JSON))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setContentType(String)}
     */
    @Test
    public void testSetContentTypeWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader(
                                header("Content-Type", String.format("%s.*", MediaType.APPLICATION_XML))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setContentType("XML");
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setContentType(String)}
     */
    @Test(expected = WakamitiException.class)
    public void testSetContentTypeWithError() {
        // act
        contributor.setContentType("AAA");
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testJsonResponseWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"name\":\"Susan\",\"ape1\":\"Martin\"}")
                        .withHeaders(
                                header("vary", "Origin"),
                                header("vary", "Access-Control-Request-Method"),
                                header("vary", "Access-Control-Request-Headers")
                        )
        );

        // act
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
        assertThat(JsonUtils.readStringValue(result, "body.name")).isEqualTo("Susan");
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} json response
     */
    @Test
    public void testJsonResponseWhenNullBodyWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
        assertThat(JsonUtils.readStringValue(result, "body")).isNull();
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} xml response
     */
    @Test
    public void testXmlResponseWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_XML)
                        .withBody("<item><name>Susan</name><ape1>Martin</ape1></item>")
                        .withHeaders(
                                header("vary", "Origin"),
                                header("vary", "Access-Control-Request-Method"),
                                header("vary", "Access-Control-Request-Headers")
                        )
        );

        // act
        XmlObject result = (XmlObject) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(XmlUtils.readStringValue(result, "statusCode")).isEqualTo("200");
        assertThat(XmlUtils.readStringValue(result, "body.item.name")).isEqualTo("Susan");
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} xml null response
     */
    @Test
    public void testXmlResponseWhenNullBodyWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_XML)
        );

        // act
        XmlObject result = (XmlObject) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(XmlUtils.readStringValue(result, "statusCode")).isEqualTo("200");
        assertThat(XmlUtils.readStringValue(result, "body")).isNull();
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} text response
     */
    @Test
    public void testTextResponseWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.TEXT_PLAIN)
                        .withBody("5567")
                        .withHeaders(
                                header("vary", "Origin"),
                                header("vary", "Access-Control-Request-Method"),
                                header("vary", "Access-Control-Request-Headers")
                        )
        );

        // act
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
        assertThat(JsonUtils.readStringValue(result, "body")).isEqualTo("5567");
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} text null response
     */
    @Test
    public void testTextResponseWhenNullBodyWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.TEXT_PLAIN)
        );

        // act
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
        assertThat(JsonUtils.readStringValue(result, "body")).isNull();
    }

    /**
     * Test {@link RestStepContributor#setRequestParameter(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testRequestParametersWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withQueryStringParameters(
                                param("param1", "value1"),
                                param("param2", "value2")
                        )
                ,
                response()
                        .withStatusCode(200)
        );

        // act
        contributor.setRequestParameter("param1", "value1");
        contributor.setRequestParameter("param2", "value2");
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setRequestParameters(DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testRequestParameterListWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withQueryStringParameters(
                                param("param1", "value1"),
                                param("param2", "value2")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setRequestParameters(dataTable("param1", "value1", "param2", "value2"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setQueryParameter(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testQueryParametersWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withQueryStringParameters(
                                param("param1", "value1"),
                                param("param2", "value2")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setQueryParameter("param1", "value1");
        contributor.setQueryParameter("param2", "value2");
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setQueryParameters(DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testQueryParameterListWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withQueryStringParameters(
                                param("param1", "value1"),
                                param("param2", "value2")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setQueryParameters(dataTable("param1", "value1", "param2", "value2"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setPathParameter(String, String)},
     * {@link RestStepContributor#setService(String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testPathParametersWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/users/10/list/4")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setService("users/{user}/list/{list}");
        contributor.setPathParameter("user", "10");
        contributor.setPathParameter("list", "4");
        JsonNode result = (JsonNode) contributor.executeGetQuery();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setPathParameters(DataTable)},
     * {@link RestStepContributor#setService(String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testPathParameterListWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/users/10/list/4")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setService("/users/{user}/list/{list}");
        contributor.setPathParameters(dataTable("user", "10", "list", "4"));
        JsonNode result = (JsonNode) contributor.executeGetQuery();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setPathParameters(DataTable)},
     * {@link RestStepContributor#setService(String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testPathParameterListWhenIncorrectColumnsWithError() {
        // prepare
        mockServer(
                request()
                        .withPath("/users/10/list/4")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setService("/users/{user}/list/{list}");
        contributor.setPathParameters(new DataTable(new String[][]{
                new String[]{"column1"}, new String[]{"value1"}
        }));
        contributor.executeGetQuery();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setHeader(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testHeadersWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeaders(
                                header("param1", "value1", "value2"),
                                header("param2", "value1", "value2"),
                                header("Accept-Language", "es")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setHeader("param1", "value1");
        contributor.setHeader("param1", "value2");
        contributor.setHeader("param2", "value1;value2");
        contributor.setHeader("Accept-Language", "es");
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setHeaders(DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testHeaderListWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeaders(
                                header("param1", "value1", "value2"),
                                header("param2", "value2"),
                                header("Accept-Language", "*")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setHeaders(dataTable("param1", "value1;value2", "param2", "value2"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setHeaders(DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testHeaderListWhenIncorrectColumnsWithError() {
        // act
        contributor.setHeaders(
                new DataTable(new String[][]{
                        new String[]{"column1"}, new String[]{"value1"}
                })
        );
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setTimeout(Duration)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = SocketTimeoutException.class)
    public void testSetTimeoutWithError() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                ,
                response()
                        .withDelay(new Delay(TimeUnit.SECONDS, 5))
        );

        // act
        contributor.setTimeout(Duration.ofSeconds(1));
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBasicAuth(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBasicAuthWithSuccess() {
        // prepare
        String token = Base64.getEncoder().encodeToString("username:password".getBytes());

        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Authorization", "Basic " + token)
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_XML)
        );

        // act
        contributor.setBasicAuth("username", "password");
        XmlObject result = (XmlObject) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(XmlUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthClient()} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBearerAuthClientWithSuccess()
            throws NoSuchFieldException, IllegalAccessException, MalformedURLException {
        // prepare
        String token = "1234567890";

        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/token")
                        .withBody(
                                params(
                                        param("grant_type", "client_credentials")
                                )
                        )
                ,
                response(json(map("access_token", token)))
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthClient();
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(keys()).containsValue(token);
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("404");
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthClient(DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBearerAuthClientWhenScopeWithSuccess()
            throws MalformedURLException, NoSuchFieldException, IllegalAccessException {
        // prepare
        String token = "1234567890";

        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/token")
                        .withBody(
                                params(
                                        param("grant_type", "client_credentials"),
                                        param("scope", "something")
                                )
                        )
                ,
                response(json(map("access_token", token)))
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthClient(dataTable("scope", "something"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(keys()).containsValue(token);
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("404");
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthClient()} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBearerAuthClientWhenCachedWithSuccess() throws MalformedURLException {
        // prepare
        String token = "1234567890";

        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");
        contributor.oauth2Provider.configuration().cacheAuth(true);

        mockServer(
                request()
                        .withPath("/token")
                        .withBody(
                                params(
                                        param("grant_type", "client_credentials")
                                )
                        )
                ,
                response(json(map("access_token", token)))
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)

        );

        // act
        contributor.setBearerAuthClient();
        // If it calls the service more than once, it will throw an error
        contributor.executeGetSubject();
        contributor.executeGetSubject();

        // check
        verify(contributor, times(1)).retrieveOauthToken(any());
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBearerAuthPasswordWithSuccess()
            throws MalformedURLException, NoSuchFieldException, IllegalAccessException {
        // prepare
        String token = "1234567890";

        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/token")
                        .withBody(
                                params(
                                        param("grant_type", "password"),
                                        param("username", "username"),
                                        param("password", "password")
                                )
                        )
                ,
                response(json(map("access_token", token)))
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthPassword("username", "password");
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(keys()).containsValue(token);
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("404");
    }

    /**
     * Test {@link
     * RestStepContributor#setBearerAuthPassword(String, String, DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBearerAuthPasswordWhenScopeWithSuccess()
            throws MalformedURLException, NoSuchFieldException, IllegalAccessException {
        // prepare
        String token = "1234567890";

        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/token")
                        .withBody(
                                params(
                                        param("grant_type", "password"),
                                        param("username", "username"),
                                        param("password", "password"),
                                        param("scope", "something")
                                )
                        )
                ,
                response(json(map("access_token", token)))
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthPassword("username", "password", dataTable("scope", "something"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(keys()).containsValue(token);
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("404");
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetBearerAuthPasswordWhenCachedWithSuccess() throws MalformedURLException {
        // prepare
        String token = "1234567890";

        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");
        contributor.oauth2Provider.configuration().cacheAuth(true);

        mockServer(
                request()
                        .withPath("/token")
                        .withBody(
                                params(
                                        param("grant_type", "password"),
                                        param("username", "username"),
                                        param("password", "password")
                                )
                        )
                ,
                response(json(map("access_token", token)))
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthPassword("username", "password");
        // If it calls the service more than once, it will throw an error
        contributor.executeGetSubject();
        contributor.executeGetSubject();

        // check
        verify(contributor, times(1)).retrieveOauthToken(any());
    }

    /**
     * Test {@link RestStepContributor#setNoneAuth()},
     * {@link RestStepContributor#setHeader(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetNoneAuthWithSuccess() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/")
                        .withHeader(
                                header(not("Authorization"))
                        )
                ,
                response()
                        .withStatusCode(401)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        contributor.setHeader("Authorization", "loquesea");

        // act
        contributor.setNoneAuth(); // auth must be overridden
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("401");
    }

    /**
     * Test {@link RestStepContributor#setBearerDefault()} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testSetBearerAuthPasswordWhenNoGrantTypeConfigWithError() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));

        // act
        contributor.setBearerDefault();
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testSetBearerAuthPasswordWhenNoUrlConfigWithError() {
        // prepare
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        // act
        contributor.setBearerAuthPassword("username", "password");
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testSetBearerAuthPasswordWhenNoClientIdConfigWithError() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        // act
        contributor.setBearerAuthPassword("username", "password");
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testSetBearerAuthPasswordWhenNoClientSecretConfigWithError() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");

        // act
        contributor.setBearerAuthPassword("username", "password");
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerDefault()} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testSetBearerAuthPasswordWhenNoRequiredParamConfigWithError() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().type(GrantType.PASSWORD);

        // act
        contributor.setBearerDefault();
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = AssertionError.class)
    public void testSetBearerAuthPasswordWhenCodeErrorWithError() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/token")
                ,
                response()
                        .withStatusCode(400)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthPassword("username", "password");
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthPassword(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = AssertionError.class)
    public void testSetBearerAuthPasswordWhenTokenMissingWithError() throws MalformedURLException {
        // prepare
        contributor.oauth2Provider.configuration().url(new URL(BASE_URL.concat("/token")));
        contributor.oauth2Provider.configuration().clientId("WEB_APP");
        contributor.oauth2Provider.configuration().clientSecret("ytv8923yy9234y96");

        mockServer(
                request()
                        .withPath("/token")
                ,
                response(json(map("other", "123")))
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        contributor.setBearerAuthPassword("username", "password");

        // act
        contributor.executeGetSubject();

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setBearerAuthFile(File)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testWhenAuthHeaderWithSuccess() {
        // prepare
        String token = "1234567890";

        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Authorization", "Bearer " + token)
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setBearerAuthFile(file(TOKEN_PATH));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setAttachedFile(String, Document)}  and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetAttachedFileWithSuccess() throws IOException {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Content-Type",
                                MediaType.MULTIPART_FORM_DATA + "; boundary="
                                        + RestAssured.config().getMultiPartConfig().defaultBoundary())
                        .withBody(
                                attached(
                                        file(
                                                RestAssured.config().getMultiPartConfig().defaultSubtype(),
                                                MediaType.TEXT_PLAIN.toString(),
                                                RestAssured.config().getMultiPartConfig().defaultControlName(),
                                                "Test content"
                                        ),
                                        file(
                                                RestAssured.config().getMultiPartConfig().defaultSubtype(),
                                                MediaType.TEXT_PLAIN.toString(),
                                                RestAssured.config().getMultiPartConfig().defaultControlName(),
                                                "Test content 2"
                                        )
                                )
                        )
                        .withBody(
                                regex(".*file\\.txt.*")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setAttachedFile("file", new Document("Test content"));
        contributor.setAttachedFile("file", new Document("Test content 2"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setAttachedFile(String, Document)}  and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetAttachedFileWhenContentTypeWithSuccess() throws IOException {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Content-Type",
                                MediaType.MULTIPART_FORM_DATA + "; boundary="
                                        + RestAssured.config().getMultiPartConfig().defaultBoundary())
                        .withBody(
                                attached(
                                        file(
                                                RestAssured.config().getMultiPartConfig().defaultSubtype(),
                                                MediaType.APPLICATION_JSON.toString(),
                                                "fichero",
                                                json(map("user", "Pepe"))
                                        )
                                )
                        )
                        .withBody(
                                regex(".*file\\.json.*")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setAttachedFile("fichero",
                new Document(json(map("user", "Pepe")), "json"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setAttachedFile(String, Document)},
     * {@link RestStepContributor#setMultipartSubtype(String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetAttachedFileWhenSubtypeWithSuccess() throws IOException {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Content-Type",
                                "multipart/mixed; boundary="
                                        + RestAssured.config().getMultiPartConfig().defaultBoundary())
                        .withBody(
                                regex(".*")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setMultipartSubtype("mixed");
        contributor.setAttachedFile("file", new Document("Test content"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setAttachedFile(String, Document)},
     * {@link RestStepContributor#setFilename(String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetAttachedFileWhenFilenameWithSuccess() throws IOException {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Content-Type",
                                MediaType.MULTIPART_FORM_DATA + "; boundary="
                                        + RestAssured.config().getMultiPartConfig().defaultBoundary())
                        .withBody(
                                regex(".*fichero\\.txt.*")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setFilename("fichero");
        contributor.setAttachedFile("file", new Document("Test content"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setAttachedFile(String, File)}  and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testSetAttachedFileWhenFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader("Content-Type",
                                MediaType.MULTIPART_FORM_DATA
                                        + "; boundary=" + RestAssured.config().getMultiPartConfig().defaultBoundary())
                        .withBody(
                                attached(
                                        file(
                                                RestAssured.config().getMultiPartConfig().defaultSubtype(),
                                                MediaType.TEXT_PLAIN.toString(),
                                                RestAssured.config().getMultiPartConfig().defaultControlName(),
                                                "1234567890"
                                        )
                                )
                        )
                        .withBody(
                                regex(".*token\\.txt.*")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setAttachedFile("file", file(TOKEN_PATH));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setAttachedFile(String, File)}  and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test(expected = WakamitiException.class)
    public void testSetAttachedFileWhenFileNotFoundWithError() {
        // act
        contributor.setAttachedFile("file", new File("file.tmp"));

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#setFormParameter(String, String)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testWithFormParametersWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader(
                                header("Content-Type",
                                        String.format("%s.*", MediaType.APPLICATION_FORM_URLENCODED))
                        )
                        .withQueryStringParameters(
                                param("param1", "value1"),
                                param("param2", "value2")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setFormParameter("param1", "value1");
        contributor.setFormParameter("param2", "value2");
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setFormParameters(DataTable)} and
     * {@link RestStepContributor#executeGetSubject()}
     */
    @Test
    public void testWithFormParametersListWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withPath("/")
                        .withHeader(
                                header("Content-Type",
                                        String.format("%s.*", MediaType.APPLICATION_FORM_URLENCODED))
                        )
                        .withQueryStringParameters(
                                param("param1", "value1"),
                                param("param2", "value2")
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setFormParameters(dataTable("param1", "value1", "param2", "value2"));
        JsonNode result = (JsonNode) contributor.executeGetSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executeDeleteDataUsingDocument(Document)}
     */
    @Test
    public void testDeleteDataWhenDocumentWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("DELETE")
                        .withBody(json(map("user", "Pepe")))
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor
                .executeDeleteDataUsingDocument(new Document(json(map("user", "Pepe"))));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executeDeleteDataUsingFile(File)}
     */
    @Test
    public void testDeleteDataWhenFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("DELETE")
                        .withBody("1234567890")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executeDeleteDataUsingFile(file(TOKEN_PATH));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePutSubjectUsingDocument(Document)}
     */
    @Test
    public void testWhenPutSubjectDocumentWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PUT")
                        .withPath("/")
                        .withBody(json(map("user", "Pepe")))
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePutSubjectUsingDocument(
                new Document(json(map("user", "Pepe"))));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePutSubjectUsingFile(File)}
     */
    @Test
    public void testWhenPutSubjectFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PUT")
                        .withPath("/")
                        .withBody("1234567890")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePutSubjectUsingFile(file(TOKEN_PATH));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setRequestParameter(String, String)} and
     * {@link RestStepContributor#executePutSubject()}
     */
    @Test
    public void testWhenPutSubjectAndParamsWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PUT")
                        .withPath("/")
                        .withQueryStringParameter("param1", "value1")
                        .withBody(
                                Not.not(regex(".+"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        contributor.setRequestParameter("param1", "value1");

        // act
        JsonNode result = (JsonNode) contributor.executePutSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePutSubject()}
     */
    @Test
    public void testWhenPutSubjectWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PUT")
                        .withPath("/")
                        .withBody(
                                Not.not(regex(".+"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePutSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePutSubjectUsingDocument(Document)}
     */
    @Test
    public void testWhenPatchSubjectDocumentWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PATCH")
                        .withPath("/")
                        .withBody(json(map("user", "Pepe")))
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor
                .executePatchSubjectUsingDocument(new Document(json(map("user", "Pepe"))));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePutSubjectUsingFile(File)}
     */
    @Test
    public void testWhenPatchSubjectFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PATCH")
                        .withPath("/")
                        .withBody("1234567890")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePatchSubjectUsingFile(file(TOKEN_PATH));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setRequestParameter(String, String)} and
     * {@link RestStepContributor#executePatchSubject()}
     */
    @Test
    public void testWhenPatchSubjectAndParamsWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PATCH")
                        .withPath("/")
                        .withQueryStringParameter("param1", "value1")
                        .withBody(
                                Not.not(regex(".+"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setRequestParameter("param1", "value1");
        JsonNode result = (JsonNode) contributor.executePatchSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePatchSubject()}
     */
    @Test
    public void testWhenPatchSubjectWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("PATCH")
                        .withPath("/")
                        .withBody(
                                Not.not(regex(".+"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );


        // act
        JsonNode result = (JsonNode) contributor.executePatchSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePatchSubjectUsingDocument(Document)}
     */
    @Test
    public void testWhenPostSubjectDocumentWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody(json(map("user", "Pepe")))
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );


        // act
        JsonNode result = (JsonNode) contributor
                .executePostSubjectUsingDocument(new Document(json(map("user", "Pepe"))));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePatchSubjectUsingFile(File)}
     */
    @Test
    public void testWhenPostSubjectFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody("1234567890")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePostSubjectUsingFile(file(TOKEN_PATH));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setRequestParameter(String, String)} and
     * {@link RestStepContributor#executePostSubject()}
     */
    @Test
    public void testWhenPostSubjectAndParamWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody(
                                params(param("param1", "value1"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setRequestParameter("param1", "value1");
        JsonNode result = (JsonNode) contributor.executePostSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePostSubject()}
     */
    @Test
    public void testWhenPostSubjectWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody(
                                Not.not(regex(".+"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePostSubject();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePostSubjectUsingDocument(Document)}
     */
    @Test
    public void testWhenPostDataDocumentWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody(json(map("user", "Pepe")))
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor
                .executePostDataUsingDocument(new Document(json(map("user", "Pepe"))));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePostSubjectUsingFile(File)}
     */
    @Test
    public void testWhenPostDataFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody("1234567890")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePostDataUsingFile(file(TOKEN_PATH));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executeDeleteDataUsingFile(File)}
     */
    @Test
    public void testWhenDeleteDataFileWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("DELETE")
                        .withPath("/")
                        .withBody("1234567890")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executeDeleteDataUsingFile(file(TOKEN_PATH));

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#setRequestParameter(String, String)} and
     * {@link RestStepContributor#executePostData()}
     */
    @Test
    public void testWhenPostDataAndParamWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody(
                                params(param("param1", "value1"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        contributor.setRequestParameter("param1", "value1");
        JsonNode result = (JsonNode) contributor.executePostData();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#executePostData()}
     */
    @Test
    public void testWhenPostDataWithSuccess() {
        // prepare
        mockServer(
                request()
                        .withMethod("POST")
                        .withPath("/")
                        .withBody(
                                Not.not(regex(".+"))
                        )
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
        );

        // act
        JsonNode result = (JsonNode) contributor.executePostData();

        // check
        assertThat(result).isNotNull();
        assertThat(JsonUtils.readStringValue(result, "statusCode")).isEqualTo("200");
    }

    /**
     * Test {@link RestStepContributor#assertHttpCode(Assertion)}
     */
    @Test(expected = WakamitiException.class)
    public void testWhenResponseIsNullWithError() {
        contributor.assertHttpCode(new MatcherAssertion<>(equalTo(200)));
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} and
     * {@link RestStepContributor#assertBodyStrictComparison(Document)}
     */
    @Test(expected = WakamitiException.class)
    public void testWhenResponseHasInvalidContentTypeWithError() {
        // prepare
        mockServer(
                request()
                        .withMethod("GET")
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_BINARY)
                        .withBody("test")
        );

        // act
        contributor.executeGetSubject();
        contributor.assertBodyStrictComparison(new Document("test"));

        // check
        // An error should be thrown
    }

    /**
     * Test {@link RestStepContributor#executeGetSubject()} and
     * {@link RestStepContributor#assertBodyStrictComparison(Document)}
     */
    @Test(expected = WakamitiException.class)
    public void testWhenResponseHasInvalidContentTypeHelperWithError() {
        // prepare
        mockServer(
                request()
                        .withMethod("GET")
                        .withPath("/")
                ,
                response()
                        .withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_OCTET_STREAM)
                        .withBody("test")
        );

        // act
        contributor.executeGetSubject();
        contributor.assertBodyStrictComparison(new Document("test"));

        // check
        // An error should be thrown
    }


    private void mockServer(HttpRequest expected, HttpResponse response) {
        client.when(expected, Times.once()).respond(response);
    }

    private DataTable dataTable(String... data) {
        List<String[]> result = new LinkedList<>();
        result.add(new String[]{"name", "value"});
        for (int i = 0; i < data.length; i = i + 2) {
            result.add(new String[]{data[i], data[i + 1]});
        }
        return new DataTable(result.toArray(new String[0][0]));
    }

    @SuppressWarnings("unchecked")
    private Map<List<String>, String> keys() throws NoSuchFieldException, IllegalAccessException {
        Field field = Oauth2ProviderConfig.class.getDeclaredField("cachedToken");
        field.setAccessible(true);
        return ((Map<List<String>, String>) field.get(null));
    }

}
