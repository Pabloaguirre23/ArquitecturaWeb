package ar.edu.unicen.ticketya.gateway.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "es obligatorio") String email,
        @NotBlank(message = "es obligatorio") String password) {}