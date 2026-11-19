package ar.edu.unicen.ticketya.gateway.auth;

import java.time.Instant;

public record LoginResponse(String token, String role, String email, String nombre, Instant expiresAt) {}