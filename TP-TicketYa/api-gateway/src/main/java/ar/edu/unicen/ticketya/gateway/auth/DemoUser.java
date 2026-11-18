package ar.edu.unicen.ticketya.gateway.auth;

import java.util.UUID;

/** Usuario demo en memoria. Simplificación aceptada en lugar de auth_db para el plazo. */
public record DemoUser(UUID id, String email, String nombre, String role, String passwordHash) {}
