package com.capstone.entity;

import java.time.Instant;

public class User {

    private final Long userId;
    private final String userReference;
    private final String name;
    private final String email;
    private final String phoneNo;
    private final Instant createdAt;

    public User(
            Long userId,
            String userReference,
            String name,
            String email,
            String phoneNo,
            Instant createdAt) {

        this.userId = userId;
        this.userReference = userReference;
        this.name = name;
        this.email = email;
        this.phoneNo = phoneNo;
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserReference() {
        return userReference;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}