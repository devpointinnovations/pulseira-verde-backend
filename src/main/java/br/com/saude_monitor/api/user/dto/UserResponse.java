package br.com.saude_monitor.api.user.dto;

import java.time.Instant;
import lombok.Builder;

@Builder
public record UserResponse(
	boolean success,
	String message,
	String id,
	String fullName,
	String email,
	String phone,
	boolean active,
	Instant createdAt,
	Instant updatedAt
) {
}
