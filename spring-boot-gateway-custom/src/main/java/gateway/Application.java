package gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@EnableConfigurationProperties(UriConfiguration.class)
@SpringBootApplication
@RestController
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Bean
	public RouteLocator myRoutes(RouteLocatorBuilder builder, UriConfiguration configuration, RedisRateLimiter redisRateLimiter) {
		return builder.routes()
		        .route(p -> p
		            .path("/get")
		            .filters(f -> f.addRequestHeader("X-CUSTOM-HEADER", "Hello, World!"))
		            .uri(configuration.getHttpbin()))
		        .route(p -> p
		                .host("*.hystrix.com")
		                .filters(f -> f.hystrix(config -> config
		                		.setName("mycmd")
		                		.setFallbackUri("forward:/fallback")))
		                .uri(configuration.getHttpbin()))
		        .route(p -> p
		                .host("*.throttle.com")
		                .filters(f -> f.requestRateLimiter(config -> config
		                		.setRateLimiter(redisRateLimiter)))
		                .uri(configuration.getHttpbin()))
		        .build();
	}
	
	@RequestMapping("/fallback")
	public Mono<String> fallback() {
	  return Mono.just("fallback");
	}
	
	@Bean
	RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(1, 1);
	}
	
	@Bean
	KeyResolver userKeyResolver() {
		return exchange -> Mono.just(/* exchange.getRequest().getQueryParams().getFirst("user") */"throttle");
	}

}

@ConfigurationProperties
class UriConfiguration {

	private String httpbin = "http://httpbin.org:80";

	public String getHttpbin() {
		return httpbin;
	}

	public void setHttpbin(String httpbin) {
		this.httpbin = httpbin;
	}
	
}
