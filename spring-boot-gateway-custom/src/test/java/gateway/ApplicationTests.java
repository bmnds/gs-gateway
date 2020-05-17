package gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import redis.embedded.RedisServer;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, 
	properties = {
		"httpbin=http://localhost:${wiremock.server.port}"
		})
@AutoConfigureWireMock(port = 0)
public class ApplicationTests {

	@Autowired
	private WebTestClient webClient;
	
	private static RedisServer redisServer;
	
	@BeforeClass
	public static void setup() {
		redisServer = new RedisServer();
		redisServer.start();
	}
	
	@AfterClass
	public static void teardown() {
		redisServer.stop();
	}

	@Test
	public void testCustomHeaderAdditionOnProxiedRequest() throws Exception {
		stubFor(get(urlEqualTo("/get"))
				.willReturn(aResponse()
				.withBody("{\"headers\":{\"Hello\":\"World\"}}")
				.withHeader("Content-Type", "application/json")));

		webClient.get().uri("/get")
				.exchange()
				.expectStatus().isOk()
				.expectBody().jsonPath("$.headers.Hello").isEqualTo("World");
	}
	
	@Test
	public void testHystrixFallback() {
		stubFor(get(urlEqualTo("/delay/3"))
				.willReturn(aResponse()
				.withBody("no fallback")
				.withFixedDelay(3000)));
		
		webClient.get().uri("/delay/3").header("Host", "www.hystrix.com").exchange()
				.expectStatus().isOk().expectBody()
				.consumeWith(response -> assertThat(response.getResponseBody()).isEqualTo("fallback".getBytes()));
	}

	@Test
	public void testThrottleWithRedis() {
		stubFor(get(urlEqualTo("/anything?foo=bar")).willReturn(aResponse().withBody("{\"args\":{\"foo\":\"bar\"}}")));

		webClient.get().uri("/anything?foo=bar").header("Host", "www.throttle.com").exchange()
				.expectStatus().isOk();
		webClient.get().uri("/anything?foo=bar").header("Host", "www.throttle.com").exchange();
		webClient.get().uri("/anything?foo=bar").header("Host", "www.throttle.com").exchange()
				.expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

}
