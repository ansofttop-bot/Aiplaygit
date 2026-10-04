package com.aiplay.ai;

import com.google.gson.*;

/** Tolerant JSON helpers for model output. */
public final class Json {
	private Json() {}

	public static JsonObject parseObject(String raw) {
		if (raw == null) throw new IllegalArgumentException("empty response");
		String s = raw.trim();
		int a = s.indexOf('{'), b = s.lastIndexOf('}');
		if (a < 0 || b <= a) throw new IllegalArgumentException("no JSON object in response");
		try {
			JsonElement e = JsonParser.parseString(s.substring(a, b + 1));
			if (!e.isJsonObject()) throw new IllegalArgumentException("JSON is not an object");
			return e.getAsJsonObject();
		} catch (JsonParseException ex) {
			throw new IllegalArgumentException("invalid JSON: " + ex.getMessage());
		}
	}

	public static String str(JsonObject o, String k, String d) {
		try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : d; } catch (Exception e) { return d; }
	}
	public static int num(JsonObject o, String k, int d) {
		try { return o.has(k) && o.get(k).isJsonPrimitive() ? (int) Math.round(o.get(k).getAsDouble()) : d; } catch (Exception e) { return d; }
	}
	public static double dbl(JsonObject o, String k, double d) {
		try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : d; } catch (Exception e) { return d; }
	}
	public static boolean bool(JsonObject o, String k, boolean d) {
		try { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : d; } catch (Exception e) { return d; }
	}
	public static JsonArray arr(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
	}
}
