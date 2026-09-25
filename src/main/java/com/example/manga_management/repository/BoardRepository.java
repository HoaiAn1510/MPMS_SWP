package com.example.manga_management.repository;

import com.example.manga_management.entity.Board;
import com.example.manga_management.entity.User;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, String> {
    Optional<Board> findByUser_Id(String userId);

    Optional<Board> findByUser(User user);

    Optional<Board> findTopByOrderByIdDesc();
}
