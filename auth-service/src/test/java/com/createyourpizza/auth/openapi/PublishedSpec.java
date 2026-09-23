package com.createyourpizza.auth.openapi;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A committed file under {@code docs/openapi}, read the way an integrator importing it
 * would — no Spring context, no exporter, just the bytes that ship.
 */
record PublishedSpec(Map<String, Object> root) {

	static PublishedSpec json(String fileName) {
		try {
			return new PublishedSpec(asMap(new ObjectMapper()
					.readValue(Files.readString(file(fileName), StandardCharsets.UTF_8), Map.class)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static PublishedSpec yaml(String fileName) {
		try (Reader reader = Files.newBufferedReader(file(fileName), StandardCharsets.UTF_8)) {
			return new PublishedSpec(asMap(new Yaml().load(reader)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path file(String fileName) {
		return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
				.getParent()
				.resolve("docs/openapi")
				.resolve(fileName);
	}

	Map<String, Object> operation(String method, String path) {
		return child(child(child(root, "paths"), path), method.toLowerCase(Locale.ROOT));
	}

	Map<String, Object> responses(String method, String path) {
		return child(operation(method, path), "responses");
	}

	/** The response an integrator ends up reading, following {@code $ref} into components. */
	Map<String, Object> response(String method, String path, String code) {
		return dereference(child(responses(method, path), code));
	}

	/** Follows a node's {@code $ref}, wherever in the document it points. */
	Map<String, Object> dereference(Map<String, Object> node) {
		Object ref = node.get("$ref");
		return ref == null ? node : resolve(String.valueOf(ref));
	}

	/** The node a {@code $ref} names — response, schema, header, anything. */
	Map<String, Object> resolve(String ref) {
		Object target = target(ref);
		if (target == null) {
			throw new AssertionError("Published spec cannot resolve $ref '" + ref + "'");
		}
		return asMap(target);
	}

	/**
	 * Every {@code $ref} in the whole document whose target is missing, keyed by the JSON
	 * pointer that holds it. An integrator's parser rejects each one of these, so the only
	 * acceptable value is empty — schema references included, not just response components.
	 */
	Map<String, String> danglingReferences() {
		Map<String, String> dangling = new TreeMap<>();
		collectReferences(root, "", dangling);
		dangling.values().removeIf(ref -> target(ref) != null);
		return dangling;
	}

	private static void collectReferences(Object node, String pointer, Map<String, String> refs) {
		if (node instanceof Map<?, ?> map) {
			map.forEach((key, value) -> {
				if ("$ref".equals(key)) {
					refs.put(pointer, String.valueOf(value));
				} else {
					collectReferences(value, pointer + "/" + key, refs);
				}
			});
		} else if (node instanceof List<?> list) {
			for (int index = 0; index < list.size(); index++) {
				collectReferences(list.get(index), pointer + "/" + index, refs);
			}
		}
	}

	/** Walks a local JSON pointer; {@code null} when it names nothing, or is not local. */
	private Object target(String ref) {
		if (!ref.startsWith("#/")) {
			return null;
		}
		Object node = root;
		for (String token : ref.substring(2).split("/")) {
			String key = token.replace("~1", "/").replace("~0", "~");
			if (!(node instanceof Map<?, ?> map) || !map.containsKey(key)) {
				return null;
			}
			node = map.get(key);
		}
		return node;
	}

	/** Every documented operation keyed as {@code "METHOD /path"}, with its response codes. */
	Map<String, Set<String>> responseCodesByOperation() {
		Map<String, Set<String>> codes = new TreeMap<>();
		child(root, "paths").forEach((path, pathItem) -> asMap(pathItem).forEach((method, operation) -> {
			if (operation instanceof Map<?, ?> map && map.containsKey("responses")) {
				codes.put(method.toUpperCase(Locale.ROOT) + " " + path,
						new TreeSet<>(child(asMap(operation), "responses").keySet()));
			}
		}));
		return codes;
	}

	static Map<String, Object> child(Map<String, Object> parent, String key) {
		Object value = parent.get(key);
		if (value == null) {
			throw new AssertionError("Published spec has no '" + key + "' under " + parent.keySet());
		}
		return asMap(value);
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> asMap(Object value) {
		return (Map<String, Object>) value;
	}
}
