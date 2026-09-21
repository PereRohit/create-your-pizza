package com.createyourpizza.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(int status, String message, String error, T data) {

	public static <T> ApiEnvelope<T> success(int status, T data) {
		return new ApiEnvelope<>(status, "success", "", data);
	}

	public static ApiEnvelope<Void> error(int status, String message, String error) {
		return new ApiEnvelope<>(status, message, error, null);
	}
}
