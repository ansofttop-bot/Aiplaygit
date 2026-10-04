package com.aiplay.ai;

import com.aiplay.config.ConfigManager;
import com.aiplay.config.ModConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Async DeepSeek (OpenAI-compatible) client. Never blocks the game thread. The API key is only sent in the Authorization header. */
public final class AIClient {
	public record Result(String content, long latencyMs, String requestJson) {}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private AIClient() {}

	public static CompletableFuture<Result> chat(JsonArray messages, double temperature, int maxTokens) {
		String key = ConfigManager.apiKey();
		if (key.isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("DeepSeek API key is not set (Settings -> DeepSeek API Key)"));
		ModConfig cfg = ConfigManager.cfg;
		String json;
		HttpRequest req;
		try {
			JsonObject body = new JsonObject();
			body.addProperty("model", cfg.model);
			body.add("messages", messages);
			body.addProperty("temperature", temperature);
			body.addProperty("max_tokens", maxTokens);
			body.addProperty("stream", false);
			JsonObject rf = new JsonObject();
			rf.addProperty("type", "json_object");
			body.add("response_format", rf);
			json = body.toString();
			req = HttpRequest.newBuilder(URI.create(cfg.apiUrl)).timeout(Duration.ofSeconds(25))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + key)
					.POST(HttpRequest.BodyPublishers.ofString(json)).build();
		} catch (Exception e) {
			return CompletableFuture.failedFuture(new IllegalStateException("Bad API URL / request: " + e.getMessage()));
		}
		final String reqJson = json;
		long t0 = System.currentTimeMillis();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
			int sc = resp.statusCode();
			if (sc != 200) throw new IllegalStateException(describe(sc));
			try {
				JsonObject o = Json.parseObject(resp.body());
				String content = o.getAsJsonArray("choices").get(0).getAsJsonObject()
						.getAsJsonObject("message").get("content").getAsString();
				if (content == null || content.isBlank()) throw new IllegalStateException("DeepSeek returned an empty answer");
				return new Result(content, System.currentTimeMillis() - t0, reqJson);
			} catch (IllegalStateException e) { throw e; }
			catch (Exception e) { throw new IllegalStateException("Unexpected DeepSeek response format"); }
		});
	}

	private static String describe(int sc) {
		return switch (sc) {
			case 401 -> "Invalid API key (HTTP 401)";
			case 402 -> "Insufficient DeepSeek balance (HTTP 402)";
			case 400, 422 -> "DeepSeek rejected the request (HTTP " + sc + ") - check model name / settings";
			case 429 -> "DeepSeek rate limit (HTTP 429)";
			default -> sc >= 500 ? "DeepSeek server error (HTTP " + sc + ")" : "DeepSeek HTTP error " + sc;
		};
	}

	/** Human-readable, key-free error text. */
	public static String errorText(Throwable t) {
		while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) t = t.getCause();
		String m;
		if (t instanceof HttpTimeoutException) m = "DeepSeek request timed out";
		else if (t instanceof ConnectException || t instanceof java.nio.channels.UnresolvedAddressException || t instanceof java.io.IOException)
			m = "Cannot reach DeepSeek (network problem)";
		else m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
		String key = ConfigManager.apiKey();
		if (!key.isEmpty()) m = m.replace(key, "****");
		return m;
	}
}
