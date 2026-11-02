package com.stablecoin.config;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String ACTUATOR_TAG = "Actuator";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("StableCoin API")
			.description("Outbox, Idempotence, Dual Write")
			.version("0.0.1"));
	}

	/**
	 * springdoc.show-actuator 로 생성된 Actuator 문서를 보완한다.
	 * 기본 문서에는 /actuator/health 의 200 응답만 있고, 구성요소별 경로(/actuator/health/chain)는 없다.
	 */
	@Bean
	public OpenApiCustomizer actuatorHealthDocs() {
		return openApi -> {
			PathItem health = openApi.getPaths().get("/actuator/health");
			if (health == null || health.getGet() == null) {
				return;
			}
			health.getGet()
				.summary("전체 헬스 체크")
				.description("노드(chain), 디스크 등 모든 구성요소의 상태를 조회합니다. 하나라도 DOWN이면 전체가 DOWN(503)입니다.")
				.responses(new ApiResponses()
					.addApiResponse("200", healthResponse("모든 구성요소가 UP",
							Map.of("status", "UP", "components",
									Map.of("chain", Map.of("status", "UP", "details", Map.of("chainId", 31337))))))
					.addApiResponse("503", healthResponse("하나 이상의 구성요소가 DOWN",
							Map.of("status", "DOWN", "components", Map.of("chain",
									Map.of("status", "DOWN", "details", Map.of("code", "CHAIN_UNAVAILABLE")))))));

			openApi.getPaths()
				.addPathItem("/actuator/health/chain", new PathItem().get(new Operation().addTagsItem(ACTUATOR_TAG)
					.operationId("chainHealth")
					.summary("노드 연결 상태")
					.description("노드에 eth_chainId를 요청해 연결을 확인합니다. 노드가 응답하지 않으면 read-timeout(3초) 후 DOWN을 반환합니다.")
					.responses(new ApiResponses()
						.addApiResponse("200",
								healthResponse("노드 연결 정상",
										Map.of("status", "UP", "details", Map.of("chainId", 31337))))
						.addApiResponse("503", healthResponse(
								"노드에 연결할 수 없음(CHAIN_UNAVAILABLE) 또는 노드 응답이 올바르지 않음(CHAIN_ERROR)",
								Map.of("status", "DOWN", "details", Map.of("code", "CHAIN_UNAVAILABLE")))))));

			List<Tag> tags = openApi.getTags();
			if (tags != null) {
				tags.stream()
					.filter(tag -> ACTUATOR_TAG.equals(tag.getName()))
					.forEach(tag -> tag.setDescription("애플리케이션과 의존 서비스 상태 확인"));
			}
		};
	}

	private static ApiResponse healthResponse(String description, Map<String, Object> example) {
		return new ApiResponse().description(description)
			.content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
					new MediaType().schema(new ObjectSchema()).example(example)));
	}

}
