package com.orthodox.orthodoxapp.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

/**
 * ServerSecret: a random secret the server makes for itself once and then
 * keeps, as a row in the "server_secret" table.
 *
 * StaySignedIn uses one to sign its cookies. It must stay the same across
 * restarts (or every "stay signed in" cookie would stop working), which is
 * why it lives in the database rather than in the server's memory. Nobody
 * types it in or ever needs to see it.
 */
@Entity
public class ServerSecret {

    // What the secret is for, e.g. "stay-signed-in".
    @Id
    private String name;

    // 32 random bytes, in Base64 text.
    @Column(nullable = false)
    private String secret;

    @Column(nullable = false)
    private Instant createdAt;

    protected ServerSecret() {
    }

    public ServerSecret(String name, String secret) {
        this.name = name;
        this.secret = secret;
        this.createdAt = Instant.now();
    }

    public String getSecret() {
        return secret;
    }
}
