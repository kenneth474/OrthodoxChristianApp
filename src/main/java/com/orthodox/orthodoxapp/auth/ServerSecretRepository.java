package com.orthodox.orthodoxapp.auth;

import org.springframework.data.jpa.repository.JpaRepository;

/** Loads and saves ServerSecret rows, found by their name. */
public interface ServerSecretRepository extends JpaRepository<ServerSecret, String> {
}
