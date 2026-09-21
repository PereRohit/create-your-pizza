package com.createyourpizza.auth.web;

import com.createyourpizza.auth.web.dto.Pagination;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(int status, String message, String error, T data, Pagination pagination) {

	public static <T> ApiEnvelope<T> success(int status, T data) {
		return new ApiEnvelope<>(status, "success", "", data, null);
	}

	public static <T> ApiEnvelope<T> success(int status, T data, Pagination pagination) {
		return new ApiEnvelope<>(status, "success", "", data, pagination);
	}

	public static ApiEnvelope<Void> error(int status, String message, String error) {
		return new ApiEnvelope<>(status, message, error, null, null);
	}
}
