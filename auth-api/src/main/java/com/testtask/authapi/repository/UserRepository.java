package com.testtask.authapi.repository;

import com.testtask.authapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    User findByEmail(String email);

    boolean existsByEmail(String email);
}
